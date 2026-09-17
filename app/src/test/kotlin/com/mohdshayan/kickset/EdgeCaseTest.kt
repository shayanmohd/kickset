package com.mohdshayan.kickset

import com.mohdshayan.kickset.calc.CutSolver
import com.mohdshayan.kickset.calc.OffsetSolver
import com.mohdshayan.kickset.calc.Replay
import com.mohdshayan.kickset.calc.Outcome
import com.mohdshayan.kickset.calc.TemplateSolver
import com.mohdshayan.kickset.core.fittings.TableSet
import com.mohdshayan.kickset.core.jobs.BACKUP_FORMAT
import com.mohdshayan.kickset.core.jobs.CalcJson
import com.mohdshayan.kickset.core.jobs.BackupCalc
import com.mohdshayan.kickset.core.jobs.BackupCodec
import com.mohdshayan.kickset.core.jobs.BackupFile
import com.mohdshayan.kickset.core.jobs.BackupJob
import com.mohdshayan.kickset.core.jobs.BackupPrefs
import com.mohdshayan.kickset.core.jobs.BackupRead
import com.mohdshayan.kickset.core.jobs.CsvExporter
import com.mohdshayan.kickset.core.jobs.CutInputs
import com.mohdshayan.kickset.core.jobs.CutSheetEntry
import com.mohdshayan.kickset.core.jobs.CutSheetPdf
import com.mohdshayan.kickset.core.jobs.OffsetInputs
import com.mohdshayan.kickset.core.jobs.TemplateInputs
import com.mohdshayan.kickset.core.offset.ParallelOffset
import com.mohdshayan.kickset.core.offset.validAngle
import com.mohdshayan.kickset.core.pdf.Paper
import com.mohdshayan.kickset.core.pdf.PdfWriter
import com.mohdshayan.kickset.core.template.TemplateOutcome
import com.mohdshayan.kickset.core.template.WrapTemplates
import com.mohdshayan.kickset.core.units.LengthFormatter
import com.mohdshayan.kickset.core.units.LengthParser
import com.mohdshayan.kickset.core.units.MM_PER_INCH
import com.mohdshayan.kickset.core.units.ParsedLength
import com.mohdshayan.kickset.core.units.UnitPrefs
import com.mohdshayan.kickset.core.units.UnitSystem
import com.mohdshayan.kickset.data.export.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.TimeZone

/**
 * The corners a fitter reaches by accident: an empty field, a zero, a minus sign, a number with too
 * many digits, an angle right on a limit, a phone in another time zone, and a file that is not a
 * backup. None of them may crash, and none of them may print arithmetic that does not check out.
 */
class EdgeCaseTest {

    private val tables: TableSet by lazy {
        val dir = listOf(File("src/main/assets/tables"), File("app/src/main/assets/tables")).first { it.isDirectory }
        TableSet.parse(
            File(dir, "asme_b16_9.json").readText(), File(dir, "asme_b16_11.json").readText(),
            File(dir, "asme_b16_5_bolts.json").readText(), File(dir, "asme_b36_10_19.json").readText(),
        )
    }

    private val mm = UnitPrefs(UnitSystem.MM, 16, 1.0)
    private val inch = UnitPrefs(UnitSystem.INCH, 16, 1.0)

    // ------------------------------------------------------------------ empty, zero and negative

    @Test
    fun everyCalculatorOpensOnAPromptRatherThanAnAnswerOrACrash() {
        assertTrue(OffsetSolver.solve(OffsetInputs(), mm).outcome is Outcome.Prompt)
        assertTrue(OffsetSolver.solve(OffsetInputs(mode = "ROLLING"), mm).outcome is Outcome.Prompt)
        assertTrue(OffsetSolver.solve(OffsetInputs(mode = "PARALLEL"), mm).outcome is Outcome.Prompt)
        assertTrue(CutSolver.solve(CutInputs(), mm, 3.0, 1.6, tables).outcome is Outcome.Prompt)
        assertTrue(CutSolver.solve(CutInputs(tab = "ELBOW", elbowAngle = ""), mm, 3.0, 1.6, tables).outcome is Outcome.Prompt)
        assertTrue(TemplateSolver.solve(TemplateInputs(kind = "LATERAL", lateralAngle = ""), mm, Paper.A4, tables).outcome is Outcome.Prompt)
    }

    @Test
    fun zeroAndNegativeLengthsAreRefusedInsteadOfSolved() {
        assertEquals(ParsedLength.Invalid, LengthParser.parse("-5", UnitSystem.MM))
        assertEquals(ParsedLength.Invalid, LengthParser.parse("-5 3/4", UnitSystem.INCH))
        assertEquals(0.0, (LengthParser.parse("0", UnitSystem.MM) as ParsedLength.Ok).mm, 0.0)

        val zeroSet = OffsetSolver.solve(OffsetInputs(set = "0"), mm)
        assertEquals("Set must be more than zero", zeroSet.fields["set"]?.error)
        assertTrue(zeroSet.outcome is Outcome.Prompt)

        // A zero root gap is a legitimate answer, so it must not be refused as "more than zero".
        val noGap = CutSolver.solve(CutInputs(centreToCentre = "1000", rootGap = "0"), mm, 3.0, 1.6, tables)
        assertEquals(null, noGap.fields["rootGap"]?.error)
        val cut = (noGap.outcome as Outcome.Solved).readouts.first()
        assertEquals("1000 mm minus 152 and 64 with no gap", "784 mm", cut.primary)

        // Fittings longer than the centre to centre are a problem, not a negative cut length.
        val tooShort = CutSolver.solve(CutInputs(centreToCentre = "100"), mm, 3.0, 1.6, tables)
        assertTrue(tooShort.outcome is Outcome.Problem)
    }

    @Test
    fun anglesRightOnTheirLimits() {
        assertFalse(validAngle(0.0)); assertFalse(validAngle(90.0)); assertFalse(validAngle(-45.0))
        assertFalse(validAngle(Double.NaN)); assertFalse(validAngle(Double.POSITIVE_INFINITY))
        assertTrue(validAngle(0.01)); assertTrue(validAngle(89.99))
        assertEquals("Angle must be between 0 and 90 degrees, like 45", OffsetSolver.solve(OffsetInputs(set = "300", customAngle = "90"), mm).angleError)
        assertEquals(null, OffsetSolver.solve(OffsetInputs(set = "300", customAngle = "89.9"), mm).angleError)

        // 60 degrees is the last miter cut a wrap can hold; a lateral runs 30 to 89.
        assertTrue(WrapTemplates.miter(114.3, 57.15, 60.0, 16) is TemplateOutcome.Ok)
        assertEquals(TemplateOutcome.BadAngle, WrapTemplates.miter(114.3, 57.15, 60.1, 16))
        assertEquals(TemplateOutcome.BadAngle, WrapTemplates.miter(114.3, 57.15, 0.0, 16))
        assertTrue(WrapTemplates.lateral(114.3, 57.15, 84.15, 30.0, 16) is TemplateOutcome.Ok)
        assertTrue(WrapTemplates.lateral(114.3, 57.15, 84.15, 89.0, 16) is TemplateOutcome.Ok)
        assertEquals(TemplateOutcome.BadAngle, WrapTemplates.lateral(114.3, 57.15, 84.15, 29.9, 16))
        assertEquals(TemplateOutcome.BadAngle, WrapTemplates.lateral(114.3, 57.15, 84.15, 89.1, 16))

        // A branch the size of the header is an equal saddle; anything larger cannot be wrapped.
        assertTrue(WrapTemplates.saddle(114.3, 57.15, 57.15, 16) is TemplateOutcome.Ok)
        assertEquals(TemplateOutcome.BranchTooLarge, WrapTemplates.saddle(114.3, 57.15, 57.0, 16))
    }

    // ------------------------------------------------------------------ huge input

    @Test
    fun hugeNumbersAreRefusedAtTheFieldRatherThanCarriedIntoTheMaths() {
        assertTrue(LengthParser.parse("999999", UnitSystem.MM) is ParsedLength.Ok)
        assertEquals(ParsedLength.Invalid, LengthParser.parse("1000001", UnitSystem.MM))
        assertEquals(ParsedLength.Invalid, LengthParser.parse("99999999999999999999", UnitSystem.MM))
        assertEquals(ParsedLength.Invalid, LengthParser.parse("1e9", UnitSystem.MM))
        // A field holds 24 characters, so the longest thing a fitter can type still has to be handled.
        assertEquals(ParsedLength.Invalid, LengthParser.parse("9".repeat(24), UnitSystem.MM))

        // The biggest offset the parser allows still solves, and still formats.
        val big = OffsetSolver.solve(OffsetInputs(set = "999999", customAngle = "0.5"), mm)
        val solved = big.outcome as Outcome.Solved
        assertTrue(solved.readouts.first().primary.endsWith(" mm"))
        assertTrue(solved.working.all { it.isNotBlank() })

        // NPS 24 at 32 stations is the largest wrap the app can be asked for: it stays finite and tiled.
        val t = TemplateSolver.solve(TemplateInputs(kind = "SADDLE", headerNps = "24", branchNps = "24", stations = 32), mm, Paper.A4, tables)
        val curve = requireNotNull(t.curve)
        assertEquals(33, curve.ordinatesMm.size)
        assertTrue(curve.ordinatesMm.all { it.isFinite() && it >= 0.0 })
        assertTrue("an NPS 24 wrap needs several sheets", (t.outcome as Outcome.Solved).readouts.any { it.primary.endsWith("sheets") })
    }

    @Test
    fun eightParallelLinesAllAdvance() {
        val r = ParallelOffset.solve(300.0, 45.0, ParallelOffset.MAX_LINES)
        assertEquals(8, r.lines.size)
        assertEquals(0.0, r.lines.first().advanceMm, 0.0)
        assertEquals(7 * 300.0 * Math.tan(Math.PI / 8), r.lines.last().advanceMm, 1e-9)
        // A hand-edited backup carrying a silly line count is clamped, not thrown.
        assertEquals(ParallelOffset.MAX_LINES, ParallelOffset.solve(300.0, 45.0, 99).lines.size)
        assertEquals(ParallelOffset.MIN_LINES, ParallelOffset.solve(300.0, 45.0, 0).lines.size)
        assertEquals(ParallelOffset.MIN_LINES, ParallelOffset.solve(300.0, 45.0, -3).lines.size)
    }

    // ------------------------------------------------------------------ shown working that checks out

    /**
     * Every parallel line used to be worked from the rounded advance above it, so at 1/16 inch a
     * spread of 12 printed "Line 3: 2 x 5 in = 9 15/16 in". A fitter checking that on paper gets 10.
     * Each line now multiplies the spread by the factor, which is arithmetic he can repeat.
     */
    @Test
    fun everyParallelLineIsWorkedFromTheSpreadAndNotFromARoundedAdvance() {
        for (u in listOf(inch, UnitPrefs(UnitSystem.INCH, 32, 1.0), mm, UnitPrefs(UnitSystem.MM, 16, 0.5))) {
            // A bare "12" is read in whichever unit system the fitter is working in.
            val r = ParallelOffset.solve(if (u.system == UnitSystem.INCH) 12 * MM_PER_INCH else 12.0, 45.0, 5)
            val lines = ParallelOffset.working(r, u)
            assertEquals(5, lines.size)
            val factor = LengthFormatter.decimal(r.factor, 4)
            r.lines.drop(1).forEachIndexed { k, line ->
                val text = lines[k + 1].text
                assertEquals("Line ${line.index}: spread ${u.working(r.spreadMm)} x $factor x ${line.index - 1} = ${u.working(line.advanceMm)}", text)
                // What the line claims to equal is what the row beside the sketch shows.
                assertTrue("$text should end on ${u.working(line.advanceMm)}", text.endsWith(u.working(line.advanceMm)))
            }
        }
    }

    @Test
    fun theSolvedParallelScreenAgreesWithItsOwnRows() {
        // In inches the working lines use the fitter's own denominator, so they read back letter for
        // letter. In millimetres the working carries one decimal on purpose, so the rows are the same
        // figure rounded to the millimetre precision he chose, never a different one.
        for (u in listOf(inch, UnitPrefs(UnitSystem.INCH, 32, 1.0), mm, UnitPrefs(UnitSystem.MM, 16, 0.5))) {
            val out = OffsetSolver.solve(OffsetInputs(mode = "PARALLEL", spread = "12", angle = 45.0, lines = 5), u)
            val solved = out.outcome as Outcome.Solved
            // A bare "12" is read in whichever unit system the fitter is working in.
            val r = ParallelOffset.solve(if (u.system == UnitSystem.INCH) 12 * MM_PER_INCH else 12.0, 45.0, 5)
            assertEquals(5, out.parallelRows.size)
            assertEquals(u.primary(r.spreadMm * r.factor), solved.readouts.first().primary)
            assertTrue(solved.working.first().endsWith(u.working(r.spreadMm * r.factor)))
            r.lines.drop(1).forEachIndexed { k, line ->
                val row = out.parallelRows[k + 1].second
                assertTrue("row '$row' should open on ${u.primary(line.advanceMm)}", row.startsWith(u.primary(line.advanceMm)))
                assertTrue(solved.working[k + 1].endsWith(u.working(line.advanceMm)))
            }
        }
    }

    // ------------------------------------------------------------------ unit boundaries

    @Test
    fun millimetreAndInchPrecisionBoundaries() {
        assertEquals(0.5, LengthFormatter.roundTo(0.25, 0.5), 1e-12)
        assertEquals(0.0, LengthFormatter.roundTo(0.24, 0.5), 1e-12)
        assertEquals("0 mm", LengthFormatter.millimetres(0.4, 1.0))
        assertEquals("1 mm", LengthFormatter.millimetres(0.5, 1.0))
        assertEquals("0.5 mm", LengthFormatter.millimetres(0.3, 0.5))
        assertEquals("0 mm", LengthFormatter.millimetres(0.0, 0.5))
        // Half a sixteenth rounds up, and the carry reaches the whole inch.
        assertEquals("1", LengthFormatter.inchParts(0.96875 * MM_PER_INCH, 16).toString())
        assertEquals("31/32", LengthFormatter.inchParts(0.96875 * MM_PER_INCH, 32).toString())
        assertEquals("0", LengthFormatter.inchParts(0.0, 16).toString())
        // A negative length never reaches a field, but a formatter must not print "-0".
        assertEquals("0", LengthFormatter.inchParts(-0.01, 16).toString())
        assertEquals("-1", LengthFormatter.inchParts(-MM_PER_INCH, 16).toString())
    }

    // ------------------------------------------------------------------ time zones and DST

    @Test
    fun exportDatesFollowThePhonesOwnCalendarDayAcrossZonesAndDst() {
        val original = TimeZone.getDefault()
        try {
            // 2026-03-08 06:30 UTC is still 2026-03-08 in every zone below, but the wall clock differs:
            // New York springs forward at 07:00 UTC that morning, Kolkata is +5:30, Auckland is +13.
            val instant = 1_772_951_400_000L
            for ((zone, expected) in listOf(
                "UTC" to "2026-03-08", "Asia/Kolkata" to "2026-03-08",
                "America/New_York" to "2026-03-08", "Pacific/Auckland" to "2026-03-08",
            )) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                assertEquals(zone, expected, Files.isoDate(instant))
                assertTrue(zone, Files.readableDate(instant).isNotBlank())
            }
            // One hour later New York has jumped from 01:30 to 03:30 local; the date must not move.
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            assertEquals("2026-03-08", Files.isoDate(instant + 3_600_000L))
            // An instant that is already tomorrow in Auckland must read as tomorrow there.
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))
            assertEquals("2026-03-09", Files.isoDate(instant + 12L * 3_600_000L))
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals("2026-03-08", Files.isoDate(instant + 12L * 3_600_000L))
            assertEquals("1970-01-01", Files.isoDate(0L))
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun backupFileNamesAndJobStemsStayValidFileNames() {
        assertEquals("kickset-backup-2026-03-08.json", BackupCodec.fileName("2026-03-08"))
        assertEquals("unit-3-cooling-water", Files.safeStem("Unit 3 cooling water"))
        assertEquals("job", Files.safeStem("///"))
        assertEquals("job", Files.safeStem(""))
        assertEquals(40, Files.safeStem("a".repeat(200)).length)
        assertFalse(Files.safeStem("Spool 14 / rack 2").contains('/'))
    }

    // ------------------------------------------------------------------ corrupt and empty files

    @Test
    fun moreShapesOfFileThatIsNotAKicksetBackup() {
        val good = BackupCodec.encode(
            BackupFile(BACKUP_FORMAT, 1, 1L, BackupPrefs(), listOf(BackupJob("Spool 14", "", 1L, 2L, listOf(
                BackupCalc("SIMPLE_OFFSET", "Kick", """{"set":"12"}""", "Travel 17 in", "INCH", 3L),
            )))),
        )
        assertTrue(BackupCodec.decode(good.toByteArray()) is BackupRead.Valid)

        fun rejected(name: String, bytes: ByteArray) =
            assertTrue(name, BackupCodec.decode(bytes) is BackupRead.Invalid)

        rejected("random bytes", ByteArray(512) { (it * 7 % 251).toByte() })
        rejected("a PDF", "%PDF-1.4\n1 0 obj".toByteArray())
        rejected("a CSV", "job,label,kind\nUnit 3,Spool 14,CUT_LENGTH\n".toByteArray())
        rejected("a bare string", "\"kickset-backup\"".toByteArray())
        rejected("null", "null".toByteArray())
        rejected("no jobs key", """{"format":"$BACKUP_FORMAT","schema":1,"exportedAt":1}""".toByteArray())
        rejected("negative date", good.replace("\"exportedAt\":1", "\"exportedAt\":-1").toByteArray())
        rejected("an inputs array", good.replace("""{\"set\":\"12\"}""", "[1,2,3]").toByteArray())
        rejected("empty inputs", good.replace("""{\"set\":\"12\"}""", "{}").toByteArray())
        rejected("a blank label", good.replace("\"label\":\"Kick\"", "\"label\":\" \"").toByteArray())
        rejected("a gap of NaN", good.replace("\"rootGapMm\":3.0", "\"rootGapMm\":1e9").toByteArray())
        rejected("a UTF-16 file", good.toByteArray(Charsets.UTF_16))
        rejected("a lone surrogate byte", byteArrayOf(0xED.toByte(), 0xA0.toByte(), 0x80.toByte()))
        // A backup with no jobs at all is legal: it still carries the settings.
        assertTrue(BackupCodec.decode(BackupCodec.encode(BackupFile(BACKUP_FORMAT, 1, 1L, BackupPrefs(), emptyList())).toByteArray()) is BackupRead.Valid)
    }

    @Test
    fun emptyExportsProduceAFileRatherThanNothing() {
        assertEquals("job,label,kind,unit,headline,inputs\r\n", CsvExporter.write("Unit 3", emptyList()))
        fun sheet(n: Int): String {
            val entries = List(n) { CutSheetEntry("Spool ${it + 14}", "Cut length", "Cut 42 in", listOf("Centre to centre 48 in")) }
            val pages = CutSheetPdf.pages("Unit 3 cooling water", "8 Mar 2026", entries, Paper.A4)
            assertTrue("a cut sheet is always at least one page", pages.isNotEmpty())
            return PdfWriter.write(pages, "Unit 3 cooling water").toString(Charsets.ISO_8859_1)
        }
        assertTrue("an empty job still prints its heading", sheet(0).contains("Unit 3 cooling water"))
        assertTrue("0 saved cuts", sheet(0).contains("0 saved cuts."))
        // One saved cut is one cut, not "1 saved cuts".
        assertTrue("one cut should read singular", sheet(1).contains("1 saved cut."))
        assertFalse(sheet(1).contains("1 saved cuts"))
        assertTrue(sheet(2).contains("2 saved cuts."))
        assertNotEquals(0, sheet(0).length)
    }

    /**
     * Tapping a saved cut reopens it in its calculator: the stored inputs decode back into the same
     * fields and solve to the same headline the row shows, in the unit it was saved in. A round trip
     * that lost a field would reopen a fitter's spool on someone else's numbers.
     */
    @Test
    fun aSavedCutReopensOnTheNumbersItWasSavedWith() {
        val json = CalcJson.json
        val offsets = OffsetInputs(mode = "ROLLING", set = "12", roll = "16", angle = 45.0, lines = 5)
        val cut = CutInputs(nps = "4", centreToCentre = "28 5/16", rootGap = "1/8")
        val template = TemplateInputs(kind = "LATERAL", headerNps = "6", branchNps = "4", lateralAngle = "45", stations = 32, basis = "MEAN")

        val a = json.decodeFromString(OffsetInputs.serializer(), json.encodeToString(OffsetInputs.serializer(), offsets))
        assertEquals(offsets, a)
        assertEquals(
            (OffsetSolver.solve(offsets, inch).outcome as Outcome.Solved).headline,
            (OffsetSolver.solve(a, inch).outcome as Outcome.Solved).headline,
        )
        val b = json.decodeFromString(CutInputs.serializer(), json.encodeToString(CutInputs.serializer(), cut))
        assertEquals(cut, b)
        assertEquals(
            (CutSolver.solve(cut, inch, 3.0, 1.6, tables).outcome as Outcome.Solved).headline,
            (CutSolver.solve(b, inch, 3.0, 1.6, tables).outcome as Outcome.Solved).headline,
        )
        val c = json.decodeFromString(TemplateInputs.serializer(), json.encodeToString(TemplateInputs.serializer(), template))
        assertEquals(template, c)
        assertEquals(
            (TemplateSolver.solve(template, inch, Paper.A4, tables).outcome as Outcome.Solved).headline,
            (TemplateSolver.solve(c, inch, Paper.A4, tables).outcome as Outcome.Solved).headline,
        )

        // The cut sheet replays the same stored inputs, in the unit each cut was saved in.
        for (unit in listOf("MM", "INCH")) {
            val lines = Replay.working("ROLLING_OFFSET", json.encodeToString(OffsetInputs.serializer(), offsets), unit, inch, 3.0, 1.6, Paper.A4, tables)
            assertEquals(3, lines.size)
            assertTrue(lines.first().startsWith("True offset"))
        }
        // A row whose stored inputs are damaged replays as nothing rather than throwing.
        assertEquals(emptyList<String>(), Replay.working("ROLLING_OFFSET", "not json", "MM", inch, 3.0, 1.6, Paper.A4, tables))
        assertEquals(emptyList<String>(), Replay.working("NO_SUCH_KIND", "{}", "MM", inch, 3.0, 1.6, Paper.A4, tables))
    }
}
