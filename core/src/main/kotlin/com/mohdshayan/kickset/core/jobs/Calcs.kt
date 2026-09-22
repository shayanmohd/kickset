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

/**
 * The settings a saved calculation was worked in, frozen onto the row. Replaying a saved cut for the cut
 * sheet has to use these, not today's settings: a changed default gap or fraction denominator would print
 * working lines that contradict the headline saved beside them.
 */
@Serializable
data class CalcSettings(
    val inchPrecision: Int = 16,
    val mmPrecision: Double = 1.0,
    val rootGapMm: Double = 3.0,
    val socketGapMm: Double = 1.6,
) {
    val valid: Boolean
        get() = inchPrecision in setOf(16, 32) && mmPrecision in setOf(1.0, 0.5) &&
            rootGapMm.isFinite() && rootGapMm in 0.0..25.0 && socketGapMm.isFinite() && socketGapMm in 0.0..25.0

    companion object {
        /** Null for a row saved before snapshots existed, or one whose snapshot is unusable. */
        fun decode(json: String): CalcSettings? =
            if (json.isBlank()) null
            else runCatching { CalcJson.json.decodeFromString(serializer(), json) }.getOrNull()?.takeIf { it.valid }
    }
}

object CalcJson {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
}
