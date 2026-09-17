package com.mohdshayan.kickset.core.offset

import com.mohdshayan.kickset.core.units.LengthFormatter
import com.mohdshayan.kickset.core.units.UnitPrefs
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** The fitting angles fitters reach for first. */
val PRESET_ANGLES = listOf(11.25, 22.5, 30.0, 45.0, 60.0)

fun rad(deg: Double) = deg * PI / 180.0
fun deg(rad: Double) = rad * 180.0 / PI

/** csc and cot of a fitting angle: the travel and run multipliers. */
data class Multipliers(val angleDeg: Double) {
    val csc: Double = 1.0 / sin(rad(angleDeg))
    val cot: Double = 1.0 / tan(rad(angleDeg))
}

fun angleLabel(deg: Double): String {
    val s = LengthFormatter.decimal(deg, 2).trimEnd('0').trimEnd('.')
    return "$s°"
}

fun validAngle(deg: Double) = deg.isFinite() && deg > 0.0 && deg < 90.0

/** One line of shown working, already written out. */
data class WorkLine(val text: String)

data class SimpleOffsetResult(val setMm: Double, val angleDeg: Double, val travelMm: Double, val runMm: Double, val m: Multipliers)

object SimpleOffset {
    fun solve(setMm: Double, angleDeg: Double): SimpleOffsetResult {
        require(setMm > 0 && validAngle(angleDeg))
        val m = Multipliers(angleDeg)
        return SimpleOffsetResult(setMm, angleDeg, setMm * m.csc, setMm * m.cot, m)
    }

    fun working(r: SimpleOffsetResult, u: UnitPrefs) = listOf(
        WorkLine("Set ${u.working(r.setMm)} x csc ${angleLabel(r.angleDeg)} (${LengthFormatter.decimal(r.m.csc, 4)}) = travel ${u.working(r.travelMm)}"),
        WorkLine("Set ${u.working(r.setMm)} x cot ${angleLabel(r.angleDeg)} (${LengthFormatter.decimal(r.m.cot, 4)}) = run ${u.working(r.runMm)}"),
    )
}

data class RollingOffsetResult(
    val setMm: Double, val rollMm: Double, val trueOffsetMm: Double,
    val angleDeg: Double, val travelMm: Double, val runMm: Double, val m: Multipliers,
    val angleFromRun: Boolean,
)

object RollingOffset {
    fun trueOffset(setMm: Double, rollMm: Double) = sqrt(setMm * setMm + rollMm * rollMm)

    /** Known fitting angle: true offset, then travel and run from it. */
    fun solve(setMm: Double, rollMm: Double, angleDeg: Double): RollingOffsetResult {
        require(setMm >= 0 && rollMm >= 0 && (setMm > 0 || rollMm > 0) && validAngle(angleDeg))
        val t = trueOffset(setMm, rollMm)
        val m = Multipliers(angleDeg)
        return RollingOffsetResult(setMm, rollMm, t, angleDeg, t * m.csc, t * m.cot, m, false)
    }

    /** Known run: the fitting angle is atan(true offset / run). */
    fun solveFromRun(setMm: Double, rollMm: Double, runMm: Double): RollingOffsetResult {
        require(setMm >= 0 && rollMm >= 0 && (setMm > 0 || rollMm > 0) && runMm > 0)
        val t = trueOffset(setMm, rollMm)
        val a = deg(atan(t / runMm))
        return RollingOffsetResult(setMm, rollMm, t, a, sqrt(t * t + runMm * runMm), runMm, Multipliers(a), true)
    }

    fun working(r: RollingOffsetResult, u: UnitPrefs): List<WorkLine> {
        val first = WorkLine("True offset = √(set ${u.working(r.setMm)}² + roll ${u.working(r.rollMm)}²) = ${u.working(r.trueOffsetMm)}")
        return if (!r.angleFromRun) listOf(
            first,
            WorkLine("True offset ${u.working(r.trueOffsetMm)} x csc ${angleLabel(r.angleDeg)} (${LengthFormatter.decimal(r.m.csc, 4)}) = travel ${u.working(r.travelMm)}"),
            WorkLine("True offset ${u.working(r.trueOffsetMm)} x cot ${angleLabel(r.angleDeg)} (${LengthFormatter.decimal(r.m.cot, 4)}) = run ${u.working(r.runMm)}"),
        ) else listOf(
            first,
            WorkLine("Angle = atan(true offset ${u.working(r.trueOffsetMm)} / run ${u.working(r.runMm)}) = ${LengthFormatter.decimal(r.angleDeg, 2)}°"),
            WorkLine("Travel = √(true offset² + run²) = ${u.working(r.travelMm)}"),
        )
    }
}

data class ParallelLine(val index: Int, val advanceMm: Double)
data class ParallelOffsetResult(val spreadMm: Double, val angleDeg: Double, val factor: Double, val lines: List<ParallelLine>)

object ParallelOffset {
    const val MIN_LINES = 2
    const val MAX_LINES = 8

    /**
     * Lines at a centre-to-centre spread S kicking together through the same angle: each line's
     * kick point moves along the run by S x tan(angle / 2) from the line inside it.
     */
    fun solve(spreadMm: Double, angleDeg: Double, lineCount: Int): ParallelOffsetResult {
        require(spreadMm > 0 && validAngle(angleDeg))
        // A line count out of range is clamped rather than thrown: it can only come from a hand-edited
        // backup, and a rack of eight is a better answer there than no answer at all.
        val n = lineCount.coerceIn(MIN_LINES, MAX_LINES)
        val k = tan(rad(angleDeg) / 2.0)
        return ParallelOffsetResult(spreadMm, angleDeg, k, (1..n).map { ParallelLine(it, (it - 1) * spreadMm * k) })
    }

    /**
     * Every line is worked from the spread and the factor, never from the rounded advance above it:
     * multiplying a figure already rounded to 1/16 inch prints arithmetic that does not check out
     * (2 x 5 in reading 9 15/16 in), which is exactly what a fitter would catch.
     */
    fun working(r: ParallelOffsetResult, u: UnitPrefs): List<WorkLine> {
        val factor = LengthFormatter.decimal(r.factor, 4)
        return listOf(
            WorkLine("Advance per line = spread ${u.working(r.spreadMm)} x tan(${angleLabel(r.angleDeg)} / 2) ($factor) = ${u.working(r.spreadMm * r.factor)}"),
        ) + r.lines.drop(1).map {
            WorkLine("Line ${it.index}: spread ${u.working(r.spreadMm)} x $factor x ${it.index - 1} = ${u.working(it.advanceMm)}")
        }
    }
}
