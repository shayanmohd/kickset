package com.mohdshayan.kickset.ui.jobs

import android.app.Activity
import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.toRoute
import com.mohdshayan.kickset.calc.Replay
import com.mohdshayan.kickset.core.jobs.BackupCalc
import com.mohdshayan.kickset.core.jobs.CalcJson
import com.mohdshayan.kickset.core.jobs.CalcKind
import com.mohdshayan.kickset.core.jobs.CsvExporter
import com.mohdshayan.kickset.core.jobs.CutInputs
import com.mohdshayan.kickset.core.jobs.CutSheetEntry
import com.mohdshayan.kickset.core.jobs.CutSheetPdf
import com.mohdshayan.kickset.core.jobs.OffsetInputs
import com.mohdshayan.kickset.core.jobs.TemplateInputs
import com.mohdshayan.kickset.core.pdf.PdfWriter
import com.mohdshayan.kickset.data.db.Job
import com.mohdshayan.kickset.data.db.JobSummary
import com.mohdshayan.kickset.data.db.SavedCalc
import com.mohdshayan.kickset.data.export.Files
import com.mohdshayan.kickset.di.ServiceLocator
import com.mohdshayan.kickset.ui.components.ClickableRow
import com.mohdshayan.kickset.ui.components.EmptyState
import com.mohdshayan.kickset.ui.components.FractionText
import com.mohdshayan.kickset.ui.components.KicksetTopBar
import com.mohdshayan.kickset.ui.components.OverflowMenu
import com.mohdshayan.kickset.ui.components.PrimaryButton
import com.mohdshayan.kickset.ui.components.SecondaryButton
import com.mohdshayan.kickset.ui.components.SkeletonRows
import com.mohdshayan.kickset.ui.components.UiMessage
import com.mohdshayan.kickset.ui.components.requestReview
import com.mohdshayan.kickset.ui.nav.JobDetailRoute
import com.mohdshayan.kickset.ui.nav.TopLevelActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JobsViewModel(app: Application) : AndroidViewModel(app) {
    /** Null until Room answers, so the first frame is a skeleton rather than a false empty state. */
    val jobs: StateFlow<List<JobSummary>?> = ServiceLocator.jobDao.observeSummaries().map<List<JobSummary>, List<JobSummary>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun JobsScreen(actions: TopLevelActions, onOpenJob: (Long) -> Unit, onOpenOffsets: () -> Unit, vm: JobsViewModel = viewModel()) {
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { KicksetTopBar("Jobs", actions = { OverflowMenu(actions.settings, actions.multipliers, actions.sources) }) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        val list = jobs
        when {
            list == null -> SkeletonRows(6, Modifier.padding(pad))
            list.isEmpty() -> EmptyState("No jobs yet", "Save a result to start a job.", Modifier.fillMaxSize().padding(pad), "Open offsets", onOpenOffsets)
            else -> LazyColumn(Modifier.padding(pad).fillMaxSize()) {
                items(list, key = { it.id }) { j ->
                    ClickableRow({ onOpenJob(j.id) }) {
                        Column {
                            Text(j.name, style = MaterialTheme.typography.titleMedium)
                            Text("${j.calcCount} ${if (j.calcCount == 1) "cut" else "cuts"}, edited ${Files.readableDate(j.updatedAt)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

data class JobDetailUi(val job: Job?, val calcs: List<SavedCalc>, val loaded: Boolean)

class JobDetailViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val jobId = handle.toRoute<JobDetailRoute>().id
    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    val ui: StateFlow<JobDetailUi> = combine(ServiceLocator.jobDao.observe(jobId), ServiceLocator.calcDao.observeForJob(jobId)) { j, c -> JobDetailUi(j, c, true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JobDetailUi(null, emptyList(), false))

    fun reopen(c: SavedCalc): String? {
        val json = CalcJson.json
        val s = ServiceLocator.session
        return try {
            when (CalcKind.byName(c.kind)) {
                CalcKind.SIMPLE_OFFSET, CalcKind.ROLLING_OFFSET, CalcKind.PARALLEL_OFFSET -> { s.reopenOffsets.value = json.decodeFromString(OffsetInputs.serializer(), c.inputsJson); "offsets" }
                CalcKind.CUT_LENGTH, CalcKind.CUT_ELBOW -> { s.reopenCut.value = json.decodeFromString(CutInputs.serializer(), c.inputsJson); "cut" }
                CalcKind.MITER, CalcKind.SADDLE, CalcKind.LATERAL -> { s.reopenTemplate.value = json.decodeFromString(TemplateInputs.serializer(), c.inputsJson); "templates" }
                null -> null
            }
        } catch (e: Exception) { null }
    }

    fun deleteCalc(id: Long) { viewModelScope.launch { ServiceLocator.calcDao.delete(id); ServiceLocator.jobDao.touch(jobId, System.currentTimeMillis()) } }
    fun deleteJob() { viewModelScope.launch { ServiceLocator.jobDao.delete(jobId) } }
    fun rename(name: String) { viewModelScope.launch { ServiceLocator.jobDao.rename(jobId, name.trim(), System.currentTimeMillis()) } }

    fun exportCsv(uri: android.net.Uri) = export(uri, "Exported CSV.") {
        val u = ui.value
        CsvExporter.write(u.job?.name ?: "", u.calcs.map { BackupCalc(it.kind, it.label, it.inputsJson, it.headline, it.unitSystem, it.createdAt) }).toByteArray(Charsets.UTF_8)
    }

    fun exportCutSheet(uri: android.net.Uri) = export(uri, "Exported cut sheet.") {
        val u = ui.value
        val s = ServiceLocator.appPrefs.current()
        val t = ServiceLocator.tables.get()
        val entries = u.calcs.map { c ->
            val working = Replay.working(c.kind, c.inputsJson, c.unitSystem, c.settingsJson, s.units, s.rootGapMm, s.socketGapMm, s.paper, t)
            // Never print a headline over a silent gap: say so instead, so nobody reads the block as complete.
            CutSheetEntry(c.label, CalcKind.byName(c.kind)?.label ?: c.kind, c.headline,
                working.ifEmpty { listOf("The working for this entry could not be reproduced. Open it in the app and save it again.") })
        }
        PdfWriter.write(CutSheetPdf.pages(u.job?.name ?: "Job", Files.readableDate(System.currentTimeMillis()), entries, s.paper), u.job?.name ?: "Cut sheet")
    }

    private fun export(uri: android.net.Uri, success: String, build: suspend () -> ByteArray) {
        viewModelScope.launch {
            val bytes = try { withContext(Dispatchers.Default) { build() } } catch (e: Exception) { null }
            if (bytes != null && Files.write(getApplication(), uri, bytes)) {
                val prefs = ServiceLocator.appPrefs
                prefs.count("export")
                messageChannel.send(UiMessage(success, prefs.recordSuccessAndShouldPrompt(System.currentTimeMillis())))
            } else messageChannel.send(UiMessage("Could not write the file. Pick another folder."))
        }
    }
}

@Composable
fun JobDetailScreen(onBack: () -> Unit, onReopen: (String) -> Unit, vm: JobDetailViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val activity = LocalContext.current as? Activity
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(vm) {
        vm.messages.collect { m -> if (snackbar.showSnackbar(m.text) == SnackbarResult.Dismissed && m.mayPromptReview && activity != null) requestReview(activity) }
    }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> if (uri != null) { haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.exportCsv(uri) } }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> if (uri != null) { haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.exportCutSheet(uri) } }
    val job = ui.job

    Scaffold(
        topBar = {
            KicksetTopBar(job?.name ?: "Job", onBack = onBack, actions = {
                if (job != null) {
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Outlined.Edit, contentDescription = "Rename job") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete job") }
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.navigationBars,
    ) { pad ->
        when {
            !ui.loaded -> SkeletonRows(5, Modifier.padding(pad))
            job == null -> EmptyState("This job was deleted", "Pick another job from the list.", Modifier.fillMaxSize().padding(pad), "Back to jobs", onBack)
            ui.calcs.isEmpty() -> EmptyState("No cuts in this job", "Save a result to add a cut.", Modifier.fillMaxSize().padding(pad), "Back to jobs", onBack)
            else -> LazyColumn(Modifier.padding(pad).fillMaxSize()) {
                item {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val stem = Files.safeStem(job.name)
                        PrimaryButton("Export cut sheet", { pdf.launch("$stem-cut-sheet.pdf") }, Modifier.weight(1f))
                        SecondaryButton("Export CSV", { csv.launch("$stem.csv") }, Modifier.weight(1f))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
                items(ui.calcs, key = { it.id }) { c ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { vm.reopen(c)?.let(onReopen) }.padding(start = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.label, style = MaterialTheme.typography.titleMedium)
                            Text(CalcKind.byName(c.kind)?.label ?: c.kind, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(2.dp))
                            FractionText(c.headline, MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { vm.deleteCalc(c.id) }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete ${c.label}") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                }
                item { Text("Tap a cut to reopen it in its calculator.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            }
        }
    }
    if (renaming && job != null) {
        var name by rememberSaveable { mutableStateOf(job.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename job") },
            text = { OutlinedTextField(name, { name = it.take(80) }, singleLine = true, label = { Text("Job name") }) },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) { vm.rename(name); renaming = false } }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete && job != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${job.name}?") },
            text = { Text("This deletes the job and its ${ui.calcs.size} saved ${if (ui.calcs.size == 1) "cut" else "cuts"}. Back up first if you may need them.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteJob(); onBack() }) { Text("Delete job", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
