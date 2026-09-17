package com.mohdshayan.kickset.ui.offsets

import android.app.Activity
import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.kickset.calc.OffsetSolver
import com.mohdshayan.kickset.calc.OffsetsSolved
import com.mohdshayan.kickset.calc.Outcome
import com.mohdshayan.kickset.core.jobs.OffsetInputs
import com.mohdshayan.kickset.core.offset.PRESET_ANGLES
import com.mohdshayan.kickset.core.offset.ParallelOffset
import com.mohdshayan.kickset.di.ServiceLocator
import com.mohdshayan.kickset.ui.components.CalcViewModelBase
import com.mohdshayan.kickset.ui.components.CalculatorLayout
import com.mohdshayan.kickset.ui.components.ChoiceChips
import com.mohdshayan.kickset.ui.components.FractionText
import com.mohdshayan.kickset.ui.components.KicksetTopBar
import com.mohdshayan.kickset.ui.components.LengthField
import com.mohdshayan.kickset.ui.components.OverflowMenu
import com.mohdshayan.kickset.ui.components.PrimaryButton
import com.mohdshayan.kickset.ui.components.Readout
import com.mohdshayan.kickset.ui.components.ResultMessage
import com.mohdshayan.kickset.ui.components.SaveToJobSheet
import com.mohdshayan.kickset.ui.components.SectionLabel
import com.mohdshayan.kickset.ui.components.Segmented
import com.mohdshayan.kickset.ui.components.CalculatorSkeleton
import com.mohdshayan.kickset.ui.components.Sketch
import com.mohdshayan.kickset.ui.components.SketchPanel
import com.mohdshayan.kickset.ui.components.SmallReadout
import com.mohdshayan.kickset.ui.components.WorkingLines
import com.mohdshayan.kickset.ui.components.formatAngle
import com.mohdshayan.kickset.ui.components.parallelSketch
import com.mohdshayan.kickset.ui.components.requestReview
import com.mohdshayan.kickset.ui.components.rollingOffsetSketch
import com.mohdshayan.kickset.ui.components.simpleOffsetSketch
import com.mohdshayan.kickset.ui.nav.TopLevelActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OffsetsUi(val inputs: OffsetInputs, val solved: OffsetsSolved)

@OptIn(FlowPreview::class)
class OffsetsViewModel(app: Application, handle: SavedStateHandle) :
    CalcViewModelBase<OffsetInputs>(app, handle, "offsets", OffsetInputs.serializer(), OffsetInputs()) {

    val ui: StateFlow<OffsetsUi?> = combine(inputsFlow, settings.filterNotNull()) { i, s -> OffsetsUi(i, OffsetSolver.solve(i, s.units)) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            ServiceLocator.session.reopenOffsets.filterNotNull().collect { replace(it); ServiceLocator.session.reopenOffsets.value = null }
        }
        viewModelScope.launch {
            ui.map { it?.solved?.travelMm }.filterNotNull().distinctUntilChanged().debounce(400).collect { prefs.setLastTravel(it) }
        }
        viewModelScope.launch {
            ui.map { (it?.solved?.outcome as? Outcome.Solved)?.headline }.filterNotNull().distinctUntilChanged().debounce(2_000).collect { prefs.count("solve") }
        }
    }
}

private val MODES = listOf("SIMPLE", "ROLLING", "PARALLEL")

@Composable
fun OffsetsScreen(actions: TopLevelActions, vm: OffsetsViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val activity = LocalContext.current as? Activity
    var saving by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.messages.collect { m ->
            if (snackbar.showSnackbar(m.text) == SnackbarResult.Dismissed && m.mayPromptReview && activity != null) requestReview(activity)
        }
    }

    Scaffold(
        topBar = { KicksetTopBar("Offsets", actions = { OverflowMenu(actions.settings, actions.multipliers, actions.sources) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        val state = ui
        val s = settings
        // Offsets is the start destination, so its first frame is the app's first frame: a skeleton
        // while DataStore answers, never a blank screen.
        if (state == null || s == null) { CalculatorSkeleton(Modifier.padding(pad)); return@Scaffold }
        val i = state.inputs
        val solved = state.solved
        Row(Modifier.padding(pad)) {
            CalculatorLayout(
                inputs = {
                    Segmented(listOf("Simple", "Rolling", "Parallel"), MODES.indexOf(i.mode).coerceAtLeast(0), { idx -> vm.update { it.copy(mode = MODES[idx]) } }, Modifier.padding(top = 4.dp, bottom = 12.dp))
                    when (i.mode) {
                        "ROLLING" -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                LengthField("Set", i.set, { v -> vm.update { it.copy(set = v) } }, solved.fields["set"]?.error, solved.fields["set"]?.echo, Modifier.weight(1f))
                                LengthField("Roll", i.roll, { v -> vm.update { it.copy(roll = v) } }, solved.fields["roll"]?.error, solved.fields["roll"]?.echo, Modifier.weight(1f))
                            }
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(i.rollingSolveAngle, role = Role.Switch) { on -> vm.update { it.copy(rollingSolveAngle = on) } },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Solve the angle from a fixed run", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Switch(checked = i.rollingSolveAngle, onCheckedChange = null)
                            }
                            if (i.rollingSolveAngle) LengthField("Run", i.run, { v -> vm.update { it.copy(run = v) } }, solved.fields["run"]?.error, solved.fields["run"]?.echo, Modifier.fillMaxWidth())
                        }
                        "PARALLEL" -> {
                            LengthField("Spread, centre to centre", i.spread, { v -> vm.update { it.copy(spread = v) } }, solved.fields["spread"]?.error, solved.fields["spread"]?.echo, Modifier.fillMaxWidth())
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Lines", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                IconButton(onClick = { vm.update { it.copy(lines = (it.lines - 1).coerceAtLeast(ParallelOffset.MIN_LINES)) } }, enabled = i.lines > ParallelOffset.MIN_LINES) {
                                    Icon(Icons.Outlined.Remove, contentDescription = "One line fewer")
                                }
                                Text("${i.lines}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.width(32.dp))
                                IconButton(onClick = { vm.update { it.copy(lines = (it.lines + 1).coerceAtMost(ParallelOffset.MAX_LINES)) } }, enabled = i.lines < ParallelOffset.MAX_LINES) {
                                    Icon(Icons.Outlined.Add, contentDescription = "One line more")
                                }
                            }
                        }
                        else -> LengthField("Set", i.set, { v -> vm.update { it.copy(set = v) } }, solved.fields["set"]?.error, solved.fields["set"]?.echo, Modifier.fillMaxWidth())
                    }
                    if (!(i.mode == "ROLLING" && i.rollingSolveAngle)) {
                        SectionLabel("Fitting angle")
                        val custom = i.customAngle.isNotBlank()
                        ChoiceChips(PRESET_ANGLES, if (custom) null else i.angle, { "${formatAngle(it)}°" }, { a -> vm.update { it.copy(angle = a, customAngle = "") } })
                        LengthField("Other angle, degrees", i.customAngle, { v -> vm.update { it.copy(customAngle = v.filter { c -> c.isDigit() || c == '.' }) } }, solved.angleError, null, Modifier.fillMaxWidth().padding(top = 4.dp), decimalOnly = true)
                    }
                },
                results = {
                    SketchPanel(Modifier.padding(top = 4.dp)) {
                        val o = solved.outcome
                        val sk = (o as? Outcome.Solved)?.sketch
                        val angle = OffsetSolver.angleOf(i) ?: 45.0
                        Sketch(
                            description = if (o is Outcome.Solved) "Sketch, not to scale. ${o.headline}" else "Offset sketch, not to scale",
                            key = (o as? Outcome.Solved)?.headline,
                        ) { ink, p ->
                            when (i.mode) {
                                "ROLLING" -> rollingOffsetSketch(ink, sk?.get("set") ?: "Set", sk?.get("roll") ?: "Roll", sk?.get("true") ?: "True offset", sk?.get("run") ?: "Run", sk?.get("travel") ?: "Travel", p)
                                "PARALLEL" -> parallelSketch(ink, i.lines, sk?.get("spread") ?: "Spread", sk?.get("advance") ?: "Advance", p)
                                else -> simpleOffsetSketch(ink, angle, sk?.get("set") ?: "Set", sk?.get("run") ?: "Run", sk?.get("travel") ?: "Travel", p)
                            }
                        }
                        Text("Sketch, not to scale", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        when (o) {
                            is Outcome.Solved -> {
                                Readout(o.readouts[0].label, o.readouts[0].primary, o.readouts[0].secondary)
                                if (o.readouts.size > 1) {
                                    Spacer(Modifier.height(12.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        o.readouts.drop(1).forEach { r -> SmallReadout(r.label, r.primary, r.secondary, Modifier.weight(1f)) }
                                    }
                                }
                            }
                            is Outcome.Prompt -> ResultMessage(o.text, false)
                            is Outcome.Problem -> ResultMessage(o.text, true)
                        }
                    }
                    val o = solved.outcome
                    if (o is Outcome.Solved) {
                        SectionLabel("Working", Modifier.padding(top = 12.dp))
                        WorkingLines(o.working)
                        if (solved.parallelRows.isNotEmpty()) {
                            SectionLabel("Each line's kick point", Modifier.padding(top = 12.dp))
                            solved.parallelRows.forEach { (line, adv) ->
                                Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(line, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    FractionText(adv, MaterialTheme.typography.bodyLarge)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton("Save to job", { saving = true }, Modifier.fillMaxWidth())
                    }
                },
            )
        }
        val o = solved.outcome
        if (saving && o is Outcome.Solved) {
            SaveToJobSheet(jobs, onDismiss = { saving = false }) { jobId, newName, label ->
                saving = false
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.save(jobId, newName, label, o.kind, o.headline, s.unitSystem)
            }
        }
    }
}
