package com.mohdshayan.kickset.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mohdshayan.kickset.core.units.LengthFormatter
import com.mohdshayan.kickset.ui.theme.DimensionStyle
import com.mohdshayan.kickset.ui.theme.LocalReducedMotion
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/** Colours and text used inside a sketch, all from theme tokens. */
class SketchInk(val pipe: Color, val dim: Color, val guide: Color, val measurer: TextMeasurer, val label: TextStyle, val small: TextStyle)

/**
 * A proportional (not to scale) sketch. `key` changes when the result changes, which redraws the
 * dimension lines over 200 ms, or instantly under reduced motion.
 */
@Composable
fun Sketch(description: String, key: Any?, modifier: Modifier = Modifier, height: Dp = 190.dp, draw: DrawScope.(SketchInk, Float) -> Unit) {
    val measurer = rememberTextMeasurer()
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(1f) }
    LaunchedEffect(key) {
        if (reduced) progress.snapTo(1f) else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(200))
        }
    }
    val ink = SketchInk(
        pipe = MaterialTheme.colorScheme.onSurface,
        dim = MaterialTheme.colorScheme.primary,
        guide = MaterialTheme.colorScheme.onSurfaceVariant,
        measurer = measurer,
        label = DimensionStyle.copy(color = MaterialTheme.colorScheme.primary),
        small = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
    )
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) { draw(ink, progress.value) }
}

fun DrawScope.pipeLine(ink: SketchInk, a: Offset, b: Offset, w: Float = 7.dp.toPx()) =
    drawLine(ink.pipe, a, b, strokeWidth = w, cap = StrokeCap.Butt)

fun DrawScope.guideLine(ink: SketchInk, a: Offset, b: Offset) =
    drawLine(ink.guide, a, b, strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))

/** Text centred on a point, kept inside the canvas. */
fun DrawScope.label(ink: SketchInk, text: String, at: Offset, style: TextStyle = ink.label) {
    val layout = ink.measurer.measure(text, style)
    val x = (at.x - layout.size.width / 2f).coerceIn(0f, max(0f, size.width - layout.size.width))
    val y = (at.y - layout.size.height / 2f).coerceIn(0f, max(0f, size.height - layout.size.height))
    drawText(layout, topLeft = Offset(x, y))
}

/** Text with its left edge at `at` and centred on it vertically, kept inside the canvas. */
fun DrawScope.labelLeft(ink: SketchInk, text: String, at: Offset, style: TextStyle = ink.label) {
    val layout = ink.measurer.measure(text, style)
    val x = at.x.coerceIn(0f, max(0f, size.width - layout.size.width))
    val y = (at.y - layout.size.height / 2f).coerceIn(0f, max(0f, size.height - layout.size.height))
    drawText(layout, topLeft = Offset(x, y))
}

/** A dimension line with end ticks, drawn to `progress` of its length, labelled at its middle. */
fun DrawScope.dimension(ink: SketchInk, a: Offset, b: Offset, text: String?, progress: Float, labelOffset: Offset = Offset.Zero, color: Color = ink.dim) {
    val end = Offset(a.x + (b.x - a.x) * progress, a.y + (b.y - a.y) * progress)
    val stroke = 1.5.dp.toPx()
    drawLine(color, a, end, strokeWidth = stroke)
    val ang = atan2(b.y - a.y, b.x - a.x)
    val t = 6.dp.toPx()
    val nx = -sin(ang) * t
    val ny = cos(ang) * t
    drawLine(color, Offset(a.x - nx, a.y - ny), Offset(a.x + nx, a.y + ny), strokeWidth = stroke)
    if (progress >= 0.999f) drawLine(color, Offset(b.x - nx, b.y - ny), Offset(b.x + nx, b.y + ny), strokeWidth = stroke)
    if (text != null) label(ink, text, Offset((a.x + b.x) / 2 + labelOffset.x, (a.y + b.y) / 2 + labelOffset.y))
}

fun formatAngle(d: Double): String = LengthFormatter.decimal(d, 2).trimEnd('0').trimEnd('.')

/** Simple offset: run along the bottom, set up the side, travel along the kick. */
fun DrawScope.simpleOffsetSketch(ink: SketchInk, angleDeg: Double, set: String, run: String, travel: String, progress: Float) {
    val pad = 24.dp.toPx()
    val cot = (1.0 / tan(Math.toRadians(angleDeg))).toFloat()
    val avail = size.width - 2 * pad - 110.dp.toPx()
    var setH = size.height * 0.5f
    var runW = setH * cot
    if (runW > avail) { runW = avail; setH = runW / max(cot, 0.01f) }
    runW = max(runW, 20.dp.toPx())
    val baseY = size.height - 44.dp.toPx()
    val a = Offset(pad + 36.dp.toPx(), baseY)
    val b = Offset(a.x + runW, baseY - setH)
    pipeLine(ink, Offset(0f, baseY), a)
    pipeLine(ink, a, b)
    pipeLine(ink, b, Offset(size.width, b.y))
    guideLine(ink, Offset(b.x, baseY), b)
    dimension(ink, Offset(a.x, baseY + 16.dp.toPx()), Offset(b.x, baseY + 16.dp.toPx()), run, progress, Offset(0f, 14.dp.toPx()))
    dimension(ink, Offset(b.x + 24.dp.toPx(), baseY), Offset(b.x + 24.dp.toPx(), b.y), set, progress, Offset(40.dp.toPx(), 0f))
    val len = hypot(b.x - a.x, b.y - a.y)
    val nx = (b.y - a.y) / len * 20.dp.toPx()
    val ny = -(b.x - a.x) / len * 20.dp.toPx()
    dimension(ink, Offset(a.x + nx, a.y + ny), Offset(b.x + nx, b.y + ny), travel, progress, Offset(nx * 1.7f, ny * 1.7f))
    label(ink, "${formatAngle(angleDeg)}°", Offset(a.x + 36.dp.toPx(), baseY - 9.dp.toPx()), ink.small)
}

/** Rolling offset: a box whose end face holds set and roll, the pipe running corner to corner. */
fun DrawScope.rollingOffsetSketch(ink: SketchInk, set: String, roll: String, trueOffset: String, run: String, travel: String, progress: Float) {
    val boxW = min(size.width * 0.3f, 130.dp.toPx())
    val boxH = size.height * 0.4f
    val depth = Offset(min(size.width * 0.34f, 170.dp.toPx()), -size.height * 0.2f)
    val f0 = Offset(56.dp.toPx(), size.height - 36.dp.toPx())
    val f1 = Offset(f0.x + boxW, f0.y)
    val f2 = Offset(f1.x, f0.y - boxH)
    val back = f2 + depth
    guideLine(ink, f0, f1); guideLine(ink, f1, f2); guideLine(ink, f0, f2)
    guideLine(ink, f1, f1 + depth); guideLine(ink, f2, back); guideLine(ink, f1 + depth, back)
    pipeLine(ink, f0 + Offset(-28.dp.toPx(), 14.dp.toPx()), f0, 6.dp.toPx())
    pipeLine(ink, f0, back, 6.dp.toPx())
    pipeLine(ink, back, back + Offset(28.dp.toPx(), -14.dp.toPx()), 6.dp.toPx())
    dimension(ink, Offset(f0.x, f0.y + 12.dp.toPx()), Offset(f1.x, f1.y + 12.dp.toPx()), roll, progress, Offset(0f, 12.dp.toPx()))
    dimension(ink, Offset(f0.x - 16.dp.toPx(), f0.y), Offset(f0.x - 16.dp.toPx(), f2.y), set, progress, Offset(-4.dp.toPx(), -boxH / 2 - 12.dp.toPx()))
    // The true offset is the end face's hypotenuse, so it gets its own dimension line stepped off the
    // face to the upper left. That is empty ground: the pipe leaves f0 towards the far corner well
    // below it, and the set label sits outside the box. Nothing is struck through.
    val hl = hypot(f2.x - f0.x, f2.y - f0.y)
    val hnx = (f2.y - f0.y) / hl * 14.dp.toPx()
    val hny = -(f2.x - f0.x) / hl * 14.dp.toPx()
    dimension(ink, Offset(f0.x + hnx, f0.y + hny), Offset(f2.x + hnx, f2.y + hny), trueOffset, progress, Offset(hnx * 2.2f, hny * 2.2f))
    dimension(ink, f1 + Offset(12.dp.toPx(), 6.dp.toPx()), f1 + depth + Offset(12.dp.toPx(), 6.dp.toPx()), run, progress, Offset(26.dp.toPx(), 14.dp.toPx()))
    label(ink, travel, Offset(back.x - 40.dp.toPx(), back.y - 22.dp.toPx()))
}

/**
 * Parallel offsets: every line the fitter asked for, kicking together, each kick point stepped along
 * the run. Both labels live in the clear band under the pipes, each sitting beside its own dimension
 * mark with a leader down to it, so neither floats free of what it measures.
 */
fun DrawScope.parallelSketch(ink: SketchInk, lines: Int, spread: String, advance: String, progress: Float) {
    val n = lines.coerceIn(2, 8)
    val bottom = size.height - 34.dp.toPx()
    val gap = min(26.dp.toPx(), (bottom - 20.dp.toPx()) / (n + 0.8f))
    val rise = gap * 1.8f
    val adv = gap * 0.414f
    val startX = 92.dp.toPx()
    val stroke = min(5.dp.toPx(), gap * 0.30f)
    for (i in 0 until n) {
        val y = bottom - i * gap
        val kx = startX + i * adv
        pipeLine(ink, Offset(0f, y), Offset(kx, y), stroke)
        pipeLine(ink, Offset(kx, y), Offset(kx + rise, y - rise), stroke)
        pipeLine(ink, Offset(kx + rise, y - rise), Offset(size.width, y - rise), stroke)
    }
    val labelY = bottom + 18.dp.toPx()
    val markY = labelY - 9.dp.toPx()
    val sx = 30.dp.toPx()
    dimension(ink, Offset(sx, bottom), Offset(sx, bottom - gap), null, progress)
    guideLine(ink, Offset(sx, bottom), Offset(sx, markY))
    labelLeft(ink, spread, Offset(sx + 8.dp.toPx(), labelY), ink.small)
    guideLine(ink, Offset(startX, bottom), Offset(startX, markY))
    guideLine(ink, Offset(startX + adv, bottom - gap), Offset(startX + adv, markY))
    dimension(ink, Offset(startX, markY), Offset(startX + adv, markY), null, progress)
    labelLeft(ink, advance, Offset(startX + adv + 10.dp.toPx(), labelY), ink.small)
}

/** Cut length: centre to centre across two elbows, takeouts, and the cut piece between them. */
fun DrawScope.cutLengthSketch(ink: SketchInk, ctoc: String, takeA: String, takeB: String, cut: String, progress: Float) {
    val y = size.height * 0.62f
    val cL = Offset(22.dp.toPx(), y)
    val cR = Offset(size.width - 22.dp.toPx(), y)
    val take = min((cR.x - cL.x) * 0.2f, size.height * 0.3f)
    val gap = 5.dp.toPx()
    val sz = Size(take * 2, take * 2)
    drawArc(ink.pipe, 90f, 90f, false, topLeft = Offset(cL.x, y - 2 * take), size = sz, style = Stroke(7.dp.toPx()))
    drawArc(ink.pipe, 0f, 90f, false, topLeft = Offset(cR.x - 2 * take, y - 2 * take), size = sz, style = Stroke(7.dp.toPx()))
    val p0 = Offset(cL.x + take + gap, y)
    val p1 = Offset(cR.x - take - gap, y)
    pipeLine(ink, p0, p1)
    guideLine(ink, Offset(cL.x, y - take - 20.dp.toPx()), Offset(cL.x, y + 26.dp.toPx()))
    guideLine(ink, Offset(cR.x, y - take - 20.dp.toPx()), Offset(cR.x, y + 26.dp.toPx()))
    dimension(ink, Offset(cL.x, y - take - 14.dp.toPx()), Offset(cR.x, y - take - 14.dp.toPx()), ctoc, progress, Offset(0f, -13.dp.toPx()), ink.guide)
    dimension(ink, Offset(cL.x, y + 20.dp.toPx()), Offset(cL.x + take, y + 20.dp.toPx()), null, progress, color = ink.guide)
    dimension(ink, Offset(cR.x - take, y + 20.dp.toPx()), Offset(cR.x, y + 20.dp.toPx()), null, progress, color = ink.guide)
    label(ink, takeA, Offset(cL.x + take / 2 + 12.dp.toPx(), y + 36.dp.toPx()), ink.small)
    label(ink, takeB, Offset(cR.x - take / 2 - 12.dp.toPx(), y + 36.dp.toPx()), ink.small)
    dimension(ink, Offset(p0.x, y - 20.dp.toPx()), Offset(p1.x, y - 20.dp.toPx()), cut, progress, Offset(0f, 16.dp.toPx() + 20.dp.toPx() + 20.dp.toPx()))
}

/** Cut elbow: a 90 elbow with the cut line at the new angle and the takeout marked. */
fun DrawScope.cutElbowSketch(ink: SketchInk, angleDeg: Double, takeout: String, progress: Float) {
    val r = min(size.width * 0.5f, size.height * 0.66f)
    val c = Offset(size.width * 0.18f, size.height - 16.dp.toPx())
    val sz = Size(r * 2, r * 2)
    drawArc(ink.guide, 270f, 90f, false, topLeft = Offset(c.x - r, c.y - r), size = sz, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
    drawArc(ink.pipe, 270f, angleDeg.toFloat(), false, topLeft = Offset(c.x - r, c.y - r), size = sz, style = Stroke(9.dp.toPx()))
    val endAng = Math.toRadians(270 + angleDeg)
    val cutPt = Offset(c.x + r * cos(endAng).toFloat(), c.y + r * sin(endAng).toFloat())
    guideLine(ink, c, cutPt)
    guideLine(ink, c, Offset(c.x, c.y - r))
    val top = Offset(c.x, c.y - r)
    val tl = (r * tan(Math.toRadians(angleDeg / 2))).toFloat()
    dimension(ink, top + Offset(0f, -12.dp.toPx()), top + Offset(tl, -12.dp.toPx()), null, progress)
    label(ink, takeout, top + Offset(tl + 70.dp.toPx(), -12.dp.toPx()))
    label(ink, "${formatAngle(angleDeg)}°", c + Offset(24.dp.toPx(), -26.dp.toPx()), ink.small)
}

/** Wrap template preview: the cutback curve across the girth with its station lines. */
fun DrawScope.wrapCurveSketch(ink: SketchInk, ordinates: List<Double>, progress: Float) {
    if (ordinates.size < 2) return
    val pad = 12.dp.toPx()
    val maxY = max(ordinates.max(), 1e-6)
    val w = size.width - 2 * pad
    val base = size.height - 20.dp.toPx()
    val h = base - pad
    val n = ordinates.size - 1
    drawLine(ink.guide, Offset(pad, base), Offset(pad + w, base), strokeWidth = 1.5.dp.toPx())
    fun yAt(i: Int) = base - (h * (1 - ordinates[i] / maxY)).toFloat()
    for (i in 0..n) {
        val x = pad + w * i / n
        drawLine(ink.guide, Offset(x, base), Offset(x, yAt(i)), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
        if (n <= 16 || i % 4 == 0) label(ink, "${if (i == n) 1 else i + 1}", Offset(x, base + 10.dp.toPx()), ink.small)
    }
    val path = Path()
    val shown = (n * progress).toInt().coerceIn(1, n)
    for (i in 0..shown) {
        val x = pad + w * i / n
        if (i == 0) path.moveTo(x, yAt(i)) else path.lineTo(x, yAt(i))
    }
    drawPath(path, ink.dim, style = Stroke(2.5.dp.toPx()))
}
