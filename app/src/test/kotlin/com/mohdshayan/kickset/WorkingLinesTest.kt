package com.mohdshayan.kickset

import com.mohdshayan.kickset.calc.CutSolver
import com.mohdshayan.kickset.calc.OffsetSolver
import com.mohdshayan.kickset.calc.Outcome
import com.mohdshayan.kickset.calc.TemplateSolver
import com.mohdshayan.kickset.core.fittings.EndFitting
import com.mohdshayan.kickset.core.fittings.JoinType
import com.mohdshayan.kickset.core.fittings.TableSet
import com.mohdshayan.kickset.core.jobs.CutInputs
import com.mohdshayan.kickset.core.jobs.OffsetInputs
import com.mohdshayan.kickset.core.jobs.TemplateInputs
import com.mohdshayan.kickset.core.pdf.Paper
import com.mohdshayan.kickset.core.units.LengthParser
import com.mohdshayan.kickset.core.units.MM_PER_INCH
import com.mohdshayan.kickset.core.units.ParsedLength
import com.mohdshayan.kickset.core.units.UnitPrefs
import com.mohdshayan.kickset.core.units.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.sqrt

/** One solved screen in the sweep: the settings it was worked in and everything it prints. */
data class Case(val key: String, val u: UnitPrefs, val solved: Outcome.Solved, val stations: Int = 0, val pinned: Boolean = false)

/**
 * Every fitting, every angle, every schedule, both unit systems and both fraction settings, worked out
 * and printed. The same list feeds both tests below: one reads the working back the way a fitter reads
 * it, the other holds the answers to the ones the build on sale printed.
 */
object Sweep {
    val units = listOf(
        UnitPrefs(UnitSystem.MM, 16, 1.0),
        UnitPrefs(UnitSystem.MM, 32, 0.5),
        UnitPrefs(UnitSystem.INCH, 16, 1.0),
        UnitPrefs(UnitSystem.INCH, 32, 0.5),
    )

    private fun key(u: UnitPrefs) = "${u.system}/${u.inchDenom}/${u.mmStep}"

    private fun solved(o: Outcome) = o as? Outcome.Solved

    /** Both ends, every size, and centre to centre typed three different ways against both gaps. */
    fun cutCases(t: TableSet): List<Case> {
        val out = mutableListOf<Case>()
        val bwSizes = CutSolver.sizesFor(t, JoinType.BUTT_WELD)
        val swSizes = CutSolver.sizesFor(t, JoinType.SOCKET_WELD)
        for (u in units) {
            val uk = key(u)
            for (a in EndFitting.entries) for (b in EndFitting.entries) {
                if (a.join != b.join) continue
                val sizes = if (a.join == JoinType.BUTT_WELD) listOf("1/2", "2", "6", "12", "24") else listOf("1/8", "1/2", "2", "4")
                for (nps in sizes) {
                    val i = CutInputs(endA = a.name, endB = b.name, nps = nps, swNps = nps, centreToCentre = "2000")
                    val s = solved(CutSolver.solve(i, u, 3.0, 1.6, t).outcome) ?: continue
                    out += Case("cut $uk ${a.name}/${b.name} NPS $nps", u, s, pinned = nps == "6" || nps == "2")
                }
            }
            for (nps in bwSizes + swSizes) {
                val bw = nps in bwSizes
                for (ctoc in listOf("2000", "48 3/8", "771.4 mm")) for (gap in listOf("", "1/8")) {
                    val i = CutInputs(
                        endA = if (bw) "BW_90LR" else "SW_90", endB = if (bw) "BW_45LR" else "SW_COUPLING",
                        nps = nps, swNps = nps, centreToCentre = ctoc, rootGap = gap, socketGap = gap,
                    )
                    val s = solved(CutSolver.solve(i, u, 3.0, 1.6, t).outcome) ?: continue
                    out += Case("cut $uk size NPS $nps ctoc '$ctoc' gap '$gap'", u, s, pinned = ctoc == "2000" && gap == "")
                }
            }
        }
        return out
    }

    /** Both elbow radii, every size, and the angles an elbow gets cut down to. */
    fun elbowCases(t: TableSet): List<Case> {
        val out = mutableListOf<Case>()
        for (u in units) for (radius in listOf("BW_90LR", "BW_90SR")) {
            for (nps in CutSolver.sizesFor(t, JoinType.BUTT_WELD)) for (angle in listOf("11.25", "22.5", "30", "45", "67.5", "89")) {
                val i = CutInputs(tab = "ELBOW", elbowRadius = radius, elbowNps = nps, elbowAngle = angle)
                val s = solved(CutSolver.solve(i, u, 3.0, 1.6, t).outcome) ?: continue
                out += Case("elbow ${key(u)} $radius NPS $nps $angle", u, s, pinned = nps == "6")
            }
        }
        return out
    }

    /** Every fitting angle the chips offer, two angles off the presets, and all four offset modes. */
    fun offsetCases(): List<Case> {
        val angles = listOf(11.25, 22.5, 30.0, 45.0, 60.0, 7.5, 67.3)
        val out = mutableListOf<Case>()
        for (u in units) {
            val uk = key(u)
            val lengths = if (u.system == UnitSystem.INCH) listOf("12", "12 5/16", "300 mm") else listOf("300", "771.4", "12 5/16")
            for (angle in angles) for (length in lengths) {
                val simple = OffsetInputs(mode = "SIMPLE", set = length, customAngle = "$angle")
                solved(OffsetSolver.solve(simple, u).outcome)?.let { out += Case("simple $uk '$length' $angle", u, it, pinned = true) }

                val rolling = OffsetInputs(mode = "ROLLING", set = length, roll = lengths.first(), customAngle = "$angle")
                solved(OffsetSolver.solve(rolling, u).outcome)?.let { out += Case("rolling $uk '$length' $angle", u, it, pinned = length == lengths.first()) }

                for (count in listOf(2, 5, 8)) {
                    val parallel = OffsetInputs(mode = "PARALLEL", spread = length, customAngle = "$angle", lines = count)
                    solved(OffsetSolver.solve(parallel, u).outcome)?.let { out += Case("parallel $uk '$length' $angle x$count", u, it, pinned = count == 5) }
                }
            }
            for (run in lengths) {
                val i = OffsetInputs(mode = "ROLLING", rollingSolveAngle = true, set = lengths.first(), roll = lengths.last(), run = run)
                solved(OffsetSolver.solve(i, u).outcome)?.let { out += Case("rolling-from-run $uk '$run'", u, it, pinned = true) }
            }
        }
        return out
    }

    /** Every template kind, every schedule the pipe table carries, both bases and both station counts. */
    fun templateCases(t: TableSet): List<Case> {
        val out = mutableListOf<Case>()
        for (u in units) {
            val uk = key(u)
            for (branch in listOf("1", "4", "6", "12")) for (schedule in t.pipe.schedules) for (basis in listOf("OD", "MEAN", "ID")) for (stations in listOf(16, 32)) {
                val pin = basis == "OD" && stations == 16 && branch == "6"
                val base = TemplateInputs(kind = "SADDLE", headerNps = "12", branchNps = branch, schedule = schedule, basis = basis, stations = stations)
                val name = "$uk NPS $branch Sch $schedule $basis $stations"
                solved(TemplateSolver.solve(base, u, Paper.A4, t).outcome)?.let { out += Case("saddle $name", u, it, stations, pin) }
                for (angle in listOf("30", "45", "60", "89")) {
                    solved(TemplateSolver.solve(base.copy(kind = "LATERAL", lateralAngle = angle), u, Paper.A4, t).outcome)
                        ?.let { out += Case("lateral $name $angle", u, it, stations, pin && angle == "45") }
                }
                for (pieces in 2..5) {
                    solved(TemplateSolver.solve(base.copy(kind = "MITER", miterPieces = pieces, miterTurn = "90"), u, Paper.A4, t).outcome)
                        ?.let { out += Case("miter $name ${pieces}pc", u, it, stations, pin && pieces == 3) }
                }
                solved(TemplateSolver.solve(base.copy(kind = "MITER", singleCut = true, singleCutAngle = "22.5"), u, Paper.A4, t).outcome)
                    ?.let { out += Case("miter-cut $name", u, it, stations, pin) }
            }
        }
        return out
    }

    fun all(t: TableSet): List<Case> = cutCases(t) + elbowCases(t) + offsetCases() + templateCases(t)

    /** One pinned line: the key, the headline and every readout, exactly as the screen shows them. */
    fun answer(c: Case): String = buildString {
        append(c.key).append(" | ").append(c.solved.headline)
        for (r in c.solved.readouts) {
            append(" | ").append(r.label).append(": ").append(r.primary)
            if (r.secondary != null) append(" (").append(r.secondary).append(")")
        }
    }
}

/**
 * A fitter who checks the arithmetic and finds it wrong stops trusting every other number on the
 * screen. Every printed line is read back here the way he reads it: the numbers that are printed, the
 * arithmetic the line claims between them, and the figure printed at the end of it. A line reads true
 * when that figure is what the printed numbers give, to the last digit the line prints.
 */
class WorkingLinesTest {

    private val tables: TableSet by lazy {
        val dir = listOf(File("src/main/assets/tables"), File("app/src/main/assets/tables")).first { it.isDirectory }
        TableSet.parse(
            File(dir, "asme_b16_9.json").readText(), File(dir, "asme_b16_11.json").readText(),
            File(dir, "asme_b16_5_bolts.json").readText(), File(dir, "asme_b36_10_19.json").readText(),
        )
    }

    // ------------------------------------------------------------------ reading a printed line back

    private val lengthRe = Regex("""(\d+(?:\.\d+)? \d+/\d+|\d+/\d+|\d+(?:\.\d+)?) (mm|in)""")
    private val parenFactorRe = Regex("""\((\d+\.\d+)\)""")
    private val bareFactorRe = Regex("""x (\d+\.\d+)""")

    /** Every length the line prints, in millimetres, read back with the app's own parser. */
    private fun lengths(line: String): List<Double> = lengthRe.findAll(line).map {
        val p = LengthParser.parse(it.value, UnitSystem.MM)
        assertTrue("could not read '${it.value}' out of '$line'", p is ParsedLength.Ok)
        (p as ParsedLength.Ok).mm
    }.toList()

    /** Half of the last digit a figure prints: how far a reader's own arithmetic may land from it. */
    private fun halfOfLastDigit(u: UnitPrefs, text: String): Double {
        val m = lengthRe.find(text) ?: error("no figure in '$text'")
        val body = m.groupValues[1]
        val inch = m.groupValues[2] == "in"
        val written = when {
            // A fraction that reduces hides the denominator it was written to, so take the fitter's own.
            body.contains('/') -> MM_PER_INCH / maxOf(body.substringAfter('/').toDouble(), u.inchDenom.toDouble())
            body.contains('.') -> Math.pow(10.0, -(body.length - body.indexOf('.') - 1).toDouble()) * (if (inch) MM_PER_INCH else 1.0)
            else -> if (inch) MM_PER_INCH / u.inchDenom else u.mmStep
        }
        // A figure written exactly the way the answer above it is written is read at that precision:
        // it is the one place the working falls back to when nothing finer can be written.
        val headline = if (inch) MM_PER_INCH / u.inchDenom else u.mmStep
        val step = if (m.value == u.primary(lengths(m.value).single())) maxOf(written, headline) else written
        return step / 2.0 + 1e-9
    }

    private fun mm(v: Double) = String.format(Locale.ROOT, "%.6f mm", v)

    /** The line claims its printed numbers come to [claim]; [printed] is the figure it prints for that. */
    private fun reads(u: UnitPrefs, line: String, claim: Double, printed: String) {
        val value = lengths(printed).single()
        assertTrue("'$line' reads ${mm(claim)} but prints ${mm(value)} in '$printed'", abs(claim - value) <= halfOfLastDigit(u, printed))
    }

    /** A running total is printed exactly, so the subtraction above it has to land on it digit for digit. */
    private fun readsExactly(line: String, claim: Double, printed: String) {
        val value = lengths(printed).single()
        assertTrue("'$line' reads ${mm(claim)} but prints ${mm(value)}", abs(claim - value) <= 1e-6)
    }

    private fun parenFactor(line: String) = parenFactorRe.find(line)?.groupValues?.get(1)?.toDouble() ?: error("no multiplier in '$line'")

    private fun bareFactor(line: String) = bareFactorRe.find(line)?.groupValues?.get(1)?.toDouble() ?: error("no multiplier in '$line'")

    /** The tail of a line from its last equals sign: what the line says the arithmetic comes to. */
    private fun answerOf(line: String) = line.substringAfterLast("= ")

    /**
     * A figure the working prints for a quantity that is also an answer has to round to that answer.
     * Printing 1863.5 mm under an answer of 1863 mm reads as a different number to anyone who rounds
     * what he sees.
     */
    private fun agreesWithReadout(c: Case, printed: Double, readout: Int) {
        val r = c.solved.readouts[readout]
        assertEquals("${c.key}: working ${mm(printed)} under '${r.label} ${r.primary}'", r.primary, c.u.primary(printed))
    }

    // ------------------------------------------------------------------ one reader per printed block

    private fun checkCut(c: Case) {
        val w = c.solved.working
        assertEquals("$w", 4, w.size)
        val ctoc = lengths(w[0]).single()
        val first = lengths(w[1])
        val second = lengths(w[2])
        val last = lengths(w[3])
        assertEquals("$w", 2, first.size)
        assertEquals("$w", 2, second.size)
        assertEquals("$w", 3, last.size)
        readsExactly(w[1], ctoc - first[0], answerOf(w[1]))
        readsExactly(w[2], first[1] - second[0], answerOf(w[2]))
        // Both gaps come off together, and the line prints the gap and what two of them come to.
        readsExactly(w[3], 2.0 * last[0], w[3].substringAfter(", ").substringBefore(" total"))
        reads(c.u, w[3], second[1] - last[1], answerOf(w[3]))
        // The cut the working lands on, both takeouts and the gap are the ones printed above it.
        agreesWithReadout(c, last[2], 0)
        agreesWithReadout(c, first[0], 1)
        agreesWithReadout(c, second[0], 2)
        agreesWithReadout(c, last[0], 3)
    }

    private fun checkElbow(c: Case) {
        val w = c.solved.working
        assertEquals("$w", 4, w.size)
        reads(c.u, w[0], lengths(w[0])[0] * parenFactor(w[0]), answerOf(w[0]))
        reads(c.u, w[1], lengths(w[1])[0] * bareFactor(w[1]), answerOf(w[1]))
        val outside = lengths(w[2])
        reads(c.u, w[2], (outside[0] + outside[1]) * bareFactor(w[2]), answerOf(w[2]))
        val inside = lengths(w[3])
        reads(c.u, w[3], (inside[0] - inside[1]) * bareFactor(w[3]), answerOf(w[3]))
        // Takeout, outside mark, centreline mark and inside mark, in the order the screen shows them.
        for ((line, readout) in listOf(0 to 0, 2 to 1, 1 to 2, 3 to 3)) agreesWithReadout(c, lengths(answerOf(w[line])).single(), readout)
    }

    private fun checkSimpleOffset(c: Case) {
        val w = c.solved.working
        assertEquals("$w", 2, w.size)
        w.forEachIndexed { i, line ->
            reads(c.u, line, lengths(line)[0] * parenFactor(line), answerOf(line))
            agreesWithReadout(c, lengths(answerOf(line)).single(), i)
        }
    }

    private fun checkRollingOffset(c: Case) {
        val w = c.solved.working
        assertEquals("$w", 3, w.size)
        val root = lengths(w[0])
        reads(c.u, w[0], sqrt(root[0] * root[0] + root[1] * root[1]), answerOf(w[0]))
        agreesWithReadout(c, lengths(answerOf(w[0])).single(), 2)
        if (w[1].startsWith("Angle = atan")) {
            val f = lengths(w[1])
            assertEquals(w[1], String.format(Locale.ROOT, "%.2f", Math.toDegrees(atan(f[0] / f[1]))) + "°", answerOf(w[1]))
            val t = lengths(w[2])
            reads(c.u, w[2], sqrt(t[0] * t[0] + t[1] * t[1]), answerOf(w[2]))
            agreesWithReadout(c, lengths(answerOf(w[2])).single(), 0)
            agreesWithReadout(c, f[1], 1)
        } else {
            listOf(w[1], w[2]).forEachIndexed { i, line ->
                reads(c.u, line, lengths(line)[0] * parenFactor(line), answerOf(line))
                agreesWithReadout(c, lengths(answerOf(line)).single(), i)
            }
        }
    }

    private fun checkParallelOffset(c: Case) {
        val w = c.solved.working
        val factor = parenFactor(w[0])
        reads(c.u, w[0], lengths(w[0])[0] * factor, answerOf(w[0]))
        agreesWithReadout(c, lengths(answerOf(w[0])).single(), 0)
        w.drop(1).forEachIndexed { k, line ->
            assertEquals(line, factor, bareFactor(line), 0.0)
            reads(c.u, line, lengths(line)[0] * factor * (k + 1), answerOf(line))
        }
    }

    private fun checkTemplate(c: Case) {
        var checked = 0
        for (line in c.solved.working) when {
            line.startsWith("Cut angle = turn") -> {
                val turn = Regex("""turn (\d+(?:\.\d+)?)°""").find(line)!!.groupValues[1].toDouble()
                val joints = Regex("""x (\d+) joints""").find(line)!!.groupValues[1].toInt()
                assertEquals(line, String.format(Locale.ROOT, "%.3f", turn / (2.0 * joints)) + "°", answerOf(line))
                checked++
            }
            line.startsWith("R = header OD") -> {
                val f = lengths(line)
                reads(c.u, line, f[0] / 2.0, line.substringBefore(",").substringAfterLast("= "))
                reads(c.u, line, f[2] / 2.0, answerOf(line))
                checked++
            }
            line.startsWith("Girth = pi x OD") -> {
                val f = lengths(line)
                reads(c.u, line, PI * f[0], line.substringBefore(",").substringAfterLast("= "))
                reads(c.u, line, f[1] / c.stations, line.substringAfter("station spacing "))
                agreesWithReadout(c, f[1], 1)
                checked++
            }
            line.startsWith("Station ") -> {
                agreesWithReadout(c, lengths(line.substringAfter("cutback ")).single(), 0)
                checked++
            }
        }
        assertTrue("nothing to check in ${c.key}: ${c.solved.working}", checked > 0)
    }

    // ------------------------------------------------------------------ the sweep

    @Test
    fun everyPrintedWorkingLineReadsTrueOffTheScreen() {
        val cases = Sweep.all(tables)
        assertTrue("the sweep is meant to be exhaustive, not ${cases.size} cases", cases.size > 3_000)
        for (c in cases) {
            assertTrue(c.key, c.solved.working.isNotEmpty())
            when (c.solved.kind.name) {
                "CUT_LENGTH" -> checkCut(c)
                "CUT_ELBOW" -> checkElbow(c)
                "SIMPLE_OFFSET" -> checkSimpleOffset(c)
                "ROLLING_OFFSET" -> checkRollingOffset(c)
                "PARALLEL_OFFSET" -> checkParallelOffset(c)
                else -> checkTemplate(c)
            }
        }
    }

    /**
     * The subtraction the audit recorded and did not fix. An NPS 6 45 LR takeout is 3.75 in, which is
     * exactly 95.25 mm; it used to print as 95.3 mm under a running total of 771.4 mm above an answer
     * of 676.2 mm, so the screen read 771.4 - 95.3 = 676.1, one tenth out.
     */
    @Test
    fun theSubtractionTheAuditRecordedNowReadsTrue() {
        val u = UnitPrefs(UnitSystem.MM, 16, 1.0)
        val i = CutInputs(endA = "BW_45LR", endB = "BW_45LR", nps = "6", centreToCentre = "771.4")
        val o = CutSolver.solve(i, u, 3.0, 1.6, tables).outcome as Outcome.Solved
        assertEquals(
            listOf(
                "Centre to centre 771.4 mm",
                "minus 45 LR elbow takeout 95.25 mm = 676.15 mm",
                "minus 45 LR elbow takeout 95.25 mm = 580.9 mm",
                "minus root gap 3.0 mm at both welds, 6.0 mm total = cut 574.9 mm",
            ),
            o.working,
        )
        assertEquals("Cut length", o.readouts[0].label)
        assertEquals("575 mm", o.readouts[0].primary)
    }

    /**
     * Two of the six store screenshots show a working block: 02-rolling-offset.png and
     * 06-parallel-dark.png. Both were taken before this pass, so both have to still be what the app
     * prints, or they have to be retaken.
     */
    @Test
    fun theTwoStoreFramesThatShowWorkingStillShowWhatTheAppPrints() {
        val inch = UnitPrefs(UnitSystem.INCH, 16, 1.0)
        val rolling = OffsetSolver.solve(OffsetInputs(mode = "ROLLING", set = "12", roll = "16", angle = 45.0), inch).outcome as Outcome.Solved
        assertEquals(
            listOf(
                "True offset = √(set 12 in² + roll 16 in²) = 20 in",
                "True offset 20 in x csc 45° (1.4142) = travel 28 5/16 in",
                "True offset 20 in x cot 45° (1.0000) = run 20 in",
            ),
            rolling.working,
        )
        val parallel = OffsetSolver.solve(OffsetInputs(mode = "PARALLEL", spread = "12", angle = 45.0, lines = 5), inch).outcome as Outcome.Solved
        assertEquals(
            listOf(
                "Advance per line = spread 12 in x tan(45° / 2) (0.4142) = 5 in",
                "Line 2: spread 12 in x 0.4142 x 1 = 5 in",
                "Line 3: spread 12 in x 0.4142 x 2 = 9 15/16 in",
                "Line 4: spread 12 in x 0.4142 x 3 = 14 15/16 in",
                "Line 5: spread 12 in x 0.4142 x 4 = 19 7/8 in",
            ),
            parallel.working,
        )
    }

    /**
     * The answers themselves, held to the ones the build on sale printed. Writing the working out
     * differently is not allowed to move a cut length, a travel, a run or a takeout by so much as a
     * sixteenth, at either fraction setting or in either unit system.
     */
    @Test
    fun theAnswersAreTheOnesTheShippedBuildPrinted() {
        val printed = Sweep.all(tables).filter { it.pinned }.map { Sweep.answer(it) }
        val file = listOf(File("src/test/resources/pinned-answers.txt"), File("app/src/test/resources/pinned-answers.txt")).first { it.isFile }
        val pinned = file.readLines().filter { it.isNotBlank() }
        assertEquals("the pinned sweep changed shape", pinned.size, printed.size)
        for ((i, line) in printed.withIndex()) assertEquals("pinned answer ${i + 1}", pinned[i], line)
    }
}
