package com.mohdshayan.kickset.ui.templates

import android.app.Activity
import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.kickset.core.pdf.Paper
import com.mohdshayan.kickset.core.pdf.PdfWriter
import com.mohdshayan.kickset.core.template.DrawOp
import com.mohdshayan.kickset.core.template.Drawing
import com.mohdshayan.kickset.core.template.SheetLayout
import com.mohdshayan.kickset.core.template.TemplateDocument
import com.mohdshayan.kickset.core.template.TemplateDrawings
import com.mohdshayan.kickset.core.template.TemplatePages
import com.mohdshayan.kickset.data.export.Files
import com.mohdshayan.kickset.data.session.SolvedTemplate
import com.mohdshayan.kickset.di.ServiceLocator
import com.mohdshayan.kickset.ui.components.ChoiceChips
import com.mohdshayan.kickset.ui.components.EmptyState
import com.mohdshayan.kickset.ui.components.KicksetTopBar
import com.mohdshayan.kickset.ui.components.PrimaryButton
import com.mohdshayan.kickset.ui.components.UiMessage
import com.mohdshayan.kickset.ui.components.requestReview
import com.mohdshayan.kickset.ui.theme.RadiusSm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One thumbnail: which drawing it windows, or an ordinate table page. */
data class SheetThumb(val drawing: Drawing?, val col: Int, val row: Int, val cols: Int, val rows: Int, val caption: String)

data class SheetsUi(val template: SolvedTemplate?, val paper: Paper, val thumbs: List<SheetThumb>)

class SheetPreviewViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = ServiceLocator.appPrefs
    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    val ui: StateFlow<SheetsUi?> = combine(ServiceLocator.session.template, prefs.settings) { t, s ->
        if (t == null) SheetsUi(null, s.paper, emptyList()) else {
            val layout = SheetLayout(s.paper)
            val thumbs = mutableListOf<SheetThumb>()
            fun add(d: Drawing, noun: String) {
                val cols = layout.columnsFor(d.widthMm)
                val rows = layout.rowsFor(d.heightMm)
                for (r in 0 until rows) for (c in 0 until cols) thumbs += SheetThumb(d, c, r, cols, rows, "$noun, column ${c + 1}, row ${r + 1}")
            }
            add(TemplateDrawings.wrap(t.curve), "Wrap")
            t.hole?.let { add(TemplateDrawings.hole(it), "Header hole") }
            repeat(TemplatePages.ordinatePageCount(t.curve)) { thumbs += SheetThumb(null, 0, 0, 1, 1, "Ordinate table") }
            SheetsUi(t, s.paper, thumbs)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setPaper(p: Paper) { viewModelScope.launch { prefs.setPaper(p) } }

    fun export(uri: android.net.Uri) {
        viewModelScope.launch {
            val state = ui.value ?: return@launch
            val t = state.template ?: return@launch
            val bytes = withContext(Dispatchers.Default) {
                PdfWriter.write(TemplateDocument.build(t.curve, t.hole, SheetLayout(state.paper), "Kickset. ${t.title}"), t.title)
            }
            if (Files.write(getApplication(), uri, bytes)) {
                prefs.count("export")
                val prompt = prefs.recordSuccessAndShouldPrompt(System.currentTimeMillis())
                messageChannel.send(UiMessage("Exported ${state.thumbs.size} sheets. Print at actual size and measure the 100 mm bar.", prompt))
            } else {
                messageChannel.send(UiMessage("Could not write the file. Pick another folder."))
            }
        }
    }
}

@Composable
fun SheetPreviewScreen(onBack: () -> Unit, onOpenTemplates: () -> Unit, vm: SheetPreviewViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val activity = LocalContext.current as? Activity
    var showSkeleton by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(300); showSkeleton = true }
    LaunchedEffect(vm) {
        vm.messages.collect { m -> if (snackbar.showSnackbar(m.text) == SnackbarResult.Dismissed && m.mayPromptReview && activity != null) requestReview(activity) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) { haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.export(uri) }
    }

    Scaffold(
        topBar = { KicksetTopBar("Sheet preview", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.navigationBars,
    ) { pad ->
        val state = ui
        when {
            state == null -> if (showSkeleton) PageSkeletons(Modifier.padding(pad))
            state.template == null -> EmptyState("No template to print", "Solve a miter, saddle or lateral first, then export it here.", Modifier.fillMaxSize().padding(pad), "Open templates", onOpenTemplates)
            else -> {
                val t = state.template
                LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Text(t.title, style = MaterialTheme.typography.titleMedium)
                            Text("${state.thumbs.size} sheets, landscape, 10 mm margins and 10 mm overlap. Every sheet carries a 100 mm bar and a 4 in bar.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            ChoiceChips(Paper.entries, state.paper, { it.label }, vm::setPaper)
                            Spacer(Modifier.height(8.dp))
                            PrimaryButton("Export PDF", { launcher.launch("${t.fileStem}-${state.paper.name.lowercase()}.pdf") }, Modifier.fillMaxWidth())
                            Text("Print at 100 percent (actual size) with no fit to page, then measure both bars before you wrap and cut.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    itemsIndexed(state.thumbs) { idx, thumb ->
                        Column {
                            PageThumb(thumb, state.paper)
                            Text("Sheet ${idx + 1}. ${thumb.caption}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageSkeletons(modifier: Modifier) {
    val c = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.padding(16.dp).semantics { contentDescription = "Preparing sheets" }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) { Box(Modifier.fillMaxWidth(0.6f).aspectRatio(297f / 210f).background(c, RoundedCornerShape(RadiusSm))) }
    }
}

/** A landscape page drawn from the same drawing, layout and bars the PDF uses. */
@Composable
private fun PageThumb(thumb: SheetThumb, paper: Paper) {
    val layout = SheetLayout(paper)
    val paperColor = MaterialTheme.colorScheme.surface
    val ink = MaterialTheme.colorScheme.onSurface
    val guide = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier.fillMaxWidth().aspectRatio((paper.widthMm / paper.heightMm).toFloat())
            .background(paperColor, RoundedCornerShape(RadiusSm)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(RadiusSm))
            .semantics { contentDescription = thumb.caption },
    ) {
        val k = size.width / paper.widthMm.toFloat()
        fun px(mmX: Double) = (mmX * k).toFloat()
        fun py(mmY: Double) = size.height - (mmY * k).toFloat()
        val wl = layout.windowLeftMm
        val wb = layout.windowBottomMm
        val ww = layout.windowWidthMm
        val wh = layout.windowHeightMm
        drawRect(guide, Offset(px(wl), py(wb + wh)), Size(px(ww), px(wh)), style = Stroke(0.5f))
        val d = thumb.drawing
        if (d != null) {
            val ox = layout.originX(thumb.col)
            val oy = layout.originY(thumb.row)
            clipRect(px(wl), py(wb + wh), px(wl + ww), py(wb)) {
                for (op in d.ops) when (op) {
                    is DrawOp.Line -> drawLine(if (op.dashed) guide else ink, Offset(px(op.x1 - ox + wl), py(op.y1 - oy + wb)), Offset(px(op.x2 - ox + wl), py(op.y2 - oy + wb)), strokeWidth = 1f)
                    is DrawOp.Poly -> {
                        for (j in 1 until op.points.size) {
                            val a = op.points[j - 1]; val b = op.points[j]
                            drawLine(accent, Offset(px(a.first - ox + wl), py(a.second - oy + wb)), Offset(px(b.first - ox + wl), py(b.second - oy + wb)), strokeWidth = 2f)
                        }
                    }
                    is DrawOp.Label -> Unit
                }
            }
            val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))
            if (thumb.col < thumb.cols - 1) drawLine(guide, Offset(px(wl + ww - layout.overlapMm), py(wb)), Offset(px(wl + ww - layout.overlapMm), py(wb + wh)), 1f, pathEffect = dash)
            if (thumb.col > 0) drawLine(guide, Offset(px(wl + layout.overlapMm), py(wb)), Offset(px(wl + layout.overlapMm), py(wb + wh)), 1f, pathEffect = dash)
        } else {
            for (r in 0 until 14) drawLine(guide, Offset(px(wl), py(wb + wh - 12 - r * 10.0)), Offset(px(wl + 120), py(wb + wh - 12 - r * 10.0)), 1f)
        }
        val by = layout.marginMm + 6.0
        drawLine(ink, Offset(px(layout.marginMm), py(by)), Offset(px(layout.marginMm + 100.0), py(by)), 2f)
        drawLine(ink, Offset(px(layout.marginMm + 125.0), py(by)), Offset(px(layout.marginMm + 125.0 + 101.6), py(by)), 2f)
    }
}
