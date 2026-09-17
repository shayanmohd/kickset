package com.mohdshayan.kickset.core.fittings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TableSource(
    val publisher: String,
    val catalogue: String,
    val year: Int? = null,
    val pages: String,
    val url: String,
)

@Serializable
data class FittingRow(val nps: String, val `in`: Double, val mm: Double)

@Serializable
data class FittingSeries(val id: String, val name: String, val dimension: String, val measuredTo: String, val rows: List<FittingRow>) {
    fun row(nps: String): FittingRow? = rows.firstOrNull { it.nps == nps }
}

@Serializable
data class FittingStandard(val standard: String, val note: String, val sources: List<TableSource>, val fittings: List<FittingSeries>)

@Serializable
data class FlangeRow(
    val nps: String, val bolts: Int, val studIn: String,
    val boltCircleIn: Double, val boltCircleMm: Double,
    val studLengthIn: Double, val studLengthMm: Double,
)

@Serializable
data class FlangeClass(val `class`: Int, val rows: List<FlangeRow>)

@Serializable
data class FlangeStandard(val standard: String, val note: String, val sources: List<TableSource>, val classes: List<FlangeClass>)

@Serializable
data class PipeSize(val nps: String, val odIn: Double, val odMm: Double, val wallsIn: Map<String, Double>)

@Serializable
data class PipeStandard(val standard: String, val note: String, val sources: List<TableSource>, val schedules: List<String>, val sizes: List<PipeSize>)

/** Every bundled table, parsed once. */
class TableSet(val buttWeld: FittingStandard, val socketWeld: FittingStandard, val flanges: FlangeStandard, val pipe: PipeStandard) {

    fun series(id: String): FittingSeries? = (buttWeld.fittings + socketWeld.fittings).firstOrNull { it.id == id }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(b169: String, b1611: String, b165: String, b3610: String) = TableSet(
            json.decodeFromString(FittingStandard.serializer(), b169),
            json.decodeFromString(FittingStandard.serializer(), b1611),
            json.decodeFromString(FlangeStandard.serializer(), b165),
            json.decodeFromString(PipeStandard.serializer(), b3610),
        )
    }
}

/** Sorts "1/8" < "1/2" < "1 1/4" < "24" by value. */
fun npsValue(nps: String): Double {
    val parts = nps.trim().split(" ")
    var v = 0.0
    for (p in parts) {
        v += if (p.contains('/')) p.substringBefore('/').toDouble() / p.substringAfter('/').toDouble() else p.toDouble()
    }
    return v
}
