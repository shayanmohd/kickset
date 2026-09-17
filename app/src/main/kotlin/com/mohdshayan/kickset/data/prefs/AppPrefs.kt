package com.mohdshayan.kickset.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mohdshayan.kickset.core.jobs.BackupPrefs
import com.mohdshayan.kickset.core.pdf.Paper
import com.mohdshayan.kickset.core.units.UnitPrefs
import com.mohdshayan.kickset.core.units.UnitSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

data class Settings(
    val unitSystem: UnitSystem = UnitSystem.MM,
    val inchPrecision: Int = 16,
    val mmPrecision: Double = 1.0,
    val rootGapMm: Double = 3.0,
    val socketGapMm: Double = 1.6,
    val paper: Paper = Paper.A4,
    val themeMode: String = "SYSTEM",
    val unitsChosen: Boolean = false,
    val usageCountsOptIn: Boolean = false,
    val countSolves: Long = 0,
    val countSaves: Long = 0,
    val countExports: Long = 0,
) {
    val units get() = UnitPrefs(unitSystem, inchPrecision, mmPrecision)
}

/** Settings and small flags in DataStore. Jobs and calculations live in Room. */
class AppPrefs(private val context: Context) {

    private object Keys {
        val UNIT_SYSTEM = stringPreferencesKey("unit_system")
        val INCH_PRECISION = intPreferencesKey("inch_precision")
        val MM_PRECISION = doublePreferencesKey("mm_precision")
        val ROOT_GAP_MM = doublePreferencesKey("root_gap_mm")
        val SOCKET_GAP_MM = doublePreferencesKey("socket_gap_mm")
        val PAPER_SIZE = stringPreferencesKey("paper_size")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val UNITS_CHOSEN = booleanPreferencesKey("units_chosen")
        val SUCCESS_COUNT = intPreferencesKey("success_count")
        val REVIEW_PROMPTED = booleanPreferencesKey("review_prompted")
        val FIRST_OPENED_AT = longPreferencesKey("first_opened_at")
        val USAGE_OPT_IN = booleanPreferencesKey("usage_counts_opt_in")
        val COUNT_SOLVES = longPreferencesKey("count_solves")
        val COUNT_SAVES = longPreferencesKey("count_saves")
        val COUNT_EXPORTS = longPreferencesKey("count_exports")
        val LAST_TRAVEL_MM = doublePreferencesKey("last_travel_mm")
        fun draft(name: String) = stringPreferencesKey("draft_$name")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            unitSystem = if (p[Keys.UNIT_SYSTEM] == "INCH") UnitSystem.INCH else UnitSystem.MM,
            inchPrecision = p[Keys.INCH_PRECISION]?.takeIf { it == 16 || it == 32 } ?: 16,
            mmPrecision = p[Keys.MM_PRECISION]?.takeIf { it == 1.0 || it == 0.5 } ?: 1.0,
            rootGapMm = p[Keys.ROOT_GAP_MM] ?: 3.0,
            socketGapMm = p[Keys.SOCKET_GAP_MM] ?: 1.6,
            paper = if (p[Keys.PAPER_SIZE] == "LETTER") Paper.LETTER else Paper.A4,
            themeMode = p[Keys.THEME_MODE]?.takeIf { it in setOf("SYSTEM", "LIGHT", "DARK") } ?: "SYSTEM",
            unitsChosen = p[Keys.UNITS_CHOSEN] ?: false,
            usageCountsOptIn = p[Keys.USAGE_OPT_IN] ?: false,
            countSolves = p[Keys.COUNT_SOLVES] ?: 0,
            countSaves = p[Keys.COUNT_SAVES] ?: 0,
            countExports = p[Keys.COUNT_EXPORTS] ?: 0,
        )
    }.distinctUntilChanged()

    suspend fun current(): Settings = settings.first()

    suspend fun setUnitSystem(s: UnitSystem) = context.dataStore.edit { it[Keys.UNIT_SYSTEM] = s.name; it[Keys.UNITS_CHOSEN] = true }
    suspend fun setInchPrecision(v: Int) = context.dataStore.edit { it[Keys.INCH_PRECISION] = v }
    suspend fun setMmPrecision(v: Double) = context.dataStore.edit { it[Keys.MM_PRECISION] = v }
    suspend fun setRootGap(mm: Double) = context.dataStore.edit { it[Keys.ROOT_GAP_MM] = mm }
    suspend fun setSocketGap(mm: Double) = context.dataStore.edit { it[Keys.SOCKET_GAP_MM] = mm }
    suspend fun setPaper(p: Paper) = context.dataStore.edit { it[Keys.PAPER_SIZE] = p.name }
    suspend fun setThemeMode(m: String) = context.dataStore.edit { it[Keys.THEME_MODE] = m }
    suspend fun setUsageOptIn(on: Boolean) = context.dataStore.edit { it[Keys.USAGE_OPT_IN] = on }

    suspend fun markOpened(now: Long) = context.dataStore.edit { if (it[Keys.FIRST_OPENED_AT] == null) it[Keys.FIRST_OPENED_AT] = now }

    /** Local counts, kept only when the user opted in. Nothing leaves the phone. */
    suspend fun count(kind: String) = context.dataStore.edit { p ->
        if (p[Keys.USAGE_OPT_IN] != true) return@edit
        val key = when (kind) { "solve" -> Keys.COUNT_SOLVES; "save" -> Keys.COUNT_SAVES; else -> Keys.COUNT_EXPORTS }
        p[key] = (p[key] ?: 0L) + 1
    }

    /**
     * Records a successful save or export and says whether this is the moment for the one review prompt:
     * the third success, never on the first day, never twice.
     */
    suspend fun recordSuccessAndShouldPrompt(now: Long): Boolean {
        var prompt = false
        context.dataStore.edit { p ->
            val n = (p[Keys.SUCCESS_COUNT] ?: 0) + 1
            p[Keys.SUCCESS_COUNT] = n
            val first = p[Keys.FIRST_OPENED_AT] ?: now
            if (n >= 3 && p[Keys.REVIEW_PROMPTED] != true && now - first >= 24L * 3600 * 1000) {
                p[Keys.REVIEW_PROMPTED] = true
                prompt = true
            }
        }
        return prompt
    }

    val lastTravelMm: Flow<Double?> = context.dataStore.data.map { it[Keys.LAST_TRAVEL_MM] }
    suspend fun setLastTravel(mm: Double) = context.dataStore.edit { it[Keys.LAST_TRAVEL_MM] = mm }

    suspend fun draft(name: String): String? = context.dataStore.data.first()[Keys.draft(name)]
    suspend fun saveDraft(name: String, json: String) = context.dataStore.edit { it[Keys.draft(name)] = json }

    suspend fun toBackup(): BackupPrefs {
        val s = current()
        return BackupPrefs(s.unitSystem.name, s.inchPrecision, s.mmPrecision, s.rootGapMm, s.socketGapMm, s.paper.name, s.themeMode)
    }

    suspend fun restore(b: BackupPrefs) = context.dataStore.edit {
        it[Keys.UNIT_SYSTEM] = b.unitSystem
        it[Keys.INCH_PRECISION] = b.inchPrecision
        it[Keys.MM_PRECISION] = b.mmPrecision
        it[Keys.ROOT_GAP_MM] = b.rootGapMm
        it[Keys.SOCKET_GAP_MM] = b.socketGapMm
        it[Keys.PAPER_SIZE] = b.paperSize
        it[Keys.THEME_MODE] = b.themeMode
        it[Keys.UNITS_CHOSEN] = true
    }
}
