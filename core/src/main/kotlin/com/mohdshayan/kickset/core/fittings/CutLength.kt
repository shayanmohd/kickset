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

    fun working(r: CutLengthResult, u: UnitPrefs): List<WorkLine> {
        val gapName = if (r.join == JoinType.BUTT_WELD) "root gap" else "engagement gap"
        var running = r.centreToCentreMm
        val lines = mutableListOf(WorkLine("Centre to centre ${u.working(running)}"))
        running -= r.endA.takeoutMm
        lines += WorkLine("minus ${r.endA.fitting.label} takeout ${u.working(r.endA.takeoutMm)} = ${u.working(running)}")
        running -= r.endB.takeoutMm
        lines += WorkLine("minus ${r.endB.fitting.label} takeout ${u.working(r.endB.takeoutMm)} = ${u.working(running)}")
        running -= r.gapPerWeldMm
        lines += WorkLine("minus $gapName ${u.working(r.gapPerWeldMm)} = ${u.working(running)}")
        running -= r.gapPerWeldMm
        lines += WorkLine("minus $gapName ${u.working(r.gapPerWeldMm)} = cut ${u.working(running)}")
        return lines
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

    fun working(r: CutElbowResult, u: UnitPrefs) = listOf(
        WorkLine("Takeout = A90 ${u.working(r.a90Mm)} x tan(${angleLabel(r.angleDeg)} / 2) (${LengthFormatter.decimal(tan(rad(r.angleDeg) / 2), 4)}) = ${u.working(r.takeoutMm)}"),
        WorkLine("Centreline arc = ${u.working(r.a90Mm)} x ${LengthFormatter.decimal(rad(r.angleDeg), 4)} rad = ${u.working(r.centrelineArcMm)}"),
        WorkLine("Outside arc = (${u.working(r.a90Mm)} + OD/2) x ${LengthFormatter.decimal(rad(r.angleDeg), 4)} rad = ${u.working(r.outsideArcMm)}"),
        WorkLine("Inside arc = (${u.working(r.a90Mm)} - OD/2) x ${LengthFormatter.decimal(rad(r.angleDeg), 4)} rad = ${u.working(r.insideArcMm)}"),
    )
}
