package com.mohdshayan.kickset.ui.cut

import android.app.Activity
import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.kickset.calc.CutSolved
import com.mohdshayan.kickset.calc.CutSolver
import com.mohdshayan.kickset.calc.Outcome
import com.mohdshayan.kickset.core.fittings.EndFitting
import com.mohdshayan.kickset.core.fittings.JoinType
import com.mohdshayan.kickset.core.fittings.TableSet
import com.mohdshayan.kickset.core.jobs.CutInputs
import com.mohdshayan.kickset.core.units.LengthFormatter
import com.mohdshayan.kickset.core.units.UnitSystem
import com.mohdshayan.kickset.data.prefs.Settings
import com.mohdshayan.kickset.di.ServiceLocator
import com.mohdshayan.kickset.ui.components.CalcViewModelBase
import com.mohdshayan.kickset.ui.components.CalculatorLayout
import com.mohdshayan.kickset.ui.components.ChoiceChips
import com.mohdshayan.kickset.ui.components.KicksetTopBar
import com.mohdshayan.kickset.ui.components.LengthField
import com.mohdshayan.kickset.ui.components.OverflowMenu
import com.mohdshayan.kickset.ui.components.PrimaryButton
import com.mohdshayan.kickset.ui.components.Readout
import com.mohdshayan.kickset.ui.components.ResultMessage
import com.mohdshayan.kickset.ui.components.SaveToJobSheet
import com.mohdshayan.kickset.ui.components.SectionLabel
import com.mohdshayan.kickset.ui.components.Segmented
import com.mohdshayan.kickset.ui.components.Sketch
import com.mohdshayan.kickset.ui.components.SketchPanel
import com.mohdshayan.kickset.ui.components.CalculatorSkeleton
import com.mohdshayan.kickset.ui.components.SmallReadout
import com.mohdshayan.kickset.ui.components.WorkingLines
import com.mohdshayan.kickset.ui.components.cutElbowSketch
import com.mohdshayan.kickset.ui.components.cutLengthSketch
import com.mohdshayan.kickset.ui.components.requestReview
import com.mohdshayan.kickset.ui.nav.TopLevelActions
import com.mohdshayan.kickset.ui.theme.RadiusSm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CutUi(val inputs: CutInputs, val settings: Settings, val tables: TableSet, val solved: CutSolved, val lastTravelMm: Double?)

class CutLengthViewModel(app: Application, handle: SavedStateHandle) :
    CalcViewModelBase<CutInputs>(app, handle, "cut", CutInputs.serializer(), CutInputs()) {

    val ui: StateFlow<CutUi?> = combine(inputsFlow, settings.filterNotNull(), ServiceLocator.tables.tables, prefs.lastTravelMm) { i, s, t, last ->
        CutUi(i, s, t, CutSolver.solve(i, s.units, s.rootGapMm, s.socketGapMm, t), last)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            ServiceLocator.session.reopenCut.filterNotNull().collect { replace(it); ServiceLocator.session.reopenCut.value = null }
        }
    }

    fun useLastTravel() {
        viewModelScope.launch {
            val mm = prefs.lastTravelMm.first() ?: return@launch
            val s = prefs.current()
            // The fitter's own denominator, so the field reads back the figure the chip offered him.
            val text = if (s.unitSystem == UnitSystem.INCH) LengthFormatter.inchParts(mm, s.inchPrecision).toString() else LengthFormatter.decimal(mm, 1)
            update { it.copy(centreToCentre = text) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CutLengthScreen(actions: TopLevelActions, vm: CutLengthViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val activity = LocalContext.current as? Activity
    var saving by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(vm) {
        vm.messages.collect { m -> if (snackbar.showSnackbar(m.text) == SnackbarResult.Dismissed && m.mayPromptReview && activity != null) requestReview(activity) }
    }

    Scaffold(
        topBar = { KicksetTopBar("Cut length", actions = { OverflowMenu(actions.settings, actions.multipliers, actions.sources) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        val state = ui
        if (state == null) { CalculatorSkeleton(Modifier.padding(pad)); return@Scaffold }
        val i = state.inputs
        val u = state.settings.units
        val solved = state.solved
        Row(Modifier.padding(pad)) {
            CalculatorLayout(
                inputs = {
                    Segmented(listOf("Cut length", "Cut elbow"), if (i.tab == "ELBOW") 1 else 0, { idx -> vm.update { it.copy(tab = if (idx == 1) "ELBOW" else "CUT") } }, Modifier.padding(top = 4.dp, bottom = 12.dp))
                    if (i.tab != "ELBOW") {
                        val a = EndFitting.byName(i.endA) ?: EndFitting.BW_90LR
                        val b = EndFitting.byName(i.endB) ?: EndFitting.BW_45LR
                        FittingRow("Fitting at end A", a) { picking = "A" }
                        FittingRow("Fitting at end B", b) { picking = "B" }
                        val join = a.join
                        SectionLabel(if (join == JoinType.BUTT_WELD) "Size, NPS (ASME B16.9)" else "Size, NPS (ASME B16.11 Class 3000)")
                        SizeChips(CutSolver.sizesFor(state.tables, join), if (join == JoinType.BUTT_WELD) i.nps else i.swNps) { n ->
                            vm.update { if (join == JoinType.BUTT_WELD) it.copy(nps = n) else it.copy(swNps = n) }
                        }
                        Spacer(Modifier.height(8.dp))
                        LengthField("Centre to centre", i.centreToCentre, { v -> vm.update { it.copy(centreToCentre = v) } }, solved.fields["ctoc"]?.error, solved.fields["ctoc"]?.echo, Modifier.fillMaxWidth())
                        if (state.lastTravelMm != null) {
                            AssistChip(onClick = vm::useLastTravel, label = { Text("Use last travel, ${u.primary(state.lastTravelMm)}") }, shape = RoundedCornerShape(RadiusSm),
                                colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.primary), modifier = Modifier.heightIn(min = 44.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                            if (join == JoinType.BUTT_WELD) {
                                LengthField("Root gap per weld", i.rootGap, { v -> vm.update { it.copy(rootGap = v) } }, solved.fields["rootGap"]?.error,
                                    solved.fields["rootGap"]?.echo ?: "Blank uses ${u.primary(state.settings.rootGapMm)}", Modifier.weight(1f))
                            } else {
                                LengthField("Engagement gap per end", i.socketGap, { v -> vm.update { it.copy(socketGap = v) } }, solved.fields["socketGap"]?.error,
                                    solved.fields["socketGap"]?.echo ?: "Blank uses ${LengthFormatter.millimetresExact(state.settings.socketGapMm)}", Modifier.weight(1f))
                            }
                        }
                    } else {
                        SectionLabel("Elbow")
                        ChoiceChips(listOf(EndFitting.BW_90LR, EndFitting.BW_90SR), EndFitting.byName(i.elbowRadius), { it.label }, { f -> vm.update { it.copy(elbowRadius = f.name) } })
                        SectionLabel("Size, NPS")
                        SizeChips(state.tables.series(i.elbowRadius)?.rows?.map { it.nps } ?: emptyList(), i.elbowNps) { n -> vm.update { it.copy(elbowNps = n) } }
                        Spacer(Modifier.height(8.dp))
                        LengthField("Cut to angle, degrees", i.elbowAngle, { v -> vm.update { it.copy(elbowAngle = v.filter { c -> c.isDigit() || c == '.' }) } },
                            solved.fields["angle"]?.error, null, Modifier.fillMaxWidth(), decimalOnly = true)
                    }
                },
                results = {
                    val o = solved.outcome
                    SketchPanel(Modifier.padding(top = 4.dp)) {
                        val sk = (o as? Outcome.Solved)?.sketch
                        Sketch(if (o is Outcome.Solved) "Sketch, not to scale. ${o.headline}" else "Cut length sketch, not to scale", (o as? Outcome.Solved)?.headline) { ink, p ->
                            if (i.tab == "ELBOW") cutElbowSketch(ink, i.elbowAngle.toDoubleOrNull()?.takeIf { it > 0 && it < 90 } ?: 30.0, sk?.get("takeout") ?: "Takeout", p)
                            else cutLengthSketch(ink, sk?.get("ctoc") ?: "Centre to centre", sk?.get("a") ?: "Takeout A", sk?.get("b") ?: "Takeout B", sk?.get("cut") ?: "Cut", p)
                        }
                        Text("Sketch, not to scale", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        when (o) {
                            is Outcome.Solved -> {
                                Readout(o.readouts[0].label, o.readouts[0].primary, o.readouts[0].secondary)
                                Spacer(Modifier.height(12.dp))
                                o.readouts.drop(1).chunked(2).forEach { pair ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                                        pair.forEach { r -> SmallReadout(r.label, r.primary, r.secondary, Modifier.weight(1f)) }
                                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                            is Outcome.Prompt -> ResultMessage(o.text, false)
                            is Outcome.Problem -> ResultMessage(o.text, true)
                        }
                    }
                    if (o is Outcome.Solved) {
                        SectionLabel("Every subtraction", Modifier.padding(top = 12.dp))
                        WorkingLines(o.working)
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
                vm.save(jobId, newName, label, o.kind, o.headline, state.settings.unitSystem)
            }
        }
        val side = picking
        if (side != null) {
            val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(onDismissRequest = { picking = null }, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
                Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 12.dp)) {
                    Text("Fitting at end $side", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    PickerGroup("Butt weld, ASME B16.9", CutSolver.BW_ENDS) { f -> picking = null; vm.update { if (side == "A") it.copy(endA = f.name) else it.copy(endB = f.name) } }
                    PickerGroup("Socket weld, ASME B16.11 Class 3000", CutSolver.SW_ENDS) { f -> picking = null; vm.update { if (side == "A") it.copy(endA = f.name) else it.copy(endB = f.name) } }
                }
            }
        }
    }
}

private fun detail(f: EndFitting) = when (f) {
    EndFitting.BW_REDUCER -> "Large end size, measured to its far end"
    EndFitting.SW_COUPLING -> "Measured to the coupling centre"
    EndFitting.BW_TEE, EndFitting.SW_TEE -> "Centre to end of the run"
    else -> "Centre to end"
}

@Composable
private fun PickerGroup(title: String, items: List<EndFitting>, onPick: (EndFitting) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    items.forEach { f ->
        Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onPick(f) }.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(f.label, style = MaterialTheme.typography.bodyLarge)
            Text(detail(f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun FittingRow(title: String, f: EndFitting, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(f.label, style = MaterialTheme.typography.titleMedium)
        }
        Icon(Icons.Outlined.ExpandMore, contentDescription = "Change $title")
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun SizeChips(sizes: List<String>, selected: String, onSelect: (String) -> Unit) {
    val state = rememberLazyListState()
    // NPS 4 sits well past the right edge of a phone-wide row, so the row opens on the chosen size
    // instead of on NPS 1/2 with nothing visibly picked. Tapping a chip does not move the row.
    LaunchedEffect(sizes) {
        val i = sizes.indexOf(selected)
        if (i > 0) state.scrollToItem(i, -160)
    }
    LazyRow(state = state, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(sizes, key = { it }) { n ->
            FilterChip(
                selected = n == selected, onClick = { onSelect(n) },
                label = { Text(n, style = MaterialTheme.typography.labelMedium) },
                shape = RoundedCornerShape(RadiusSm),
                colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface, selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer),
                border = FilterChipDefaults.filterChipBorder(true, n == selected, borderColor = MaterialTheme.colorScheme.outline, selectedBorderColor = MaterialTheme.colorScheme.primary, selectedBorderWidth = 1.5.dp),
                modifier = Modifier.heightIn(min = 44.dp),
            )
        }
    }
}
