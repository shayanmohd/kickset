package com.mohdshayan.kickset.calc

import com.mohdshayan.kickset.core.fittings.CutElbow
import com.mohdshayan.kickset.core.fittings.CutEnd
import com.mohdshayan.kickset.core.fittings.CutLength
import com.mohdshayan.kickset.core.fittings.CutLengthOutcome
import com.mohdshayan.kickset.core.fittings.EndFitting
import com.mohdshayan.kickset.core.fittings.JoinType
import com.mohdshayan.kickset.core.fittings.TableSet
import com.mohdshayan.kickset.core.jobs.CalcJson
import com.mohdshayan.kickset.core.jobs.CalcKind
import com.mohdshayan.kickset.core.jobs.CutInputs
import com.mohdshayan.kickset.core.jobs.OffsetInputs
import com.mohdshayan.kickset.core.jobs.TemplateInputs
import com.mohdshayan.kickset.core.offset.ParallelOffset
import com.mohdshayan.kickset.core.offset.RollingOffset
import com.mohdshayan.kickset.core.offset.SimpleOffset
import com.mohdshayan.kickset.core.offset.angleLabel
import com.mohdshayan.kickset.core.offset.validAngle
import com.mohdshayan.kickset.core.pdf.Paper
import com.mohdshayan.kickset.core.template.DiameterBasis
import com.mohdshayan.kickset.core.template.SheetLayout
import com.mohdshayan.kickset.core.template.TemplateDrawings
import com.mohdshayan.kickset.core.template.TemplateOutcome
import com.mohdshayan.kickset.core.template.TemplatePages
import com.mohdshayan.kickset.core.template.WrapCurve
import com.mohdshayan.kickset.core.template.WrapTemplates
import com.mohdshayan.kickset.core.template.basisDiameter
import com.mohdshayan.kickset.core.units.LengthFormatter
import com.mohdshayan.kickset.core.units.LengthParser
import com.mohdshayan.kickset.core.units.MM_PER_INCH
import com.mohdshayan.kickset.core.units.ParsedLength
import com.mohdshayan.kickset.core.units.UnitPrefs
import com.mohdshayan.kickset.core.units.UnitSystem

/** An input field's feedback: an error, or the parsed value echoed in the other unit. */
data class FieldState(val error: String? = null, val echo: String? = null)

data class ReadoutData(val label: String, val primary: String, val secondary: String?)

sealed interface Outcome {
    /** Nothing to solve yet; the text says what to enter. */
    data class Prompt(val text: String) : Outcome
    /** The inputs parse but cannot be solved; the text says how to fix it. */
    data class Problem(val text: String) : Outcome
    data class Solved(
        val kind: CalcKind,
        val readouts: List<ReadoutData>,
        val working: List<String>,
        val headline: String,
        val sketch: Map<String, String>,
        val sizeText: String? = null,
    ) : Outcome
}

private fun readLength(text: String, name: String, example: String, u: UnitPrefs, positive: Boolean = true): Pair<Double?, FieldState> =
    when (val p = LengthParser.parse(text, u.system)) {
        ParsedLength.Empty -> null to FieldState()
        ParsedLength.Invalid -> null to FieldState(error = "$name must be a length, like $example")
        is ParsedLength.Ok -> {
            if (positive && p.mm <= 0.0) null to FieldState(error = "$name must be more than zero")
            else {
                val echo = if (p.readAs == UnitSystem.INCH) "Reads as ${LengthFormatter.millimetres(p.mm, 0.5)}"
                else "Reads as ${LengthFormatter.inches(p.mm, u.inchDenom)}"
                p.mm to FieldState(echo = echo)
            }
        }
    }

private fun examples(u: UnitPrefs, inch: String, mm: String) = if (u.system == UnitSystem.INCH) inch else mm

private fun both(u: UnitPrefs, mm: Double) = u.primary(mm) to u.secondary(mm)

private fun ro(label: String, u: UnitPrefs, mm: Double) = both(u, mm).let { ReadoutData(label, it.first, it.second) }

// ---------------------------------------------------------------------------------------------- offsets

data class OffsetsSolved(val fields: Map<String, FieldState>, val outcome: Outcome, val travelMm: Double?, val angleError: String?, val parallelRows: List<Pair<String, String>>)

object OffsetSolver {
    fun angleOf(i: OffsetInputs): Double? =
        if (i.customAngle.isNotBlank()) i.customAngle.trim().toDoubleOrNull()?.takeIf { validAngle(it) } else i.angle

    fun solve(i: OffsetInputs, u: UnitPrefs): OffsetsSolved {
        val fields = mutableMapOf<String, FieldState>()
        val (set, setF) = readLength(i.set, "Set", examples(u, "12 or 12 5/16", "300 or 312.5"), u); fields["set"] = setF
        val (roll, rollF) = readLength(i.roll, "Roll", examples(u, "16 or 16 3/8", "400 or 412.5"), u); fields["roll"] = rollF
        val (run, runF) = readLength(i.run, "Run", examples(u, "20 or 20 1/2", "500"), u); fields["run"] = runF
        val (spread, spreadF) = readLength(i.spread, "Spread", examples(u, "12 or 11 3/4", "300"), u); fields["spread"] = spreadF
        val angle = angleOf(i)
        val angleError = if (i.customAngle.isNotBlank() && angle == null) "Angle must be between 0 and 90 degrees, like 45" else null
        val anyError = fields.values.any { it.error != null } || angleError != null

        fun result(o: Outcome, travel: Double? = null, rows: List<Pair<String, String>> = emptyList()) = OffsetsSolved(fields, o, travel, angleError, rows)
        when (i.mode) {
            "ROLLING" -> {
                if (set == null && i.set.isBlank()) return result(Outcome.Prompt("Enter a set to solve."))
                if (roll == null && i.roll.isBlank()) return result(Outcome.Prompt("Enter a roll to solve."))
                if (anyError || set == null || roll == null) return result(Outcome.Prompt("Fix the marked field to solve."))
                val r = if (i.rollingSolveAngle) {
                    if (run == null) return result(Outcome.Prompt(if (i.run.isBlank()) "Enter the run to solve the angle." else "Fix the marked field to solve."))
                    RollingOffset.solveFromRun(set, roll, run)
                } else RollingOffset.solve(set, roll, angle ?: return result(Outcome.Prompt("Pick an angle to solve.")))
                val headline = "Travel ${u.primary(r.travelMm)} (${u.secondary(r.travelMm)}), run ${u.primary(r.runMm)}, true offset ${u.primary(r.trueOffsetMm)}, ${LengthFormatter.decimal(r.angleDeg, 2)}°"
                val readouts = mutableListOf(ro("Travel", u, r.travelMm), ro("Run", u, r.runMm), ro("True offset", u, r.trueOffsetMm))
                if (r.angleFromRun) readouts += ReadoutData("Fitting angle", "${LengthFormatter.decimal(r.angleDeg, 2)}°", "csc ${LengthFormatter.decimal(r.m.csc, 4)}")
                return result(Outcome.Solved(CalcKind.ROLLING_OFFSET, readouts, RollingOffset.working(r, u).map { it.text }, headline,
                    mapOf("set" to u.primary(set), "roll" to u.primary(roll), "true" to "True ${u.primary(r.trueOffsetMm)}", "run" to u.primary(r.runMm), "travel" to u.primary(r.travelMm))), r.travelMm)
            }
            "PARALLEL" -> {
                if (spread == null && i.spread.isBlank()) return result(Outcome.Prompt("Enter the spread between lines to solve."))
                if (anyError || spread == null) return result(Outcome.Prompt("Fix the marked field to solve."))
                val a = angle ?: return result(Outcome.Prompt("Pick an angle to solve."))
                val r = ParallelOffset.solve(spread, a, i.lines.coerceIn(ParallelOffset.MIN_LINES, ParallelOffset.MAX_LINES))
                val step = spread * r.factor
                val rows = r.lines.map { "Line ${it.index}" to (if (it.index == 1) "Kicks first" else "${u.primary(it.advanceMm)} (${u.secondary(it.advanceMm)})") }
                val headline = "Advance ${u.primary(step)} (${u.secondary(step)}) per line, ${r.lines.size} lines at ${u.primary(spread)} spread, ${angleLabel(a)}"
                return result(Outcome.Solved(CalcKind.PARALLEL_OFFSET, listOf(ro("Advance per line", u, step)), ParallelOffset.working(r, u).map { it.text }, headline,
                    mapOf("spread" to u.primary(spread), "advance" to u.primary(step))), null, rows)
            }
            else -> {
                if (set == null && i.set.isBlank()) return result(Outcome.Prompt("Enter a set to solve."))
                if (anyError || set == null) return result(Outcome.Prompt("Fix the marked field to solve."))
                val a = angle ?: return result(Outcome.Prompt("Pick an angle to solve."))
                val r = SimpleOffset.solve(set, a)
                val headline = "Travel ${u.primary(r.travelMm)} (${u.secondary(r.travelMm)}), run ${u.primary(r.runMm)}, set ${u.primary(set)}, ${angleLabel(a)}"
                return result(Outcome.Solved(CalcKind.SIMPLE_OFFSET, listOf(ro("Travel", u, r.travelMm), ro("Run", u, r.runMm),
                    ReadoutData("Multipliers", "csc ${LengthFormatter.decimal(r.m.csc, 4)}", "cot ${LengthFormatter.decimal(r.m.cot, 4)}")),
                    SimpleOffset.working(r, u).map { it.text }, headline,
                    mapOf("set" to u.primary(set), "run" to u.primary(r.runMm), "travel" to u.primary(r.travelMm))), r.travelMm)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- cut length

data class CutSolved(val fields: Map<String, FieldState>, val outcome: Outcome)

object CutSolver {
    val BW_ENDS = EndFitting.entries.filter { it.join == JoinType.BUTT_WELD }
    val SW_ENDS = EndFitting.entries.filter { it.join == JoinType.SOCKET_WELD }

    fun sizesFor(t: TableSet, join: JoinType): List<String> =
        (if (join == JoinType.BUTT_WELD) t.buttWeld else t.socketWeld).fittings.flatMap { s -> s.rows.map { it.nps } }.distinct()
            .sortedBy { com.mohdshayan.kickset.core.fittings.npsValue(it) }

    fun solve(i: CutInputs, u: UnitPrefs, rootGapDefault: Double, socketGapDefault: Double, t: TableSet): CutSolved {
        val fields = mutableMapOf<String, FieldState>()
        if (i.tab == "ELBOW") return solveElbow(i, u, t)
        val a = EndFitting.byName(i.endA) ?: EndFitting.BW_90LR
        val b = EndFitting.byName(i.endB) ?: EndFitting.BW_45LR
        val (ctoc, cf) = readLength(i.centreToCentre, "Centre to centre", examples(u, "48 3/8", "1000"), u); fields["ctoc"] = cf
        val (rg, rgf) = readLength(i.rootGap, "Root gap", examples(u, "1/8", "3"), u, positive = false); fields["rootGap"] = rgf
        val (sg, sgf) = readLength(i.socketGap, "Engagement gap", examples(u, "1/16", "1.6"), u, positive = false); fields["socketGap"] = sgf
        if (a.join != b.join) return CutSolved(fields, Outcome.Problem("Pick two butt-weld fittings or two socket-weld fittings."))
        val nps = if (a.join == JoinType.BUTT_WELD) i.nps else i.swNps
        val rowA = t.series(a.seriesId)?.row(nps)
        val rowB = t.series(b.seriesId)?.row(nps)
        if (rowA == null || rowB == null) {
            val missing = if (rowA == null) a else b
            return CutSolved(fields, Outcome.Problem("NPS $nps has no ${missing.label} in the ${if (a.join == JoinType.BUTT_WELD) "ASME B16.9" else "ASME B16.11"} table. Pick another size or fitting."))
        }
        if (i.centreToCentre.isBlank()) return CutSolved(fields, Outcome.Prompt("Enter centre to centre to solve."))
        if (fields.values.any { it.error != null } || ctoc == null) return CutSolved(fields, Outcome.Prompt("Fix the marked field to solve."))
        val rootGap = if (i.rootGap.isBlank()) rootGapDefault else rg ?: rootGapDefault
        val socketGap = if (i.socketGap.isBlank()) socketGapDefault else sg ?: socketGapDefault
        return when (val o = CutLength.solve(ctoc, CutEnd(a, a.takeoutMm(rowA)), CutEnd(b, b.takeoutMm(rowB)), rootGap, socketGap)) {
            CutLengthOutcome.MixedJoins -> CutSolved(fields, Outcome.Problem("Pick two butt-weld fittings or two socket-weld fittings."))
            CutLengthOutcome.TooShort -> CutSolved(fields, Outcome.Problem("The fittings and gaps take up more than ${u.primary(ctoc)}. Check centre to centre."))
            is CutLengthOutcome.Ok -> {
                val r = o.result
                val headline = "Cut ${u.primary(r.cutMm)} (${u.secondary(r.cutMm)}), NPS $nps, ${a.label} to ${b.label}, C-to-C ${u.primary(ctoc)}"
                CutSolved(fields, Outcome.Solved(
                    CalcKind.CUT_LENGTH,
                    listOf(ro("Cut length", u, r.cutMm), ro("${a.label} takeout", u, r.endA.takeoutMm), ro("${b.label} takeout", u, r.endB.takeoutMm),
                        ro(if (r.join == JoinType.BUTT_WELD) "Root gap per weld" else "Engagement gap per end", u, r.gapPerWeldMm)),
                    CutLength.working(r, u).map { it.text }, headline,
                    mapOf("ctoc" to "C-to-C ${u.primary(ctoc)}", "a" to u.primary(r.endA.takeoutMm), "b" to u.primary(r.endB.takeoutMm), "cut" to "Cut ${u.primary(r.cutMm)}"),
                    sizeText = "NPS $nps",
                ))
            }
        }
    }

    private fun solveElbow(i: CutInputs, u: UnitPrefs, t: TableSet): CutSolved {
        val fields = mutableMapOf<String, FieldState>()
        val fitting = EndFitting.byName(i.elbowRadius)?.takeIf { it == EndFitting.BW_90LR || it == EndFitting.BW_90SR } ?: EndFitting.BW_90LR
        val row = t.series(fitting.seriesId)?.row(i.elbowNps)
            ?: return CutSolved(fields, Outcome.Problem("NPS ${i.elbowNps} has no ${fitting.label} in the ASME B16.9 table. Pick another size."))
        val pipe = t.pipe.sizes.firstOrNull { it.nps == i.elbowNps }
            ?: return CutSolved(fields, Outcome.Problem("NPS ${i.elbowNps} has no pipe OD in the bundled table. Pick another size."))
        if (i.elbowAngle.isBlank()) return CutSolved(mapOf("angle" to FieldState()), Outcome.Prompt("Enter the angle to cut the elbow to."))
        val angle = i.elbowAngle.trim().toDoubleOrNull()
        if (angle == null || angle <= 0.0 || angle >= 90.0) return CutSolved(mapOf("angle" to FieldState(error = "Angle must be between 0 and 90 degrees, like 30")), Outcome.Prompt("Fix the marked field to solve."))
        val r = CutElbow.solve(fitting.takeoutMm(row), angle, pipe.odIn * MM_PER_INCH)
        val headline = "Cut ${fitting.label} to ${angleLabel(angle)}: takeout ${u.primary(r.takeoutMm)} (${u.secondary(r.takeoutMm)}), outside mark ${u.primary(r.outsideArcMm)}, NPS ${i.elbowNps}"
        return CutSolved(fields, Outcome.Solved(
            CalcKind.CUT_ELBOW,
            listOf(ro("Takeout", u, r.takeoutMm), ro("Outside arc mark", u, r.outsideArcMm), ro("Centreline arc mark", u, r.centrelineArcMm), ro("Inside arc mark", u, r.insideArcMm)),
            CutElbow.working(r, u).map { it.text }, headline,
            mapOf("takeout" to "Takeout ${u.primary(r.takeoutMm)}", "angle" to angle.toString()),
            sizeText = "NPS ${i.elbowNps}",
        ))
    }
}

// ---------------------------------------------------------------------------------------------- templates

data class TemplateSolved(val outcome: Outcome, val curve: WrapCurve?, val hole: List<Pair<Double, Double>>?, val title: String, val fileStem: String, val fields: Map<String, FieldState>)

object TemplateSolver {
    fun solve(i: TemplateInputs, u: UnitPrefs, paper: Paper, t: TableSet): TemplateSolved {
        val fields = mutableMapOf<String, FieldState>()
        fun problem(text: String) = TemplateSolved(Outcome.Problem(text), null, null, "", "", fields)
        fun prompt(text: String) = TemplateSolved(Outcome.Prompt(text), null, null, "", "", fields)
        val branch = t.pipe.sizes.firstOrNull { it.nps == i.branchNps } ?: return problem("Pick a branch size.")
        val basis = DiameterBasis.entries.firstOrNull { it.name == i.basis } ?: DiameterBasis.OD
        val wallIn = branch.wallsIn[i.schedule]
        if (wallIn == null && basis != DiameterBasis.OD) return problem("NPS ${branch.nps} has no Sch ${i.schedule}. Pick another schedule.")
        val od = branch.odIn * MM_PER_INCH
        val wall = (wallIn ?: 0.0) * MM_PER_INCH
        val r = basisDiameter(od, wall, basis) / 2.0
        val stations = if (i.stations == 32) 32 else 16
        val kind = when (i.kind) { "MITER" -> CalcKind.MITER; "LATERAL" -> CalcKind.LATERAL; else -> CalcKind.SADDLE }
        val working = mutableListOf<String>()
        val outcome: TemplateOutcome
        val title: String
        val stem: String
        var headerR = 0.0
        when (kind) {
            CalcKind.MITER -> {
                val cut = if (i.singleCut) {
                    val a = i.singleCutAngle.trim().toDoubleOrNull()
                    if (i.singleCutAngle.isBlank()) return prompt("Enter the cut angle to draw the template.")
                    if (a == null || a <= 0 || a > 60) { fields["cut"] = FieldState(error = "Cut angle must be between 0 and 60 degrees, like 22.5"); return prompt("Fix the marked field to draw the template.") }
                    a
                } else {
                    val turn = i.miterTurn.trim().toDoubleOrNull()
                    if (i.miterTurn.isBlank()) return prompt("Enter the turn angle to draw the template.")
                    if (turn == null || turn <= 0 || turn > 180) { fields["turn"] = FieldState(error = "Turn must be between 0 and 180 degrees, like 90"); return prompt("Fix the marked field to draw the template.") }
                    WrapTemplates.miterCutAngle(turn, i.miterPieces.coerceIn(2, 5))
                }
                if (cut > 60) return problem("Each cut would be ${LengthFormatter.decimal(cut, 1)} degrees. Use more pieces or a smaller turn.")
                outcome = WrapTemplates.miter(od, r, cut, stations)
                title = if (i.singleCut) "Miter cut ${angleLabel(cut)}, NPS ${branch.nps}" else "Miter ${i.miterPieces} piece ${i.miterTurn.trim()}° turn, NPS ${branch.nps}"
                stem = "kickset-miter-nps${branch.nps.replace(' ', '-').replace('/', '_')}"
                if (!i.singleCut) working += "Cut angle = turn ${i.miterTurn.trim()}° / (2 x ${i.miterPieces - 1} joints) = ${LengthFormatter.decimal(cut, 3)}°"
                working += "Cutback y = r x tan(${LengthFormatter.decimal(cut, 3)}°) x (1 - cos phi), r = ${u.working(r)}"
            }
            else -> {
                val header = t.pipe.sizes.firstOrNull { it.nps == i.headerNps } ?: return problem("Pick a header size.")
                headerR = header.odIn * MM_PER_INCH / 2.0
                if (kind == CalcKind.LATERAL) {
                    val b = i.lateralAngle.trim().toDoubleOrNull()
                    if (i.lateralAngle.isBlank()) return prompt("Enter the lateral angle to draw the template.")
                    if (b == null || b < 30 || b > 89) { fields["lateral"] = FieldState(error = "Lateral angle must be between 30 and 89 degrees, like 45"); return prompt("Fix the marked field to draw the template.") }
                    outcome = WrapTemplates.lateral(od, r, headerR, b, stations)
                    if (outcome == TemplateOutcome.BranchTooLarge) return problem("A lateral branch cannot be larger than the header.")
                    title = "Lateral ${angleLabel(b)}, NPS ${branch.nps} on NPS ${header.nps}"
                    stem = "kickset-lateral-nps${branch.nps.replace(' ', '-').replace('/', '_')}-on-nps${header.nps.replace(' ', '-').replace('/', '_')}"
                    working += "Cutback y = (R - √(R² - r² sin² phi)) / sin ${angleLabel(b)} + r (1 - cos phi) / tan ${angleLabel(b)}"
                } else {
                    outcome = WrapTemplates.saddle(od, r, headerR, stations)
                    if (outcome == TemplateOutcome.BranchTooLarge) return problem("A saddle branch cannot be larger than the header.")
                    title = "Saddle, NPS ${branch.nps} on NPS ${header.nps}"
                    stem = "kickset-saddle-nps${branch.nps.replace(' ', '-').replace('/', '_')}-on-nps${header.nps.replace(' ', '-').replace('/', '_')}"
                    working += "Cutback y = R - √(R² - r² sin² phi)"
                }
                working += "R = header OD / 2 = ${u.working(headerR)}, r = branch ${basis.label} / 2 = ${u.working(r)}"
            }
        }
        val ok = outcome as? TemplateOutcome.Ok ?: return problem("That angle cannot be drawn. Pick another.")
        val c = ok.curve
        val fullTitle = "$title, ${basis.label} basis, $stations stations"
        working += "Girth = pi x OD ${u.working(od)} = ${u.working(c.girthMm)}, station spacing ${u.working(c.girthMm / stations)}"
        val maxIdx = c.ordinatesMm.indexOf(c.maxOrdinateMm)
        working += "Station ${maxIdx + 1} (${LengthFormatter.decimal(c.phiDeg[maxIdx], 2)}°): cutback ${u.working(c.maxOrdinateMm)}"
        val layout = SheetLayout(paper)
        val wrapSheets = TemplatePages.sheetCount(TemplateDrawings.wrap(c), layout)
        val holeSheets = ok.hole?.let { TemplatePages.sheetCount(TemplateDrawings.hole(it), layout) } ?: 0
        val sheets = wrapSheets + holeSheets + TemplatePages.ordinatePageCount(c)
        val readouts = mutableListOf(ro("Deepest cutback", u, c.maxOrdinateMm), ro("Girth to wrap", u, c.girthMm),
            ReadoutData("PDF on ${paper.label}", "$sheets sheets", "$wrapSheets wrap, ${if (holeSheets > 0) "$holeSheets hole, " else ""}${TemplatePages.ordinatePageCount(c)} ordinate"))
        ok.hole?.let { h ->
            val len = h.maxOf { it.first } - h.minOf { it.first }
            val wid = h.maxOf { it.second } - h.minOf { it.second }
            readouts += ReadoutData("Header hole", "${u.primary(len)} long", "${u.primary(wid)} around the header")
        }
        val headline = "$fullTitle: deepest cutback ${u.primary(c.maxOrdinateMm)} (${u.secondary(c.maxOrdinateMm)}), girth ${u.primary(c.girthMm)}"
        return TemplateSolved(Outcome.Solved(kind, readouts, working, headline, emptyMap(), sizeText = "NPS ${branch.nps}"), c, ok.hole, fullTitle, stem, fields)
    }
}

/** Recomputes a saved calculation for the cut sheet, in the unit it was saved in. */
object Replay {
    fun working(kind: String, inputsJson: String, unit: String, base: UnitPrefs, rootGap: Double, socketGap: Double, paper: Paper, t: TableSet): List<String> = try {
        val u = base.copy(system = if (unit == "INCH") UnitSystem.INCH else UnitSystem.MM)
        val json = CalcJson.json
        when (CalcKind.byName(kind)) {
            CalcKind.SIMPLE_OFFSET, CalcKind.ROLLING_OFFSET, CalcKind.PARALLEL_OFFSET ->
                (OffsetSolver.solve(json.decodeFromString(OffsetInputs.serializer(), inputsJson), u).outcome as? Outcome.Solved)?.working
            CalcKind.CUT_LENGTH, CalcKind.CUT_ELBOW ->
                (CutSolver.solve(json.decodeFromString(CutInputs.serializer(), inputsJson), u, rootGap, socketGap, t).outcome as? Outcome.Solved)?.working
            CalcKind.MITER, CalcKind.SADDLE, CalcKind.LATERAL ->
                (TemplateSolver.solve(json.decodeFromString(TemplateInputs.serializer(), inputsJson), u, paper, t).outcome as? Outcome.Solved)?.working
            null -> null
        } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}
