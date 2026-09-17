package com.mohdshayan.kickset.ui.components

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.google.android.play.core.review.ReviewManagerFactory
import com.mohdshayan.kickset.data.db.JobSummary

/** At 600dp and wider, calculators show inputs and results side by side. */
@Composable
fun isWide(): Boolean = LocalConfiguration.current.screenWidthDp >= 600

@Composable
fun CalculatorLayout(inputs: @Composable ColumnScope.() -> Unit, results: @Composable ColumnScope.() -> Unit) {
    if (isWide()) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(0.4f).verticalScroll(rememberScrollState()).imePadding().padding(bottom = 24.dp), content = inputs)
            Column(Modifier.weight(0.6f).verticalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 24.dp), content = results)
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            inputs()
            results()
        }
    }
}

/** The overflow on every top-level screen. */
@Composable
fun OverflowMenu(onSettings: () -> Unit, onMultipliers: () -> Unit, onSources: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More options") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Settings") }, onClick = { open = false; onSettings() })
            DropdownMenuItem(text = { Text("Multipliers") }, onClick = { open = false; onMultipliers() })
            DropdownMenuItem(text = { Text("Table sources") }, onClick = { open = false; onSources() })
        }
    }
}

private const val NEW_JOB = -1L

/** Save to job: pick a recent job or start one, give the cut a label. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveToJobSheet(jobs: List<JobSummary>, onDismiss: () -> Unit, onSave: (jobId: Long?, newJobName: String?, label: String) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by rememberSaveable { mutableStateOf(jobs.firstOrNull()?.id ?: NEW_JOB) }
    var jobName by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var labelError by rememberSaveable { mutableStateOf<String?>(null) }
    var nameError by rememberSaveable { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp)) {
            Text("Save to job", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = label, onValueChange = { label = it.take(80); labelError = null },
                label = { Text("Label") }, singleLine = true, isError = labelError != null,
                supportingText = { Text(labelError ?: "Spool number or where this cut goes", color = if (labelError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.fillMaxWidth(),
            )
            SectionLabel("Job")
            jobs.take(6).forEach { j ->
                JobChoice(j.name, "${j.calcCount} saved", selected == j.id) { selected = j.id }
            }
            JobChoice("New job", null, selected == NEW_JOB) { selected = NEW_JOB }
            if (selected == NEW_JOB) {
                OutlinedTextField(
                    value = jobName, onValueChange = { jobName = it.take(80); nameError = null },
                    label = { Text("Job name") }, singleLine = true, isError = nameError != null,
                    supportingText = { nameError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Save to job", onClick = {
                var ok = true
                if (label.isBlank()) { labelError = "Give the cut a label, like Spool 14."; ok = false }
                if (selected == NEW_JOB && jobName.isBlank()) { nameError = "Give the job a name, like Unit 3 cooling water."; ok = false }
                if (ok) onSave(selected.takeIf { it != NEW_JOB }, jobName.takeIf { selected == NEW_JOB }, label.trim())
            }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun JobChoice(title: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** The one in-app review request, after the third success and never on the first day. */
fun requestReview(activity: Activity) {
    val manager = ReviewManagerFactory.create(activity)
    manager.requestReviewFlow().addOnCompleteListener { task ->
        if (task.isSuccessful) manager.launchReviewFlow(activity, task.result)
    }
}

@Composable
fun ClickableRow(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) { content() }
}
