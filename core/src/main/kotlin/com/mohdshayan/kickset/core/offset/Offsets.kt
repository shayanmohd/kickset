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

    /** The set is printed exactly and each multiplier carries the places its own line needs. */
    fun working(r: SimpleOffsetResult, u: UnitPrefs): List<WorkLine> {
        val set = u.exact(r.setMm)
        val (travel, csc) = u.product(r.travelMm, set.value, r.m.csc)
        val (run, cot) = u.product(r.runMm, set.value, r.m.cot)
        return listOf(
            WorkLine("Set ${set.text} x csc ${angleLabel(r.angleDeg)} ($csc) = travel ${travel.text}"),
            WorkLine("Set ${set.text} x cot ${angleLabel(r.angleDeg)} ($cot) = run ${run.text}"),
        )
    }
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

    /**
     * The set, the roll and the run are printed exactly. A true offset is a square root, so it has no
     * exact form at all: it is printed to as many places as the lines under it need, and those lines
     * are worked from the figure printed here rather than from the unrounded one behind it.
     */
    fun working(r: RollingOffsetResult, u: UnitPrefs): List<WorkLine> {
        val set = u.exact(r.setMm)
        val roll = u.exact(r.rollMm)
        val angle = "${LengthFormatter.decimal(r.angleDeg, 2)}°"
        if (r.angleFromRun) {
            val run = u.exact(r.runMm)
            val travel = u.answer(r.travelMm)
            val t = u.answer(r.trueOffsetMm) { f ->
                "${LengthFormatter.decimal(deg(atan(f.value / run.value)), 2)}°" == angle &&
                    u.at(sqrt(f.value * f.value + run.value * run.value), travel.level).text == travel.text
            }
            return listOf(
                WorkLine("True offset = √(set ${set.text}² + roll ${roll.text}²) = ${t.text}"),
                WorkLine("Angle = atan(true offset ${t.text} / run ${run.text}) = $angle"),
                WorkLine("Travel = √(true offset ${t.text}² + run ${run.text}²) = ${travel.text}"),
            )
        }
        val travel = u.answer(r.travelMm)
        val run = u.answer(r.runMm)
        val t = u.answer(r.trueOffsetMm) { f ->
            LengthFormatter.multiplierOrNull(r.m.csc) { u.at(f.value * it, travel.level).text == travel.text } != null &&
                LengthFormatter.multiplierOrNull(r.m.cot) { u.at(f.value * it, run.level).text == run.text } != null
        }
        val csc = LengthFormatter.multiplier(r.m.csc) { u.at(t.value * it, travel.level).text == travel.text }
        val cot = LengthFormatter.multiplier(r.m.cot) { u.at(t.value * it, run.level).text == run.text }
        return listOf(
            WorkLine("True offset = √(set ${set.text}² + roll ${roll.text}²) = ${t.text}"),
            WorkLine("True offset ${t.text} x csc ${angleLabel(r.angleDeg)} ($csc) = travel ${travel.text}"),
            WorkLine("True offset ${t.text} x cot ${angleLabel(r.angleDeg)} ($cot) = run ${run.text}"),
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
        val spread = u.exact(r.spreadMm)
        val step = u.answer(r.spreadMm * r.factor)
        val advances = r.lines.drop(1).map { u.answer(it.advanceMm) }
        // One multiplier serves every line, so it carries the places the longest line needs.
        val factor = LengthFormatter.multiplier(r.factor) { f ->
            u.at(spread.value * f, step.level).text == step.text &&
                advances.withIndex().all { (k, a) -> u.at(spread.value * f * (k + 1), a.level).text == a.text }
        }
        return listOf(
            WorkLine("Advance per line = spread ${spread.text} x tan(${angleLabel(r.angleDeg)} / 2) ($factor) = ${step.text}"),
        ) + advances.withIndex().map { (k, a) ->
            WorkLine("Line ${k + 2}: spread ${spread.text} x $factor x ${k + 1} = ${a.text}")
        }
    }
}
