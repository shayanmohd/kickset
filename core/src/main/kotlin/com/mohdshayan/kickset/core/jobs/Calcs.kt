package com.mohdshayan.kickset.core.jobs

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What a saved calculation is. The names are stored in the database and in backups; never rename one. */
enum class CalcKind(val label: String) {
    SIMPLE_OFFSET("Simple offset"),
    ROLLING_OFFSET("Rolling offset"),
    PARALLEL_OFFSET("Parallel offsets"),
    CUT_LENGTH("Cut length"),
    CUT_ELBOW("Cut elbow"),
    MITER("Miter template"),
    SADDLE("Saddle template"),
    LATERAL("Lateral template");

    companion object {
        fun byName(name: String): CalcKind? = entries.firstOrNull { it.name == name }
    }
}

/** Inputs exactly as typed, so reopening a saved calculation shows the fitter his own numbers. */
@Serializable
data class OffsetInputs(
    val mode: String = "SIMPLE",
    val set: String = "",
    val roll: String = "",
    val run: String = "",
    val spread: String = "",
    val angle: Double = 45.0,
    val customAngle: String = "",
    val rollingSolveAngle: Boolean = false,
    val lines: Int = 3,
)

@Serializable
data class CutInputs(
    val tab: String = "CUT",
    val endA: String = "BW_90LR",
    val endB: String = "BW_45LR",
    val nps: String = "4",
    val swNps: String = "1",
    val centreToCentre: String = "",
    val rootGap: String = "",
    val socketGap: String = "",
    val elbowRadius: String = "BW_90LR",
    val elbowNps: String = "4",
    val elbowAngle: String = "30",
)

@Serializable
data class TemplateInputs(
    val kind: String = "SADDLE",
    val headerNps: String = "6",
    val branchNps: String = "4",
    val schedule: String = "STD",
    val miterPieces: Int = 3,
    val miterTurn: String = "90",
    val singleCut: Boolean = false,
    val singleCutAngle: String = "22.5",
    val lateralAngle: String = "45",
    val stations: Int = 16,
    val basis: String = "OD",
)

object CalcJson {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
}
