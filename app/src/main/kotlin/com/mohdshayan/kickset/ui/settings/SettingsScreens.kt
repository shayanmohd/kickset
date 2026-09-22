package com.mohdshayan.kickset.ui.settings

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.kickset.core.fittings.TableSet
import com.mohdshayan.kickset.core.fittings.TableSource
import com.mohdshayan.kickset.core.jobs.BACKUP_FORMAT
import com.mohdshayan.kickset.core.jobs.BACKUP_SCHEMA
import com.mohdshayan.kickset.core.jobs.BackupCodec
import com.mohdshayan.kickset.core.jobs.BackupFile
import com.mohdshayan.kickset.core.jobs.BackupRead
import com.mohdshayan.kickset.core.jobs.BackupWrite
import com.mohdshayan.kickset.core.offset.Multipliers
import com.mohdshayan.kickset.core.offset.PRESET_ANGLES
import com.mohdshayan.kickset.core.offset.rad
import com.mohdshayan.kickset.core.offset.validAngle
import com.mohdshayan.kickset.core.pdf.Paper
import com.mohdshayan.kickset.core.units.LengthFormatter
import com.mohdshayan.kickset.core.units.LengthParser
import com.mohdshayan.kickset.core.units.ParsedLength
import com.mohdshayan.kickset.core.units.UnitSystem
import com.mohdshayan.kickset.data.export.Files
import com.mohdshayan.kickset.data.prefs.Settings
import com.mohdshayan.kickset.di.ServiceLocator
import com.mohdshayan.kickset.ui.components.ChoiceChips
import com.mohdshayan.kickset.ui.components.ClickableRow
import com.mohdshayan.kickset.ui.components.KicksetTopBar
import com.mohdshayan.kickset.ui.components.LengthField
import com.mohdshayan.kickset.ui.components.PrimaryButton
import com.mohdshayan.kickset.ui.components.SecondaryButton
import com.mohdshayan.kickset.ui.components.SectionLabel
import com.mohdshayan.kickset.ui.components.Segmented
import com.mohdshayan.kickset.ui.components.SkeletonRows
import com.mohdshayan.kickset.ui.components.UiMessage
import com.mohdshayan.kickset.ui.components.formatAngle
import com.mohdshayan.kickset.ui.theme.WorkingStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.tan

const val DISCLAIMER = "Results are reference values. Check against your drawings and the fittings in hand before you cut. Kickset is not affiliated with ASME."

data class SettingsUi(val settings: Settings, val jobCount: Int, val calcCount: Int)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = ServiceLocator.appPrefs
    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    /** A valid backup waiting for the user to pick merge or replace. */
    var pending by mutableStateOf<BackupFile?>(null)
        private set

    val ui: StateFlow<SettingsUi?> = combine(prefs.settings, ServiceLocator.jobDao.observeCount(), ServiceLocator.calcDao.observeCount()) { s, j, c -> SettingsUi(s, j, c) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setUnits(u: UnitSystem) = viewModelScope.launch { prefs.setUnitSystem(u) }
    fun setInch(v: Int) = viewModelScope.launch { prefs.setInchPrecision(v) }
    fun setMm(v: Double) = viewModelScope.launch { prefs.setMmPrecision(v) }
    fun setPaper(p: Paper) = viewModelScope.launch { prefs.setPaper(p) }
    fun setTheme(m: String) = viewModelScope.launch { prefs.setThemeMode(m) }
    fun setOptIn(on: Boolean) = viewModelScope.launch { prefs.setUsageOptIn(on) }
    fun setRootGap(mm: Double) = viewModelScope.launch { prefs.setRootGap(mm) }
    fun setSocketGap(mm: Double) = viewModelScope.launch { prefs.setSocketGap(mm) }

    fun backup(uri: android.net.Uri) {
        viewModelScope.launch {
            val prepared = withContext(Dispatchers.IO) {
                BackupCodec.encodeForWrite(BackupFile(BACKUP_FORMAT, BACKUP_SCHEMA, System.currentTimeMillis(), prefs.toBackup(), ServiceLocator.database.snapshot()))
            }
            when (prepared) {
                is BackupWrite.TooLarge -> messageChannel.send(UiMessage(BackupCodec.explainTooLargeToWrite(prepared.bytes)))
                is BackupWrite.Ready ->
                    if (Files.write(getApplication(), uri, prepared.bytes)) {
                        prefs.count("export")
                        messageChannel.send(UiMessage("Backed up to file.", prefs.recordSuccessAndShouldPrompt(System.currentTimeMillis())))
                    } else messageChannel.send(UiMessage("Could not write the file. Pick another folder."))
            }
        }
    }

    fun read(uri: android.net.Uri) {
        viewModelScope.launch {
            when (val bytes = Files.read(getApplication(), uri, BackupCodec.MAX_BYTES)) {
                is Files.Read.TooLarge -> messageChannel.send(UiMessage(BackupCodec.explain("too large")))
                is Files.Read.Failed -> messageChannel.send(UiMessage("Could not read that file. Pick another one."))
                is Files.Read.Bytes -> when (val result = withContext(Dispatchers.Default) { BackupCodec.decode(bytes.bytes) }) {
                    is BackupRead.Valid -> pending = result.file
                    is BackupRead.Invalid -> messageChannel.send(UiMessage(BackupCodec.explain(result.reason)))
                }
            }
        }
    }

    fun cancelRestore() { pending = null }

    fun restore(replace: Boolean) {
        val file = pending ?: return
        pending = null
        viewModelScope.launch {
            try {
                ServiceLocator.database.restore(file.jobs, replace)
                prefs.restore(file.prefs)
                val n = file.jobs.size
                messageChannel.send(UiMessage("Restored $n ${if (n == 1) "job" else "jobs"}."))
            } catch (e: Exception) {
                messageChannel.send(UiMessage("Could not restore that file. Nothing was changed."))
            }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onSources: () -> Unit, onMultipliers: () -> Unit, onLicences: () -> Unit, onPrivacy: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it.text) } }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) { haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.backup(uri) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.read(uri) }

    Scaffold(
        topBar = { KicksetTopBar("Settings", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.navigationBars,
    ) { pad ->
        val state = ui
        if (state == null) { SkeletonRows(6, Modifier.padding(pad)); return@Scaffold }
        val s = state.settings
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp).widthIn(max = 640.dp)) {
            SectionLabel("Units")
            Segmented(listOf("Millimetres", "Inches"), if (s.unitSystem == UnitSystem.INCH) 1 else 0, { vm.setUnits(if (it == 1) UnitSystem.INCH else UnitSystem.MM) })
            SectionLabel("Inch precision")
            ChoiceChips(listOf(16, 32), s.inchPrecision, { "1/$it in" }, { vm.setInch(it) })
            SectionLabel("Millimetre precision")
            ChoiceChips(listOf(1.0, 0.5), s.mmPrecision, { if (it == 1.0) "1 mm" else "0.5 mm" }, { vm.setMm(it) })
            SectionLabel("Default gaps")
            GapField("Root gap per butt weld", s.rootGapMm, s) { vm.setRootGap(it) }
            GapField("Socket engagement gap", s.socketGapMm, s) { vm.setSocketGap(it) }
            SectionLabel("Template paper")
            ChoiceChips(Paper.entries, s.paper, { it.label }, { vm.setPaper(it) })
            SectionLabel("Theme")
            ChoiceChips(listOf("SYSTEM", "LIGHT", "DARK"), s.themeMode, { when (it) { "LIGHT" -> "Light"; "DARK" -> "Dark"; else -> "Match system" } }, { vm.setTheme(it) })

            SectionLabel("Your data", Modifier.padding(top = 16.dp))
            Text("${state.jobCount} ${if (state.jobCount == 1) "job" else "jobs"} and ${state.calcCount} saved ${if (state.calcCount == 1) "cut" else "cuts"} on this phone. A backup is one JSON file you keep where you choose.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("Back up to file", { backupLauncher.launch(BackupCodec.fileName(Files.isoDate())) }, Modifier.weight(1f))
                SecondaryButton("Restore from file", { restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, Modifier.weight(1f))
            }

            SectionLabel("Local counts", Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(s.usageCountsOptIn, role = Role.Switch) { vm.setOptIn(it) }, verticalAlignment = Alignment.CenterVertically) {
                Text("Keep counts of solves, saves and exports on this phone", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = s.usageCountsOptIn, onCheckedChange = null)
            }
            if (s.usageCountsOptIn) Text("${s.countSolves} solves, ${s.countSaves} saves, ${s.countExports} exports. These never leave the phone.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionLabel("Reference", Modifier.padding(top = 16.dp))
            listOf("Table sources" to onSources, "Multipliers" to onMultipliers, "Privacy" to onPrivacy, "Licences" to onLicences).forEach { (label, go) ->
                ClickableRow(go, Modifier.padding(horizontal = 0.dp)) { Text(label, style = MaterialTheme.typography.bodyLarge) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Spacer(Modifier.height(16.dp))
            Text(DISCLAIMER, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val pending = vm.pending
    if (pending != null) {
        val n = pending.jobs.size
        AlertDialog(
            onDismissRequest = vm::cancelRestore,
            title = { Text("Restore $n ${if (n == 1) "job" else "jobs"}?") },
            text = { Text("Add them to the jobs on this phone, or replace every job here with the backup. Settings come from the backup either way.") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { vm.restore(false) }) { Text("Add to my jobs") }
                    TextButton(onClick = { vm.restore(true) }) { Text("Replace my jobs", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = vm::cancelRestore) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
private fun GapField(label: String, mm: Double, s: Settings, onSet: (Double) -> Unit) {
    fun render(v: Double) = if (s.unitSystem == UnitSystem.INCH) LengthFormatter.inchParts(v, 32).toString() else LengthFormatter.decimal(v, 1)
    var text by rememberSaveable(s.unitSystem) { mutableStateOf(render(mm)) }
    // The field owns its text while the fitter is typing. Re-seeding it from the saved value on every
    // accepted keystroke would rewrite a typed "2" as "2.0" and make "2.5" impossible to enter, so the
    // text is re-seeded only when the saved value changes from somewhere else, such as a restored backup.
    var written by rememberSaveable(s.unitSystem) { mutableStateOf(mm) }
    LaunchedEffect(mm) { if (mm != written) { text = render(mm); written = mm } }
    val parsed = LengthParser.parse(text, s.unitSystem)
    val error = when {
        parsed is ParsedLength.Invalid -> "Must be a length, like ${if (s.unitSystem == UnitSystem.INCH) "1/8" else "3"}"
        parsed is ParsedLength.Ok && parsed.mm > 25.0 -> "Must be 25 mm or less"
        else -> null
    }
    LengthField(label, text, { v ->
        text = v
        val p = LengthParser.parse(v, s.unitSystem)
        if (p is ParsedLength.Ok && p.mm <= 25.0) { written = p.mm; onSet(p.mm) }
    }, error, (parsed as? ParsedLength.Ok)?.let { "Reads as ${LengthFormatter.millimetresExact(it.mm)}, ${LengthFormatter.inches(it.mm, 32)}" }, Modifier.fillMaxWidth())
}

// ---------------------------------------------------------------------------------------------- units sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitsSheet(onPick: (UnitSystem) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { onPick(UnitSystem.MM) }, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Work in millimetres or inches?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Text("Every answer shows both. You can switch in Settings.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Text("Results are reference values. Check before you cut.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("Millimetres", { onPick(UnitSystem.MM) }, Modifier.weight(1f))
                PrimaryButton("Inches", { onPick(UnitSystem.INCH) }, Modifier.weight(1f))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- reference pages

class SourcesViewModel(app: Application) : AndroidViewModel(app) {
    val tables: StateFlow<TableSet?> = ServiceLocator.tables.tables.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun SourcesScreen(onBack: () -> Unit, vm: SourcesViewModel = viewModel()) {
    val t by vm.tables.collectAsStateWithLifecycle()
    ReferencePage("Table sources", onBack) {
        val tables = t
        if (tables == null) { SkeletonRows(4); return@ReferencePage }
        Para("Every takeout, pipe wall and flange bolt value in Kickset was typed from one manufacturer catalogue and checked against a second. A value only one catalogue gave, or that two catalogues disagreed on without a third to settle it, is left out rather than guessed.")
        Para("Left out for that reason: NPS 22 butt-weld fittings and NPS 3 1/2 XXS pipe. Sch 5 and the small-bore Sch 30 walls are not included in this version. Where two catalogues disagreed and a third settled it (Class 150 NPS 10 bolt circle, Class 300 NPS 16 bolt circle, NPS 2 XXS wall), the value shown is the one two sources agree on.")
        Para("Inch values are the published fractions. Millimetres are the inch value times 25.4, so they can differ by a fraction of a millimetre from a metric chart that rounds to whole millimetres.")
        StandardBlock(tables.buttWeld.standard, tables.buttWeld.note, tables.buttWeld.sources, tables.buttWeld.fittings.joinToString { "${it.name} NPS ${it.rows.first().nps} to ${it.rows.last().nps}" })
        StandardBlock(tables.socketWeld.standard, tables.socketWeld.note, tables.socketWeld.sources, tables.socketWeld.fittings.joinToString { "${it.name} NPS ${it.rows.first().nps} to ${it.rows.last().nps}" })
        StandardBlock(tables.pipe.standard, tables.pipe.note, tables.pipe.sources, "NPS ${tables.pipe.sizes.first().nps} to ${tables.pipe.sizes.last().nps}, schedules ${tables.pipe.schedules.joinToString(", ")}")
        StandardBlock(tables.flanges.standard, tables.flanges.note, tables.flanges.sources, "Class 150 and 300, NPS ${tables.flanges.classes.first().rows.first().nps} to ${tables.flanges.classes.first().rows.last().nps}")
        Para("Only dimensional facts are reproduced. No standard text is included. Kickset is not affiliated with ASME or any manufacturer named here. $DISCLAIMER")
    }
}

@Composable
private fun StandardBlock(title: String, note: String, sources: List<TableSource>, coverage: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp).semantics { heading() })
    Text(coverage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    sources.forEach { s ->
        Column(Modifier.padding(top = 10.dp)) {
            Text(s.publisher, style = MaterialTheme.typography.titleMedium)
            Text("${s.catalogue}${s.year?.let { ", $it" } ?: ""}. Pages: ${s.pages}.", style = MaterialTheme.typography.bodyMedium)
            SelectionContainer { Text(s.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 12.dp))
}

@Composable
fun MultipliersScreen(onBack: () -> Unit) {
    var custom by rememberSaveable { mutableStateOf("") }
    ReferencePage("Multipliers", onBack) {
        Para("Travel = offset x csc(angle). Run = offset x cot(angle). Parallel lines advance by spread x tan(angle / 2).")
        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            listOf("Angle", "Travel, csc", "Run, cot", "Advance, tan/2").forEach { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        val angles = PRESET_ANGLES + listOfNotNull(custom.toDoubleOrNull()?.takeIf { validAngle(it) && it !in PRESET_ANGLES })
        angles.forEach { a ->
            val m = Multipliers(a)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${formatAngle(a)}°", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(LengthFormatter.decimal(m.csc, 4), style = WorkingStyle.copy(color = MaterialTheme.colorScheme.primary), modifier = Modifier.weight(1f))
                Text(LengthFormatter.decimal(m.cot, 4), style = WorkingStyle, modifier = Modifier.weight(1f))
                Text(LengthFormatter.decimal(tan(rad(a) / 2), 4), style = WorkingStyle, modifier = Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        Spacer(Modifier.height(12.dp))
        LengthField("Another angle, degrees", custom, { custom = it.filter { c -> c.isDigit() || c == '.' } },
            if (custom.isNotBlank() && custom.toDoubleOrNull()?.let { validAngle(it) } != true) "Angle must be between 0 and 90 degrees" else null, null, Modifier.fillMaxWidth(), decimalOnly = true)
    }
}

@Composable
fun LicencesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // OFL 1.1 condition 2 asks that the copyright notice and the licence itself travel with the font. The
    // name tables inside the four bundled TTFs carry the notice; these two files carry the licence, so a
    // buyer can read it here. The app has no permission that could open a link instead.
    val ofl = remember(context) {
        listOf("licences/OFL-Archivo.txt", "licences/OFL-ArchivoNarrow.txt")
            .map { name -> context.assets.open(name).bufferedReader().use { it.readText().trim() } }
    }
    ReferencePage("Licences", onBack) {
        Para("Archivo and Archivo Narrow, copyright The Archivo Project Authors (Omnibus-Type). Bundled under the SIL Open Font License 1.1, which allows use, bundling and redistribution with software; the fonts may not be sold on their own. Both licences are printed in full below.")
        Para("AndroidX, Jetpack Compose, Material Components for Compose, Room, DataStore, Navigation, Kotlin, kotlinx.coroutines and kotlinx.serialization: Apache License 2.0.")
        Para("Google Play In-App Review library: Play Core Software Development Kit Terms of Service.")
        Para("Kickset, copyright SocialSure Private Limited.")
        SelectionContainer {
            Column {
                for (text in ofl) {
                    HorizontalDivider(Modifier.padding(top = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    ReferencePage("Privacy", onBack) {
        Para("Kickset has no account, no ads, no analytics and no network permission. Nothing you type or save is sent anywhere.")
        Para("Jobs, saved cuts and settings are stored only on this phone. Android's own backup is switched off for Kickset, so the system does not copy them to your Google account either. You can export them to a file you choose with Back up to file, and delete them by deleting a job or clearing the app's storage.")
        Para("The optional local counts stay on this phone and are off unless you turn them on.")
        Para("If you choose to rate the app when Google Play asks, that review goes through the Play Store app under Google's own terms. Kickset never sees it.")
    }
}

@Composable
private fun ReferencePage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(topBar = { KicksetTopBar(title, onBack = onBack) }, containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets.navigationBars) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp).widthIn(max = 640.dp)) { content() }
    }
}

@Composable
private fun Para(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 10.dp))
}
