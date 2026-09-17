package com.mohdshayan.kickset.ui.templates

import android.app.Activity
import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.mohdshayan.kickset.calc.Outcome
import com.mohdshayan.kickset.calc.TemplateSolved
import com.mohdshayan.kickset.calc.TemplateSolver
import com.mohdshayan.kickset.core.fittings.TableSet
import com.mohdshayan.kickset.core.jobs.TemplateInputs
import com.mohdshayan.kickset.core.template.DiameterBasis
import com.mohdshayan.kickset.data.prefs.Settings
import com.mohdshayan.kickset.data.session.SolvedTemplate
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
import com.mohdshayan.kickset.ui.components.SecondaryButton
import com.mohdshayan.kickset.ui.components.SectionLabel
import com.mohdshayan.kickset.ui.components.Segmented
import com.mohdshayan.kickset.ui.components.Sketch
import com.mohdshayan.kickset.ui.components.SketchPanel
import com.mohdshayan.kickset.ui.components.CalculatorSkeleton
import com.mohdshayan.kickset.ui.components.SmallReadout
import com.mohdshayan.kickset.ui.components.WorkingLines
import com.mohdshayan.kickset.ui.components.requestReview
import com.mohdshayan.kickset.ui.components.wrapCurveSketch
import com.mohdshayan.kickset.ui.cut.SizeChips
import com.mohdshayan.kickset.ui.nav.TopLevelActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TemplatesUi(val inputs: TemplateInputs, val settings: Settings, val tables: TableSet, val solved: TemplateSolved)

class TemplatesViewModel(app: Application, handle: SavedStateHandle) :
    CalcViewModelBase<TemplateInputs>(app, handle, "templates", TemplateInputs.serializer(), TemplateInputs()) {

    val ui: StateFlow<TemplatesUi?> = combine(inputsFlow, settings.filterNotNull(), ServiceLocator.tables.tables) { i, s, t ->
        TemplatesUi(i, s, t, TemplateSolver.solve(i, s.units, s.paper, t))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            ServiceLocator.session.reopenTemplate.filterNotNull().collect { replace(it); ServiceLocator.session.reopenTemplate.value = null }
        }
    }

    /** Hands the solved template to the sheet preview. */
    fun openSheets(): Boolean {
        val s = ui.value?.solved ?: return false
        val c = s.curve ?: return false
        ServiceLocator.session.template.value = SolvedTemplate(s.title, s.fileStem, c, s.hole)
        return true
    }
}

private val KINDS = listOf("MITER", "SADDLE", "LATERAL")

@Composable
fun TemplatesScreen(actions: TopLevelActions, onOpenSheets: () -> Unit, vm: TemplatesViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val activity = LocalContext.current as? Activity
    var saving by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.messages.collect { m -> if (snackbar.showSnackbar(m.text) == SnackbarResult.Dismissed && m.mayPromptReview && activity != null) requestReview(activity) }
    }

    Scaffold(
        topBar = { KicksetTopBar("Templates", actions = { OverflowMenu(actions.settings, actions.multipliers, actions.sources) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        val state = ui
        if (state == null) { CalculatorSkeleton(Modifier.padding(pad)); return@Scaffold }
        val i = state.inputs
        val solved = state.solved
        val sizes = state.tables.pipe.sizes.map { it.nps }
        Row(Modifier.padding(pad)) {
            CalculatorLayout(
                inputs = {
                    Segmented(listOf("Miter", "Saddle", "Lateral"), KINDS.indexOf(i.kind).coerceAtLeast(0), { idx -> vm.update { it.copy(kind = KINDS[idx]) } }, Modifier.padding(top = 4.dp, bottom = 8.dp))
                    if (i.kind != "MITER") {
                        SectionLabel("Header, NPS")
                        SizeChips(sizes, i.headerNps) { n -> vm.update { it.copy(headerNps = n) } }
                    }
                    SectionLabel(if (i.kind == "MITER") "Pipe, NPS" else "Branch, NPS")
                    SizeChips(sizes, i.branchNps) { n -> vm.update { it.copy(branchNps = n) } }
                    when (i.kind) {
                        "MITER" -> {
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(i.singleCut, role = Role.Switch) { on -> vm.update { it.copy(singleCut = on) } }, verticalAlignment = Alignment.CenterVertically) {
                                Text("Single cut at a set angle", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Switch(checked = i.singleCut, onCheckedChange = null)
                            }
                            if (i.singleCut) {
                                LengthField("Cut angle, degrees", i.singleCutAngle, { v -> vm.update { it.copy(singleCutAngle = v.filter { c -> c.isDigit() || c == '.' }) } }, solved.fields["cut"]?.error, null, Modifier.fillMaxWidth(), decimalOnly = true)
                            } else {
                                SectionLabel("Pieces")
                                ChoiceChips(listOf(2, 3, 4, 5), i.miterPieces, { "$it" }, { n -> vm.update { it.copy(miterPieces = n) } })
                                LengthField("Turn, degrees", i.miterTurn, { v -> vm.update { it.copy(miterTurn = v.filter { c -> c.isDigit() || c == '.' }) } }, solved.fields["turn"]?.error, null, Modifier.fillMaxWidth().padding(top = 4.dp), decimalOnly = true)
                            }
                        }
                        "LATERAL" -> LengthField("Lateral angle, 30 to 89 degrees", i.lateralAngle, { v -> vm.update { it.copy(lateralAngle = v.filter { c -> c.isDigit() || c == '.' }) } }, solved.fields["lateral"]?.error, null, Modifier.fillMaxWidth().padding(top = 8.dp), decimalOnly = true)
                    }
                    SectionLabel("Stations")
                    ChoiceChips(listOf(16, 32), i.stations, { "$it" }, { n -> vm.update { it.copy(stations = n) } })
                    SectionLabel("Branch diameter basis")
                    ChoiceChips(DiameterBasis.entries, DiameterBasis.entries.firstOrNull { it.name == i.basis }, { it.label }, { b -> vm.update { it.copy(basis = b.name) } })
                    if (i.basis != "OD") {
                        SectionLabel("Schedule, for the wall")
                        ChoiceChips(state.tables.pipe.schedules, i.schedule, { it }, { sch -> vm.update { it.copy(schedule = sch) } })
                    }
                },
                results = {
                    val o = solved.outcome
                    SketchPanel(Modifier.padding(top = 4.dp)) {
                        Sketch(if (o is Outcome.Solved) "Wrap curve preview. ${o.headline}" else "Wrap curve preview", (o as? Outcome.Solved)?.headline, height = 170.dp) { ink, p ->
                            solved.curve?.let { wrapCurveSketch(ink, it.ordinatesMm, p) }
                        }
                        Text("Cutback at each station, not to scale", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        when (o) {
                            is Outcome.Solved -> {
                                Text(solved.title, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
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
                        SectionLabel("Working", Modifier.padding(top = 12.dp))
                        WorkingLines(o.working)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PrimaryButton("Export PDF", { if (vm.openSheets()) onOpenSheets() }, Modifier.weight(1f))
                            SecondaryButton("Save to job", { saving = true }, Modifier.weight(1f))
                        }
                        Text("Prints true to scale only at 100 percent with no fit to page.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
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
    }
}
