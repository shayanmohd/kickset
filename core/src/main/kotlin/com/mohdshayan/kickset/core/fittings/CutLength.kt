package com.mohdshayan.kickset.core.fittings

import com.mohdshayan.kickset.core.offset.angleLabel
import com.mohdshayan.kickset.core.offset.rad
import com.mohdshayan.kickset.core.offset.WorkLine
import com.mohdshayan.kickset.core.units.LengthFormatter
import com.mohdshayan.kickset.core.units.MM_PER_INCH
import com.mohdshayan.kickset.core.units.UnitPrefs
import kotlin.math.tan

enum class JoinType { BUTT_WELD, SOCKET_WELD }

/** The fittings a fitter can put at either end of a cut, with the table series each reads. */
enum class EndFitting(val label: String, val seriesId: String, val join: JoinType) {
    BW_90LR("90 LR elbow", "BW_90LR", JoinType.BUTT_WELD),
    BW_90SR("90 SR elbow", "BW_90SR", JoinType.BUTT_WELD),
    BW_45LR("45 LR elbow", "BW_45LR", JoinType.BUTT_WELD),
    BW_TEE("Tee", "BW_TEE", JoinType.BUTT_WELD),
    BW_REDUCER("Concentric reducer", "BW_REDUCER", JoinType.BUTT_WELD),
    SW_90("SW 90 elbow", "SW_90", JoinType.SOCKET_WELD),
    SW_45("SW 45 elbow", "SW_45", JoinType.SOCKET_WELD),
    SW_TEE("SW tee", "SW_TEE", JoinType.SOCKET_WELD),
    SW_COUPLING("SW coupling", "SW_COUPLING", JoinType.SOCKET_WELD);

    /**
     * Takeout from the exact published inch fraction (the mm column in the JSON is rounded for display).
     * A coupling's table value is the gap between its two socket bottoms; each side takes half.
     */
    fun takeoutMm(row: FittingRow): Double = (row.`in` * MM_PER_INCH).let { if (this == SW_COUPLING) it / 2.0 else it }

    companion object {
        fun byName(name: String): EndFitting? = entries.firstOrNull { it.name == name }
    }
}

data class CutEnd(val fitting: EndFitting, val takeoutMm: Double)

data class CutLengthResult(
    val centreToCentreMm: Double,
    val endA: CutEnd,
    val endB: CutEnd,
    val gapPerWeldMm: Double,
    val cutMm: Double,
    val join: JoinType,
)

sealed interface CutLengthOutcome {
    data class Ok(val result: CutLengthResult) : CutLengthOutcome
    data object MixedJoins : CutLengthOutcome
    data object TooShort : CutLengthOutcome
}

object CutLength {
    /**
     * Butt weld: cut = C-to-C minus both takeouts minus one root gap per weld (two welds).
     * Socket weld: cut = C-to-C minus both centre-to-socket-bottom dimensions minus both engagement gaps.
     */
    fun solve(centreToCentreMm: Double, a: CutEnd, b: CutEnd, rootGapMm: Double, socketGapMm: Double): CutLengthOutcome {
        require(centreToCentreMm > 0 && rootGapMm >= 0 && socketGapMm >= 0)
        if (a.fitting.join != b.fitting.join) return CutLengthOutcome.MixedJoins
        val gap = if (a.fitting.join == JoinType.BUTT_WELD) rootGapMm else socketGapMm
        val cut = centreToCentreMm - a.takeoutMm - b.takeoutMm - 2.0 * gap
        if (cut <= 0.0) return CutLengthOutcome.TooShort
        return CutLengthOutcome.Ok(CutLengthResult(centreToCentreMm, a, b, gap, cut, a.fitting.join))
    }

    /**
     * Every figure in the chain is printed exactly, and every running total is worked from the printed
     * figures, so the subtraction reads true off the screen. Both gaps come off on one line because a
     * gap set in millimetres is no exact fraction of an inch: taking them off last leaves the rounding
     * in the one place it belongs, the cut itself, which is the answer printed above the working.
     */
    fun working(r: CutLengthResult, u: UnitPrefs): List<WorkLine> {
        val gapName = if (r.join == JoinType.BUTT_WELD) "root gap" else "engagement gap"
        val gapWhere = if (r.join == JoinType.BUTT_WELD) "both welds" else "both ends"
        val ctoc = u.exact(r.centreToCentreMm)
        val a = u.exact(r.endA.takeoutMm)
        val b = u.exact(r.endB.takeoutMm)
        val gap = u.exact(r.gapPerWeldMm)
        val afterA = u.exact(ctoc.value - a.value)
        val afterB = u.exact(afterA.value - b.value)
        val gaps = u.exact(2.0 * gap.value)
        // The cut is written from the answer above the working, finely enough that it is also what the
        // printed chain comes to: the two can only part company where the exact cut sits on the half.
        val cut = u.answer(r.cutMm) { u.at(afterB.value - gaps.value, it.level).text == it.text }
        return listOf(
            WorkLine("Centre to centre ${ctoc.text}"),
            WorkLine("minus ${r.endA.fitting.label} takeout ${a.text} = ${afterA.text}"),
            WorkLine("minus ${r.endB.fitting.label} takeout ${b.text} = ${afterB.text}"),
            WorkLine("minus $gapName ${gap.text} at $gapWhere, ${gaps.text} total = cut ${cut.text}"),
        )
    }
}

data class CutElbowResult(
    val a90Mm: Double, val angleDeg: Double, val odMm: Double,
    val takeoutMm: Double, val centrelineArcMm: Double, val outsideArcMm: Double, val insideArcMm: Double,
)

object CutElbow {
    /**
     * A 90 elbow cut down to angle a: takeout = A90 x tan(a / 2). The bend radius equals A90, so the
     * marks along the elbow from the weld end are the arcs R x a at the centreline, R + OD/2 outside
     * and R - OD/2 inside.
     */
    fun solve(a90Mm: Double, angleDeg: Double, odMm: Double): CutElbowResult {
        require(a90Mm > 0 && angleDeg > 0 && angleDeg < 90 && odMm > 0)
        val t = rad(angleDeg)
        return CutElbowResult(
            a90Mm, angleDeg, odMm,
            a90Mm * tan(t / 2.0), a90Mm * t, (a90Mm + odMm / 2.0) * t, (a90Mm - odMm / 2.0).coerceAtLeast(0.0) * t,
        )
    }

    /**
     * The radii are printed exactly and the two multipliers carry whatever places it takes for the
     * products to come out as the marks printed beside them, so each line can be repeated on a
     * calculator. The outside and inside radii are spelled out rather than left as "OD/2", which was
     * a line nobody could check without looking the pipe OD up somewhere else.
     */
    fun working(r: CutElbowResult, u: UnitPrefs): List<WorkLine> {
        val a90 = u.exact(r.a90Mm)
        val od2 = u.exact(r.odMm / 2.0)
        val outside = u.exact(a90.value + od2.value)
        val inside = u.exact((a90.value - od2.value).coerceAtLeast(0.0))
        val (takeout, half) = u.product(r.takeoutMm, a90.value, tan(rad(r.angleDeg) / 2.0))
        // One radian figure serves all three arcs, so it carries the places the longest of them needs.
        val centre = u.answer(r.centrelineArcMm)
        val outsideArc = u.answer(r.outsideArcMm)
        val insideArc = u.answer(r.insideArcMm)
        val radians = LengthFormatter.multiplier(rad(r.angleDeg)) {
            u.at(a90.value * it, centre.level).text == centre.text &&
                u.at(outside.value * it, outsideArc.level).text == outsideArc.text &&
                u.at(inside.value * it, insideArc.level).text == insideArc.text
        }
        return listOf(
            WorkLine("Takeout = A90 ${a90.text} x tan(${angleLabel(r.angleDeg)} / 2) ($half) = ${takeout.text}"),
            WorkLine("Centreline arc = A90 ${a90.text} x $radians rad = ${centre.text}"),
            WorkLine("Outside arc = (A90 ${a90.text} + OD/2 ${od2.text}) x $radians rad = ${outsideArc.text}"),
            WorkLine("Inside arc = (A90 ${a90.text} - OD/2 ${od2.text}) x $radians rad = ${insideArc.text}"),
        )
    }
}
