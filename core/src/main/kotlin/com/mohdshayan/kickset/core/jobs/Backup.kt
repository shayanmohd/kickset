package com.mohdshayan.kickset.core.jobs

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

const val BACKUP_FORMAT = "kickset-backup"
const val BACKUP_SCHEMA = 1

@Serializable
data class BackupPrefs(
    val unitSystem: String = "MM",
    val inchPrecision: Int = 16,
    val mmPrecision: Double = 1.0,
    val rootGapMm: Double = 3.0,
    val socketGapMm: Double = 1.6,
    val paperSize: String = "A4",
    val themeMode: String = "SYSTEM",
)

@Serializable
data class BackupCalc(
    val kind: String,
    val label: String,
    val inputsJson: String,
    val headline: String,
    val unitSystem: String,
    val createdAt: Long,
)

@Serializable
data class BackupJob(
    val name: String,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val calcs: List<BackupCalc> = emptyList(),
)

@Serializable
data class BackupFile(
    val format: String,
    val schema: Int,
    val exportedAt: Long,
    val prefs: BackupPrefs = BackupPrefs(),
    val jobs: List<BackupJob>,
)

sealed interface BackupRead {
    data class Valid(val file: BackupFile) : BackupRead
    data class Invalid(val reason: String) : BackupRead
}

/**
 * Writes and checks backup files. A file is accepted only when it is complete, says it is a Kickset
 * backup of a schema this build knows, and every field is inside sane limits; anything else is rejected
 * whole, before the database is touched.
 */
object BackupCodec {
    const val MAX_BYTES = 8 * 1024 * 1024
    const val MAX_JOBS = 5_000
    const val MAX_CALCS_PER_JOB = 2_000
    const val MAX_NAME = 200
    const val MAX_NOTES = 5_000
    const val MAX_HEADLINE = 500
    const val MAX_INPUTS = 20_000

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun decode(bytes: ByteArray): BackupRead {
        if (bytes.isEmpty()) return BackupRead.Invalid("empty")
        if (bytes.size > MAX_BYTES) return BackupRead.Invalid("too large")
        val text = try {
            Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (e: java.nio.charset.CharacterCodingException) {
            return BackupRead.Invalid("not UTF-8 text")
        }
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            return BackupRead.Invalid("not a complete backup")
        }
        return validate(file)
    }

    fun validate(f: BackupFile): BackupRead {
        fun bad(r: String) = BackupRead.Invalid(r)
        if (f.format != BACKUP_FORMAT) return bad("wrong format")
        if (f.schema != BACKUP_SCHEMA) return bad("unknown schema ${f.schema}")
        if (f.exportedAt < 0) return bad("bad date")
        val p = f.prefs
        if (p.unitSystem !in setOf("MM", "INCH")) return bad("bad unit system")
        if (p.inchPrecision !in setOf(16, 32)) return bad("bad inch precision")
        if (p.mmPrecision !in setOf(1.0, 0.5)) return bad("bad mm precision")
        if (!(p.rootGapMm.isFinite() && p.rootGapMm in 0.0..25.0)) return bad("bad root gap")
        if (!(p.socketGapMm.isFinite() && p.socketGapMm in 0.0..25.0)) return bad("bad socket gap")
        if (p.paperSize !in setOf("A4", "LETTER")) return bad("bad paper")
        if (p.themeMode !in setOf("SYSTEM", "LIGHT", "DARK")) return bad("bad theme")
        if (f.jobs.size > MAX_JOBS) return bad("too many jobs")
        for (j in f.jobs) {
            if (j.name.isBlank() || j.name.length > MAX_NAME) return bad("bad job name")
            if (j.notes.length > MAX_NOTES) return bad("notes too long")
            if (j.createdAt < 0 || j.updatedAt < 0) return bad("bad job date")
            if (j.calcs.size > MAX_CALCS_PER_JOB) return bad("too many calculations")
            for (c in j.calcs) {
                if (CalcKind.byName(c.kind) == null) return bad("unknown kind ${c.kind.take(40)}")
                if (c.label.isBlank() || c.label.length > MAX_NAME) return bad("bad label")
                if (c.headline.length > MAX_HEADLINE) return bad("headline too long")
                if (c.unitSystem !in setOf("MM", "INCH")) return bad("bad calc unit")
                if (c.createdAt < 0) return bad("bad calc date")
                if (c.inputsJson.length > MAX_INPUTS) return bad("inputs too long")
                val obj: JsonObject = try { json.parseToJsonElement(c.inputsJson).jsonObject } catch (e: Exception) { return bad("inputs are not an object") }
                if (obj.isEmpty()) return bad("inputs empty")
            }
        }
        return BackupRead.Valid(f)
    }

    fun fileName(isoDate: String) = "kickset-backup-$isoDate.json"
}

object CsvExporter {
    const val HEADER = "job,label,kind,unit,headline,inputs"

    fun escape(v: String): String {
        val needs = v.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || v.startsWith(" ") || v.endsWith(" ")
        // A leading = + - @ would be run as a formula by spreadsheet apps; prefix it with a quote mark.
        val safe = if (v.isNotEmpty() && v[0] in "=+-@") "'$v" else v
        return if (needs || safe != v) "\"" + safe.replace("\"", "\"\"") + "\"" else v
    }

    fun write(jobName: String, rows: List<BackupCalc>): String = buildString {
        append(HEADER).append("\r\n")
        for (r in rows) {
            append(listOf(jobName, r.label, r.kind, r.unitSystem, r.headline, r.inputsJson).joinToString(",") { escape(it) }).append("\r\n")
        }
    }
}
