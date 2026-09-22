package com.mohdshayan.kickset

import com.mohdshayan.kickset.calc.CutSolver
import com.mohdshayan.kickset.calc.OffsetSolver
import com.mohdshayan.kickset.calc.Outcome
import com.mohdshayan.kickset.calc.Replay
import com.mohdshayan.kickset.core.fittings.TableSet
import com.mohdshayan.kickset.core.jobs.BACKUP_FORMAT
import com.mohdshayan.kickset.core.jobs.BACKUP_SCHEMA
import com.mohdshayan.kickset.core.jobs.BackupCalc
import com.mohdshayan.kickset.core.jobs.BackupCodec
import com.mohdshayan.kickset.core.jobs.BackupFile
import com.mohdshayan.kickset.core.jobs.BackupJob
import com.mohdshayan.kickset.core.jobs.BackupPrefs
import com.mohdshayan.kickset.core.jobs.BackupRead
import com.mohdshayan.kickset.core.jobs.BackupWrite
import com.mohdshayan.kickset.core.jobs.CalcJson
import com.mohdshayan.kickset.core.jobs.CalcSettings
import com.mohdshayan.kickset.core.jobs.CutInputs
import com.mohdshayan.kickset.core.jobs.OffsetInputs
import com.mohdshayan.kickset.core.pdf.Paper
import com.mohdshayan.kickset.core.pdf.PdfPage
import com.mohdshayan.kickset.core.pdf.PdfWriter
import com.mohdshayan.kickset.core.units.UnitPrefs
import com.mohdshayan.kickset.core.units.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The defects an outside review reproduced before the app went on sale, each pinned by a test, plus six
 * worked answers checked against hand arithmetic: every subtraction has to add up exactly as printed, at
 * the fitter's own fraction setting.
 */
class ShipFixesTest {

    private val tables: TableSet by lazy {
        val dir = listOf(File("src/main/assets/tables"), File("app/src/main/assets/tables")).first { it.isDirectory }
        TableSet.parse(
            File(dir, "asme_b16_9.json").readText(), File(dir, "asme_b16_11.json").readText(),
            File(dir, "asme_b16_5_bolts.json").readText(), File(dir, "asme_b36_10_19.json").readText(),
        )
    }

    private val mm = UnitPrefs(UnitSystem.MM, 16, 1.0)
    private val inch = UnitPrefs(UnitSystem.INCH, 16, 1.0)

    private fun solved(o: Outcome): Outcome.Solved {
        assertTrue("expected a solved answer, got $o", o is Outcome.Solved)
        return o as Outcome.Solved
    }

    // ---------------------------------------------- a field the current mode hides must not block the solve

    @Test
    fun junkLeftInAFieldThisModeDoesNotDrawDoesNotBlockTheAnswer() {
        // Spread is only drawn in PARALLEL, so half-typed text in it cannot stop SIMPLE.
        val simple = OffsetSolver.solve(OffsetInputs(mode = "SIMPLE", set = "300", spread = "1/", roll = "abc"), mm)
        assertEquals("Travel 424 mm (16 11/16 in), run 300 mm, set 300 mm, 45°", solved(simple.outcome).headline)

        // Run is only drawn when the angle is solved from it.
        val rolling = OffsetSolver.solve(OffsetInputs(mode = "ROLLING", set = "300", roll = "400", run = "abc"), mm)
        assertTrue(rolling.outcome is Outcome.Solved)

        // With the run switch on, the whole angle section is hidden, so a bad custom angle cannot block it.
        val fromRun = OffsetSolver.solve(OffsetInputs(mode = "ROLLING", rollingSolveAngle = true, set = "300", roll = "400", run = "500", customAngle = "0"), mm)
        assertTrue(fromRun.outcome is Outcome.Solved)

        // Set is hidden in PARALLEL.
        val parallel = OffsetSolver.solve(OffsetInputs(mode = "PARALLEL", spread = "300", set = "1/"), mm)
        assertTrue(parallel.outcome is Outcome.Solved)

        // Only the root gap is drawn for butt-weld ends, and only the engagement gap for socket-weld ends.
        val bw = CutSolver.solve(CutInputs(endA = "BW_90LR", endB = "BW_45LR", centreToCentre = "1000", socketGap = "1/"), mm, 3.0, 1.6, tables)
        assertEquals("Cut 778 mm (30 5/8 in), NPS 4, 90 LR elbow to 45 LR elbow, C-to-C 1000 mm", solved(bw.outcome).headline)
        val sw = CutSolver.solve(CutInputs(endA = "SW_90", endB = "SW_COUPLING", swNps = "2", centreToCentre = "500", rootGap = "1/"), mm, 3.0, 1.6, tables)
        assertTrue(sw.outcome is Outcome.Solved)
    }

    @Test
    fun aFieldThisModeDoesDrawStillBlocksTheAnswer() {
        assertTrue(OffsetSolver.solve(OffsetInputs(mode = "SIMPLE", set = "1/"), mm).outcome is Outcome.Prompt)
        assertTrue(OffsetSolver.solve(OffsetInputs(mode = "PARALLEL", spread = "1/"), mm).outcome is Outcome.Prompt)
        assertTrue(OffsetSolver.solve(OffsetInputs(mode = "ROLLING", rollingSolveAngle = true, set = "300", roll = "400", run = "1/"), mm).outcome is Outcome.Prompt)
        assertTrue(OffsetSolver.solve(OffsetInputs(mode = "SIMPLE", set = "300", customAngle = "95"), mm).outcome is Outcome.Prompt)
        assertTrue(CutSolver.solve(CutInputs(centreToCentre = "1000", rootGap = "1/"), mm, 3.0, 1.6, tables).outcome is Outcome.Prompt)
    }

    // ---------------------------------------------- an out-of-range stored angle must prompt, never throw

    @Test
    fun anAngleARestoredBackupCouldCarryPromptsInsteadOfCrashing() {
        for (bad in listOf(0.0, -45.0, 90.0, 180.0, 1e9, Double.NaN)) {
            for (mode in listOf("SIMPLE", "ROLLING", "PARALLEL")) {
                val i = OffsetInputs(mode = mode, set = "300", roll = "400", spread = "300", angle = bad)
                val o = OffsetSolver.solve(i, mm).outcome
                assertEquals("mode $mode angle $bad", Outcome.Prompt("Pick an angle to solve."), o)
            }
        }
        // The control still solves.
        assertTrue(OffsetSolver.solve(OffsetInputs(set = "300", angle = 45.0), mm).outcome is Outcome.Solved)
    }

    @Test
    fun aBackupCarryingAnUnusableAngleIsRejectedAtTheDoor() {
        fun file(inputs: String) = BackupFile(
            BACKUP_FORMAT, BACKUP_SCHEMA, 1_700_000_000_000L, BackupPrefs(),
            listOf(BackupJob("Job", "", 1L, 1L, listOf(BackupCalc("SIMPLE_OFFSET", "Cut 1", inputs, "Travel", "MM", 1L)))),
        )
        assertTrue(BackupCodec.validate(file("""{"mode":"SIMPLE","set":"300","angle":0.0}""")) is BackupRead.Invalid)
        assertTrue(BackupCodec.validate(file("""{"mode":"SIMPLE","set":"300","angle":180.0}""")) is BackupRead.Invalid)
        assertTrue(BackupCodec.validate(file("""{"mode":"SIMPLE","set":"300","angle":45.0}""")) is BackupRead.Valid)
    }

    @Test
    fun aCalcSettingsSnapshotOutsideItsRangeIsRejected() {
        fun file(settings: String) = BackupFile(
            BACKUP_FORMAT, BACKUP_SCHEMA, 1_700_000_000_000L, BackupPrefs(),
            listOf(BackupJob("Job", "", 1L, 1L, listOf(BackupCalc("CUT_LENGTH", "Cut 1", "{\"nps\":\"4\"}", "Cut", "MM", 1L, settings)))),
        )
        assertTrue(BackupCodec.validate(file("")) is BackupRead.Valid)
        assertTrue(BackupCodec.validate(file("""{"inchPrecision":16,"mmPrecision":1.0,"rootGapMm":3.0,"socketGapMm":1.6}""")) is BackupRead.Valid)
        assertTrue(BackupCodec.validate(file("""{"inchPrecision":7,"mmPrecision":1.0,"rootGapMm":3.0,"socketGapMm":1.6}""")) is BackupRead.Invalid)
        assertTrue(BackupCodec.validate(file("""{"inchPrecision":16,"mmPrecision":1.0,"rootGapMm":999.0,"socketGapMm":1.6}""")) is BackupRead.Invalid)
    }

    // ---------------------------------------------- the font licence has to ship inside the APK

    @Test
    fun theOpenFontLicenceTravelsInsideTheApp() {
        // The four Archivo TTFs carry the copyright notice in their name tables, but the licence body itself
        // used to live only in docs/, which is the website, not the app. OFL 1.1 condition 2 asks for both.
        val assets = listOf(File("src/main/assets/licences"), File("app/src/main/assets/licences")).first { it.isDirectory }
        val shipped = listOf("OFL-Archivo.txt", "OFL-ArchivoNarrow.txt").map { File(assets, it).readText() }
        assertTrue(shipped[0].startsWith("Copyright 2020 The Archivo Project Authors"))
        assertTrue(shipped[1].startsWith("Copyright 2019 The Archivo Narrow Project Authors"))
        for (text in shipped) {
            assertTrue(text.contains("SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007"))
            assertTrue(text.contains("contains the above copyright notice and this license"))
            assertTrue(text.contains("scripts.sil.org/OFL"))
        }
        // Byte for byte the files the repository and the website already publish.
        val docs = listOf(File("../docs"), File("docs")).first { it.isDirectory }
        assertEquals(File(docs, "OFL-Archivo.txt").readText(), shipped[0])
        assertEquals(File(docs, "OFL-ArchivoNarrow.txt").readText(), shipped[1])
    }

    // ---------------------------------------------- the writer must not produce a file the reader refuses

    private fun library(jobs: Int, calcsPerJob: Int): BackupFile {
        val inputs = CalcJson.json.encodeToString(CutInputs.serializer(), CutInputs(nps = "4", centreToCentre = "1000"))
        val snapshot = CalcJson.json.encodeToString(CalcSettings.serializer(), CalcSettings(16, 1.0, 3.0, 1.6))
        val calcs = (1..calcsPerJob).map {
            BackupCalc("CUT_LENGTH", "Spool 12-A weld $it", inputs, "Cut 778 mm (30 5/8 in), NPS 4, 90 LR elbow to 45 LR elbow, C-to-C 1000 mm", "MM", 1_700_000_000_000L, snapshot)
        }
        return BackupFile(
            BACKUP_FORMAT, BACKUP_SCHEMA, 1_700_000_000_000L, BackupPrefs(),
            (1..jobs).map { BackupJob("Rack $it, level 3 header", "north bay", 1L, 1L, calcs) },
        )
    }

    @Test
    fun aLibraryTooBigToReadBackIsNotWrittenAtAll() {
        // Nothing in the app caps saved calculations, and the limits the reader enforces on a hostile file
        // (5,000 jobs of 2,000 calculations) are far above what 8 MiB of JSON holds, so the writer has to
        // check the encoded size itself. Before this it wrote the file and said "Backed up to file."
        val big = library(jobs = 40, calcsPerJob = 1_200)
        val written = BackupCodec.encodeForWrite(big)
        assertTrue("expected TooLarge, got $written", written is BackupWrite.TooLarge)
        assertTrue((written as BackupWrite.TooLarge).bytes > BackupCodec.MAX_BYTES)
        // And the same file is exactly what the reader would have refused, which is the bug being closed.
        assertEquals(BackupRead.Invalid("too large"), BackupCodec.decode(BackupCodec.encode(big).toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun aLibraryInsideTheLimitIsWrittenAndReadsBack() {
        val ok = library(jobs = 4, calcsPerJob = 200)
        val written = BackupCodec.encodeForWrite(ok)
        assertTrue("expected Ready, got $written", written is BackupWrite.Ready)
        val bytes = (written as BackupWrite.Ready).bytes
        assertTrue(bytes.size <= BackupCodec.MAX_BYTES)
        val read = BackupCodec.decode(bytes)
        assertTrue("expected Valid, got $read", read is BackupRead.Valid)
        assertEquals(4, (read as BackupRead.Valid).file.jobs.size)
    }

    @Test
    fun eachWayOfFailingToReadGetsItsOwnSentence() {
        // Every rejection used to print "That file is not a Kickset backup", including for a file Kickset
        // itself wrote, one a newer version wrote, and one that was merely cut short.
        assertEquals("That backup is bigger than 8 MB, which is more than Kickset can read.", BackupCodec.explain("too large"))
        assertEquals("That file is empty.", BackupCodec.explain("empty"))
        assertEquals("That backup was written by a newer version of Kickset.", BackupCodec.explain("unknown schema 2"))
        assertEquals("That file is not a Kickset backup.", BackupCodec.explain("wrong format"))
        assertEquals("That file is not a complete Kickset backup. It may have been cut short.", BackupCodec.explain("not a complete backup"))
        assertEquals("That Kickset backup is damaged: bad job name.", BackupCodec.explain("bad job name"))
        // The reasons the validator actually produces all reach a sentence, and none of them is empty.
        val reasons = listOf("wrong format", "unknown schema 9", "bad date", "bad unit system", "bad inch precision",
            "bad mm precision", "bad root gap", "bad socket gap", "bad paper", "bad theme", "too many jobs",
            "bad job name", "notes too long", "bad job date", "too many calculations", "unknown kind x", "bad label",
            "headline too long", "bad calc unit", "bad calc date", "inputs too long", "settings too long",
            "bad calc settings", "inputs are not an object", "inputs empty", "angle out of range")
        for (r in reasons) assertTrue(r, BackupCodec.explain(r).endsWith("."))
        assertTrue(BackupCodec.explainTooLargeToWrite(12 * 1024 * 1024).startsWith("This library comes to 12 MB"))
    }

    // ---------------------------------------------- a cut sheet must reproduce the headline it prints

    @Test
    fun theCutSheetReplaysASavedCutInTheSettingsItWasSavedIn() {
        val inputs = CalcJson.json.encodeToString(CutInputs.serializer(), CutInputs(nps = "4", centreToCentre = "1000"))
        val snapshot = CalcJson.json.encodeToString(CalcSettings.serializer(), CalcSettings(16, 1.0, 3.0, 1.6))
        // The headline was worked at the 3.0 mm default; today the default is 5 mm and the precision is finer.
        val today = UnitPrefs(UnitSystem.MM, 32, 0.5)
        val replayed = Replay.working("CUT_LENGTH", inputs, "MM", snapshot, today, 5.0, 2.0, Paper.A4, tables)
        assertEquals("minus root gap 3.0 mm = cut 778.1 mm", replayed.last())

        // Without a snapshot an old row still replays, on today's settings, which is the documented fallback.
        val noSnapshot = Replay.working("CUT_LENGTH", inputs, "MM", "", today, 5.0, 2.0, Paper.A4, tables)
        assertEquals("minus root gap 5.0 mm = cut 774.1 mm", noSnapshot.last())
    }

    @Test
    fun theSnapshotAlsoHoldsTheFractionDenominator() {
        val inputs = CalcJson.json.encodeToString(CutInputs.serializer(), CutInputs(nps = "4", centreToCentre = "48 3/8"))
        val at16 = CalcJson.json.encodeToString(CalcSettings.serializer(), CalcSettings(16, 1.0, 3.0, 1.6))
        val today = UnitPrefs(UnitSystem.INCH, 32, 1.0)
        assertTrue(Replay.working("CUT_LENGTH", inputs, "INCH", at16, today, 3.0, 1.6, Paper.A4, tables).first().endsWith("48 3/8 in"))
    }

    // ---------------------------------------------- accented text in an exported PDF

    @Test
    fun latin1AndCp1252TextSurvivesTheExportedPdf() {
        fun pdf(text: String): String {
            val page = PdfPage(200.0, 200.0).text(10.0, 10.0, 10.0, text)
            return PdfWriter.write(listOf(page), text).toString(Charsets.ISO_8859_1)
        }
        // Latin-1 range: one byte each, written as an octal escape, instead of the old '?'.
        assertTrue(pdf("Atelier Nord-Caf\u00E9 \u00DCnit 3").contains("(Atelier Nord-Caf\\351 \\334nit 3) Tj"))
        assertTrue(pdf("Gr\u00F6\u00DFe").contains("(Gr\\366\\337e) Tj"))
        // cp1252 puts these in 128 to 159, where Latin-1 has control codes.
        assertTrue(pdf("\u2019 \u201C \u201D \u2026").contains("(\\222 \\223 \\224 \\205) Tj"))
        // Degrees and the superscript two still read exactly as before.
        assertTrue(pdf("45\u00B0 r\u00B2").contains("(45\\260 r\\262) Tj"))
        // ASCII is untouched, and a script Helvetica cannot draw is still the one case that falls back.
        assertTrue(pdf("Cut 778 mm +/- 1").contains("(Cut 778 mm +/- 1) Tj"))
        assertTrue(pdf("\u041C\u0438\u0440").contains("(???) Tj"))
    }

    // ---------------------------------------------- six worked answers against hand arithmetic

    @Test
    fun simpleOffsetInInchesPrintsArithmeticThatAddsUp() {
        val o = solved(OffsetSolver.solve(OffsetInputs(set = "12 5/16", angle = 45.0), inch).outcome)
        assertEquals("Travel 17 7/16 in (442 mm), run 12 5/16 in, set 12 5/16 in, 45°", o.headline)
        assertEquals(
            listOf(
                "Set 12 5/16 in x csc 45° (1.4142) = travel 17 7/16 in",
                "Set 12 5/16 in x cot 45° (1.0000) = run 12 5/16 in",
            ),
            o.working,
        )
    }

    @Test
    fun rollingOffsetPrintsArithmeticThatAddsUp() {
        val o = solved(OffsetSolver.solve(OffsetInputs(mode = "ROLLING", set = "300", roll = "400", angle = 22.5), mm).outcome)
        assertEquals("Travel 1307 mm (51 7/16 in), run 1207 mm, true offset 500 mm, 22.50°", o.headline)
        assertEquals(
            listOf(
                "True offset = √(set 300.0 mm² + roll 400.0 mm²) = 500.0 mm",
                "True offset 500.0 mm x csc 22.5° (2.6131) = travel 1306.6 mm",
                "True offset 500.0 mm x cot 22.5° (2.4142) = run 1207.1 mm",
            ),
            o.working,
        )
    }

    @Test
    fun parallelRackOfEightPrintsArithmeticThatAddsUp() {
        val o = solved(OffsetSolver.solve(OffsetInputs(mode = "PARALLEL", spread = "300", angle = 11.25, lines = 8), mm).outcome)
        assertEquals("Advance 30 mm (1 3/16 in) per line, 8 lines at 300 mm spread, 11.25°", o.headline)
        assertEquals(
            listOf(
                "Advance per line = spread 300.0 mm x tan(11.25° / 2) (0.0985) = 29.5 mm",
                "Line 2: spread 300.0 mm x 0.0985 x 1 = 29.5 mm",
                "Line 3: spread 300.0 mm x 0.0985 x 2 = 59.1 mm",
                "Line 4: spread 300.0 mm x 0.0985 x 3 = 88.6 mm",
                "Line 5: spread 300.0 mm x 0.0985 x 4 = 118.2 mm",
                "Line 6: spread 300.0 mm x 0.0985 x 5 = 147.7 mm",
                "Line 7: spread 300.0 mm x 0.0985 x 6 = 177.3 mm",
                "Line 8: spread 300.0 mm x 0.0985 x 7 = 206.8 mm",
            ),
            o.working,
        )
    }

    @Test
    fun buttWeldCutLengthPrintsArithmeticThatAddsUp() {
        val o = solved(CutSolver.solve(CutInputs(nps = "4", centreToCentre = "1000"), mm, 3.0, 1.6, tables).outcome)
        assertEquals("Cut 778 mm (30 5/8 in), NPS 4, 90 LR elbow to 45 LR elbow, C-to-C 1000 mm", o.headline)
        assertEquals(
            listOf(
                "Centre to centre 1000.0 mm",
                "minus 90 LR elbow takeout 152.4 mm = 847.6 mm",
                "minus 45 LR elbow takeout 63.5 mm = 784.1 mm",
                "minus root gap 3.0 mm = 781.1 mm",
                "minus root gap 3.0 mm = cut 778.1 mm",
            ),
            o.working,
        )
    }

    @Test
    fun socketWeldCutLengthUsesTheOtherGapAndStillAddsUp() {
        val i = CutInputs(endA = "SW_90", endB = "SW_COUPLING", swNps = "2", centreToCentre = "500")
        val o = solved(CutSolver.solve(i, mm, 3.0, 1.6, tables).outcome)
        assertEquals("Cut 449 mm (17 11/16 in), NPS 2, SW 90 elbow to SW coupling, C-to-C 500 mm", o.headline)
        assertEquals(
            listOf(
                "Centre to centre 500.0 mm",
                "minus SW 90 elbow takeout 38.1 mm = 461.9 mm",
                "minus SW coupling takeout 9.5 mm = 452.4 mm",
                "minus engagement gap 1.6 mm = 450.8 mm",
                "minus engagement gap 1.6 mm = cut 449.2 mm",
            ),
            o.working,
        )
    }

    @Test
    fun cutElbowAtAnElevenAndAQuarterDegreeFittingAddsUp() {
        val i = CutInputs(tab = "ELBOW", elbowRadius = "BW_90LR", elbowNps = "4", elbowAngle = "11.25")
        val o = solved(CutSolver.solve(i, mm, 3.0, 1.6, tables).outcome)
        assertEquals("Cut 90 LR elbow to 11.25°: takeout 15 mm (9/16 in), outside mark 41 mm, NPS 4", o.headline)
        assertEquals(
            listOf(
                "Takeout = A90 152.4 mm x tan(11.25° / 2) (0.0985) = 15.0 mm",
                "Centreline arc = 152.4 mm x 0.1963 rad = 29.9 mm",
                "Outside arc = (152.4 mm + OD/2) x 0.1963 rad = 41.1 mm",
                "Inside arc = (152.4 mm - OD/2) x 0.1963 rad = 18.7 mm",
            ),
            o.working,
        )
    }

    // ---------------------------------------------- the table values a spot check confirmed in two catalogues

    @Test
    fun spotCheckedTableValuesAreTheOnesTheCataloguesPrint() {
        fun bw(series: String, nps: String) = tables.series(series)?.row(nps)?.`in`
        assertEquals(6.0, bw("BW_90LR", "4")!!, 1e-9)       // Weldbend p26 C, Hackney p1 A
        assertEquals(36.0, bw("BW_90LR", "24")!!, 1e-9)
        assertEquals(6.0, bw("BW_90SR", "6")!!, 1e-9)       // Weldbend SR p40 C, Hackney p1 A
        assertEquals(5.0, bw("BW_45LR", "8")!!, 1e-9)       // both print 5.00
        assertEquals(4.125, bw("BW_TEE", "4")!!, 1e-9)      // Weldbend p49 B 4.12, Hackney p1 C 4.12
        assertEquals(8.0, bw("BW_REDUCER", "12")!!, 1e-9)   // Weldbend p62 L, Hackney p5 H
        assertEquals(0.875, bw("SW_90", "1")!!, 1e-9)       // Bonney E 7/8, Anvil fig 2150 A 0.88
        assertEquals(1.0, bw("SW_45", "2")!!, 1e-9)         // Bonney E 1, Anvil fig 2151 A 1.00
        assertEquals(0.75, bw("SW_COUPLING", "2")!!, 1e-9)  // Bonney coupling E 3/4
        val six = tables.pipe.sizes.first { it.nps == "6" }
        assertEquals(6.625, six.odIn, 1e-9)
        assertEquals(0.280, six.wallsIn["STD"]!!, 1e-9)     // Weldbend p140, Hackney p4
        assertEquals(0.864, six.wallsIn["XXS"]!!, 1e-9)
        val twelve = tables.pipe.sizes.first { it.nps == "12" }
        assertEquals(0.180, twelve.wallsIn["10"]!!, 1e-9)   // Weldbend 0.180, Sandvik 10S 4.57 mm
        assertEquals(1.312, twelve.wallsIn["160"]!!, 1e-9)
        val c150 = tables.flanges.classes.first { it.`class` == 150 }.rows.first { it.nps == "4" }
        assertEquals(8, c150.bolts)                          // Weldbend p86, USA Fastener chart
        assertEquals("5/8", c150.studIn)
        assertEquals(7.5, c150.boltCircleIn, 1e-9)
        val c300 = tables.flanges.classes.first { it.`class` == 300 }.rows.first { it.nps == "16" }
        assertEquals(20, c300.bolts)                         // Weldbend p88 22.50 bolt circle
        assertEquals(22.5, c300.boltCircleIn, 1e-9)
        // NPS 22 is in Hackney alone, so it was dropped rather than shipped on one source.
        assertFalse(tables.buttWeld.fittings.any { s -> s.rows.any { it.nps == "22" } })
    }
}
