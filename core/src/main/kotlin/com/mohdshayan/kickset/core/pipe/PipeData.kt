package com.mohdshayan.kickset.core.pipe

import com.mohdshayan.kickset.core.fittings.PipeSize
import com.mohdshayan.kickset.core.units.MM_PER_INCH
import kotlin.math.PI

data class PipeDetail(
    val nps: String, val schedule: String,
    val odMm: Double, val wallMm: Double, val idMm: Double,
    val weightKgPerM: Double, val waterFilledKgPerM: Double, val volumeLitresPerM: Double,
) {
    val weightLbPerFt get() = weightKgPerM * KG_PER_M_TO_LB_PER_FT
    val waterFilledLbPerFt get() = waterFilledKgPerM * KG_PER_M_TO_LB_PER_FT
    val volumeUsGalPerFt get() = volumeLitresPerM * LITRES_PER_M_TO_GAL_PER_FT
}

const val KG_PER_M_TO_LB_PER_FT = 0.671968975
const val LITRES_PER_M_TO_GAL_PER_FT = 0.0805196

object PipeSchedule {
    /** Carbon steel plain-end weight per ASME B36.10M: kg/m = 0.0246615 x (D - t) x t, D and t in mm. */
    fun weightKgPerM(odMm: Double, wallMm: Double) = 0.0246615 * (odMm - wallMm) * wallMm

    /** Litres per metre of bore, which is also the kilograms of water it holds. */
    fun volumeLitresPerM(idMm: Double) = PI / 4.0 * idMm * idMm / 1000.0

    fun detail(size: PipeSize, schedule: String): PipeDetail? {
        val wallIn = size.wallsIn[schedule] ?: return null
        val od = size.odIn * MM_PER_INCH
        val wall = wallIn * MM_PER_INCH
        val id = od - 2 * wall
        val w = weightKgPerM(od, wall)
        val v = volumeLitresPerM(id)
        return PipeDetail(size.nps, schedule, od, wall, id, w, w + v, v)
    }
}
