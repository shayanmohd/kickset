package com.mohdshayan.kickset.core.template

import com.mohdshayan.kickset.core.offset.rad
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

enum class TemplateKind(val label: String) { MITER("Miter"), SADDLE("Saddle"), LATERAL("Lateral") }

/** Which diameter of the branch the cutback is worked from. The header always sits on its OD. */
enum class DiameterBasis(val label: String) { OD("OD"), MEAN("Mean"), ID("ID") }

fun basisDiameter(odMm: Double, wallMm: Double, basis: DiameterBasis) = when (basis) {
    DiameterBasis.OD -> odMm
    DiameterBasis.MEAN -> odMm - wallMm
    DiameterBasis.ID -> odMm - 2 * wallMm
}

/** Cutback y at each station phi, measured back from the longest point of the pipe end. */
class WrapCurve(val stations: Int, val girthMm: Double, val phiDeg: List<Double>, val ordinatesMm: List<Double>, val exact: (Double) -> Double) {
    val maxOrdinateMm: Double get() = ordinatesMm.max()
    /** x along the wrap for station i. */
    fun xMm(i: Int) = girthMm * i / stations
}

sealed interface TemplateOutcome {
    data class Ok(val curve: WrapCurve, val hole: List<Pair<Double, Double>>?) : TemplateOutcome
    data object BranchTooLarge : TemplateOutcome
    data object BadAngle : TemplateOutcome
}

object WrapTemplates {
    val STATION_CHOICES = listOf(16, 32)
    const val MITER_MIN_PIECES = 2
    const val MITER_MAX_PIECES = 5

    /** For a turn of `turnDeg` made from `pieces` pieces, each cut face sits at turn / (2 x joints). */
    fun miterCutAngle(turnDeg: Double, pieces: Int): Double {
        require(pieces in MITER_MIN_PIECES..MITER_MAX_PIECES)
        return turnDeg / (2.0 * (pieces - 1))
    }

    private fun curve(stations: Int, girth: Double, f: (Double) -> Double): WrapCurve {
        val phis = (0..stations).map { 360.0 * it / stations }
        return WrapCurve(stations, girth, phis, phis.map { f(rad(it)) }, f)
    }

    /** Miter: y = r tan(a) (1 - cos phi). */
    fun miter(wrapOdMm: Double, r: Double, cutAngleDeg: Double, stations: Int): TemplateOutcome {
        if (!(cutAngleDeg > 0.0 && cutAngleDeg <= 60.0)) return TemplateOutcome.BadAngle
        val t = tan(rad(cutAngleDeg))
        return TemplateOutcome.Ok(curve(stations, PI * wrapOdMm) { phi -> r * t * (1 - cos(phi)) }, null)
    }

    /** Saddle (fishmouth) on a header of radius R: y = R - sqrt(R^2 - r^2 sin^2 phi). */
    fun saddle(wrapOdMm: Double, r: Double, headerR: Double, stations: Int): TemplateOutcome {
        if (r > headerR + 1e-9) return TemplateOutcome.BranchTooLarge
        val c = curve(stations, PI * wrapOdMm) { phi -> headerR - sqrt((headerR * headerR - r * r * sin(phi) * sin(phi)).coerceAtLeast(0.0)) }
        return TemplateOutcome.Ok(c, headerHole(r, headerR, 90.0))
    }

    /** Lateral at angle b: y = (R - sqrt(R^2 - r^2 sin^2 phi)) / sin b + r (1 - cos phi) / tan b. */
    fun lateral(wrapOdMm: Double, r: Double, headerR: Double, angleDeg: Double, stations: Int): TemplateOutcome {
        if (!(angleDeg >= 30.0 && angleDeg <= 89.0)) return TemplateOutcome.BadAngle
        if (r > headerR + 1e-9) return TemplateOutcome.BranchTooLarge
        val b = rad(angleDeg)
        val c = curve(stations, PI * wrapOdMm) { phi ->
            (headerR - sqrt((headerR * headerR - r * r * sin(phi) * sin(phi)).coerceAtLeast(0.0))) / sin(b) + r * (1 - cos(phi)) / tan(b)
        }
        return TemplateOutcome.Ok(c, headerHole(r, headerR, angleDeg))
    }

    /**
     * Hole outline on the header, unrolled: x along the header axis, s around the header surface.
     * A branch point at station phi meets the header where x = r cos(phi) sin(b) + t cos(b) and
     * s = R asin(r sin(phi) / R), with t = (sqrt(R^2 - r^2 sin^2 phi) + r cos(phi) cos(b)) / sin(b).
     * x is shifted so the hole is centred on the branch axis at the header's top line.
     */
    fun headerHole(r: Double, headerR: Double, angleDeg: Double, samples: Int = 360): List<Pair<Double, Double>> {
        val b = rad(angleDeg)
        val pts = (0..samples).map { i ->
            val phi = 2 * PI * i / samples
            val root = sqrt((headerR * headerR - r * r * sin(phi) * sin(phi)).coerceAtLeast(0.0))
            val t = (root + r * cos(phi) * cos(b)) / sin(b)
            val x = r * cos(phi) * sin(b) + t * cos(b)
            val s = headerR * asin((r * sin(phi) / headerR).coerceIn(-1.0, 1.0))
            x to s
        }
        val cx = (pts.maxOf { it.first } + pts.minOf { it.first }) / 2
        return pts.map { (it.first - cx) to it.second }
    }
}
