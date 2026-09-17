package com.mohdshayan.kickset.ui.pipe

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.kickset.core.fittings.PipeSize
import com.mohdshayan.kickset.core.fittings.TableSet
import com.mohdshayan.kickset.core.fittings.npsValue
import com.mohdshayan.kickset.core.pipe.PipeSchedule
import com.mohdshayan.kickset.core.units.LengthFormatter
import com.mohdshayan.kickset.core.units.MM_PER_INCH
import com.mohdshayan.kickset.data.prefs.Settings
import com.mohdshayan.kickset.di.ServiceLocator
import com.mohdshayan.kickset.ui.components.ChoiceChips
import com.mohdshayan.kickset.ui.components.ClickableRow
import com.mohdshayan.kickset.ui.components.EmptyState
import com.mohdshayan.kickset.ui.components.FractionText
import com.mohdshayan.kickset.ui.components.KicksetTopBar
import com.mohdshayan.kickset.ui.components.OverflowMenu
import com.mohdshayan.kickset.ui.components.ResultMessage
import com.mohdshayan.kickset.ui.components.Segmented
import com.mohdshayan.kickset.ui.components.SkeletonRows
import com.mohdshayan.kickset.ui.components.SketchPanel
import com.mohdshayan.kickset.ui.components.isWide
import com.mohdshayan.kickset.ui.nav.TopLevelActions
import com.mohdshayan.kickset.ui.theme.RadiusSm
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class PipeUi(val tables: TableSet, val settings: Settings)

class PipeDataViewModel(app: Application) : AndroidViewModel(app) {
    val ui: StateFlow<PipeUi?> = combine(ServiceLocator.tables.tables, ServiceLocator.appPrefs.settings) { t, s -> PipeUi(t, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private fun matches(nps: String, q: String): Boolean {
    val query = q.trim().removePrefix("NPS").removePrefix("nps").trim()
    if (query.isEmpty()) return true
    return nps == query || nps.startsWith("$query ") || nps.replace(" ", "-") == query || nps.startsWith(query)
}

private fun nearest(sizes: List<PipeSize>, q: String): Pair<String, String>? {
    val v = q.trim().toDoubleOrNull() ?: return null
    val below = sizes.lastOrNull { npsValue(it.nps) < v }?.nps
    val above = sizes.firstOrNull { npsValue(it.nps) > v }?.nps
    return if (below != null && above != null) below to above else null
}

@Composable
fun PipeDataScreen(actions: TopLevelActions, vm: PipeDataViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var schedule by rememberSaveable { mutableStateOf("40") }
    var flangeClass by rememberSaveable { mutableStateOf(150) }
    val wide = isWide()
    BackHandler(enabled = selected != null && !wide) { selected = null }

    Scaffold(
        topBar = {
            KicksetTopBar(if (selected != null && !wide) "NPS $selected" else "Pipe data",
                onBack = if (selected != null && !wide) ({ selected = null }) else null,
                actions = { OverflowMenu(actions.settings, actions.multipliers, actions.sources) })
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            val state = ui
            if (selected == null || wide) Segmented(listOf("Pipe", "Flange bolts"), tab, { tab = it }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            if (state == null) { SkeletonRows(8); return@Column }
            val u = state.settings.units
            if (tab == 1 && (selected == null || wide)) { FlangeTable(state, flangeClass) { flangeClass = it }; return@Column }
            val sizes = state.tables.pipe.sizes
            val list: @Composable (Modifier) -> Unit = { m ->
                Column(m) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it.take(8) },
                        placeholder = { Text("Search a size, like 6 or 1 1/2") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true, shape = RoundedCornerShape(RadiusSm),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    val shown = sizes.filter { matches(it.nps, query) }
                    if (shown.isEmpty()) {
                        val near = nearest(sizes, query)
                        EmptyState("No size matches ${query.trim()}", if (near != null) "Try ${near.first} or ${near.second}." else "Sizes run from NPS 1/2 to 24.", Modifier.fillMaxWidth().padding(top = 32.dp), "Show all sizes", { query = "" })
                    } else LazyColumn {
                        items(shown, key = { it.nps }) { size ->
                            ClickableRow({ selected = size.nps }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("NPS ${size.nps}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    Text(if (u.system.name == "INCH") "OD ${LengthFormatter.decimal(size.odIn, 3)} in" else "OD ${LengthFormatter.millimetresExact(size.odIn * MM_PER_INCH)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    list(Modifier.weight(0.4f))
                    val sel = sizes.firstOrNull { it.nps == selected }
                    if (sel != null) PipeDetail(sel, schedule, { schedule = it }, state, Modifier.weight(0.6f))
                    else EmptyState("Pick a size", "OD, wall, bore, weight and volume for every schedule.", Modifier.weight(0.6f).fillMaxSize())
                }
            } else {
                val sel = sizes.firstOrNull { it.nps == selected }
                if (sel == null) list(Modifier.fillMaxSize()) else PipeDetail(sel, schedule, { schedule = it }, state, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun PipeDetail(size: PipeSize, schedule: String, onSchedule: (String) -> Unit, state: PipeUi, modifier: Modifier) {
    val u = state.settings.units
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("NPS ${size.nps}, schedule", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        ChoiceChips(state.tables.pipe.schedules, schedule, { "Sch $it".replace("Sch STD", "STD").replace("Sch XS", "XS").replace("Sch XXS", "XXS") }, onSchedule)
        Spacer(Modifier.height(8.dp))
        val d = PipeSchedule.detail(size, schedule)
        SketchPanel {
            if (d == null) {
                ResultMessage("NPS ${size.nps} has no Sch $schedule. Pick another schedule.", true)
            } else {
                DataRow("Outside diameter", u.primary(d.odMm), u.secondary(d.odMm), exact = "${LengthFormatter.decimal(d.odMm / MM_PER_INCH, 3)} in")
                DataRow("Wall", u.primary(d.wallMm), u.secondary(d.wallMm), exact = "${LengthFormatter.decimal(d.wallMm / MM_PER_INCH, 3)} in, ${LengthFormatter.decimal(d.wallMm, 2)} mm")
                DataRow("Inside diameter", u.primary(d.idMm), u.secondary(d.idMm), exact = "${LengthFormatter.decimal(d.idMm / MM_PER_INCH, 3)} in, ${LengthFormatter.decimal(d.idMm, 1)} mm")
                DataRow("Weight, plain end", "${LengthFormatter.decimal(d.weightKgPerM, 2)} kg/m", "${LengthFormatter.decimal(d.weightLbPerFt, 2)} lb/ft")
                DataRow("Weight full of water", "${LengthFormatter.decimal(d.waterFilledKgPerM, 2)} kg/m", "${LengthFormatter.decimal(d.waterFilledLbPerFt, 2)} lb/ft")
                DataRow("Volume", "${LengthFormatter.decimal(d.volumeLitresPerM, 2)} L/m", "${LengthFormatter.decimal(d.volumeUsGalPerFt, 3)} US gal/ft", last = true)
            }
        }
        Text("Weight is carbon steel from the ASME B36.10M formula 0.0246615 x (OD - wall) x wall. Walls cross-checked against two published catalogues; see Table sources.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun DataRow(label: String, primary: String, secondary: String, exact: String? = null, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (exact != null) Text(exact, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            FractionText(primary, MaterialTheme.typography.titleMedium)
            FractionText(secondary, MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (!last) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun FlangeTable(state: PipeUi, cls: Int, onClass: (Int) -> Unit) {
    val rows = state.tables.flanges.classes.firstOrNull { it.`class` == cls }?.rows ?: emptyList()
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                ChoiceChips(listOf(150, 300), cls, { "Class $it" }, onClass)
                Text("ASME B16.5 raised face. Stud length includes the 0.06 in raised faces. No torque values.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Header("NPS", 0.8f); Header("Studs", 1.3f); Header("Bolt circle", 1.3f); Header("Stud length", 1.3f)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
        items(rows, key = { it.nps }) { r ->
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(r.nps, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(0.8f))
                Column(Modifier.weight(1.3f)) {
                    Text("${r.bolts} studs", style = MaterialTheme.typography.bodyMedium)
                    FractionText("${r.studIn} in", MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1.3f)) {
                    FractionText(LengthFormatter.inches(r.boltCircleMm, 16), MaterialTheme.typography.bodyMedium)
                    Text(LengthFormatter.millimetresExact(r.boltCircleIn * MM_PER_INCH), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1.3f)) {
                    FractionText(LengthFormatter.inches(r.studLengthIn * MM_PER_INCH, 16), MaterialTheme.typography.bodyMedium)
                    Text(LengthFormatter.millimetres(r.studLengthIn * MM_PER_INCH, 1.0), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Header(text: String, w: Float) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(w))
}
