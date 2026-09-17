package com.mohdshayan.kickset.core.template

import com.mohdshayan.kickset.core.pdf.PdfPage
import com.mohdshayan.kickset.core.pdf.PT_PER_INCH
import com.mohdshayan.kickset.core.pdf.mmToPt
import com.mohdshayan.kickset.core.units.LengthFormatter
import kotlin.math.PI

/** A vector drawing in millimetres, y up. Rendered onto tiled PDF pages and onto the preview canvas. */
sealed interface DrawOp {
    data class Line(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val widthPt: Double = 0.5, val dashed: Boolean = false) : DrawOp
    data class Poly(val points: List<Pair<Double, Double>>, val widthPt: Double = 1.0) : DrawOp
    data class Label(val x: Double, val y: Double, val sizePt: Double, val text: String) : DrawOp
}

data class Drawing(val widthMm: Double, val heightMm: Double, val ops: List<DrawOp>)

object TemplateDrawings {
    private const val SIDE = 15.0
    private const val EDGE_Y = 14.0
    private const val HEADROOM = 10.0

    /**
     * The wrap strip: a straight edge to lay on a square line drawn around the pipe, station lines, and the
     * cut curve at height (max cutback + 10 mm - y). The pipe end is longest where y is 0.
     */
    fun wrap(curve: WrapCurve): Drawing {
        val top = curve.maxOrdinateMm + HEADROOM
        val ops = mutableListOf<DrawOp>()
        val g = curve.girthMm
        ops += DrawOp.Line(SIDE, EDGE_Y, SIDE + g, EDGE_Y, 0.8)
        ops += DrawOp.Label(SIDE, 4.0, 7.0, "Straight edge: lay on a square line around the pipe")
        for (i in 0..curve.stations) {
            val x = SIDE + curve.xMm(i)
            val yTop = EDGE_Y + top - curve.ordinatesMm[i]
            ops += DrawOp.Line(x, EDGE_Y, x, yTop, if (i == 0 || i == curve.stations) 0.8 else 0.3, dashed = i != 0 && i != curve.stations)
            ops += DrawOp.Label(x - 1.2, EDGE_Y - 5.0, 6.0, "${i + 1}".let { if (i == curve.stations) "1" else it })
        }
        val fine = (0..720).map { k ->
            val phi = 2 * PI * k / 720
            val y = curve.exact(phi)
            (SIDE + g * k / 720) to (EDGE_Y + top - y)
        }
        ops += DrawOp.Poly(fine, 1.2)
        ops += DrawOp.Label(SIDE + 2, EDGE_Y + top + 3.0, 7.0, "Cut line")
        return Drawing(g + 2 * SIDE, EDGE_Y + top + 10.0, ops)
    }

    /** Header hole outline at full size with its two centrelines. */
    fun hole(points: List<Pair<Double, Double>>): Drawing {
        val minX = points.minOf { it.first }
        val maxX = points.maxOf { it.first }
        val minS = points.minOf { it.second }
        val maxS = points.maxOf { it.second }
        val ox = SIDE - minX
        val oy = 8.0 - minS
        val ops = mutableListOf<DrawOp>()
        ops += DrawOp.Poly(points.map { (it.first + ox) to (it.second + oy) }, 1.2)
        ops += DrawOp.Line(minX + ox - 8, oy, maxX + ox + 8, oy, 0.4, dashed = true)
        ops += DrawOp.Line(ox, minS + oy - 8, ox, maxS + oy + 8, 0.4, dashed = true)
        ops += DrawOp.Label(maxX + ox + 10, oy - 1.0, 7.0, "Header axis")
        ops += DrawOp.Label(ox + 2, maxS + oy + 4, 7.0, "Wrap this way around the header")
        return Drawing(maxX - minX + 2 * SIDE + 45, maxS - minS + 18, ops)
    }
}

/** Composes printable pages: tiled drawings with overlap marks and calibration bars, then an ordinate table. */
object TemplatePages {

    fun tiled(drawing: Drawing, layout: SheetLayout, title: String, sheetNoun: String, firstSheet: Int, totalSheets: Int): List<PdfPage> {
        val cols = layout.columnsFor(drawing.widthMm)
        val rows = layout.rowsFor(drawing.heightMm)
        val pages = mutableListOf<PdfPage>()
        var n = firstSheet
        for (r in 0 until rows) for (c in 0 until cols) {
            val page = PdfPage(layout.paper.landscapeWidthPt, layout.paper.landscapeHeightPt)
            val ox = layout.originX(c)
            val oy = layout.originY(r)
            val wl = layout.windowLeftMm
            val wb = layout.windowBottomMm
            page.comment("window col=$c row=$r")
            page.lineWidth(0.3).gray(0.6).rect(mmToPt(wl), mmToPt(wb), mmToPt(layout.windowWidthMm), mmToPt(layout.windowHeightMm)).gray(0.0)
            page.clip(mmToPt(wl), mmToPt(wb), mmToPt(layout.windowWidthMm), mmToPt(layout.windowHeightMm))
            for (op in drawing.ops) render(page, op, wl - ox, wb - oy)
            page.restore()
            // Overlap marks where a neighbour repeats this strip.
            page.lineWidth(0.4).dash(3.0, 2.0)
            if (c < cols - 1) {
                val x = mmToPt(wl + layout.windowWidthMm - layout.overlapMm)
                page.comment("overlap right").line(x, mmToPt(wb), x, mmToPt(wb + layout.windowHeightMm))
            }
            if (c > 0) {
                val x = mmToPt(wl + layout.overlapMm)
                page.comment("overlap left").line(x, mmToPt(wb), x, mmToPt(wb + layout.windowHeightMm))
            }
            if (r < rows - 1) {
                val y = mmToPt(wb + layout.windowHeightMm - layout.overlapMm)
                page.comment("overlap top").line(mmToPt(wl), y, mmToPt(wl + layout.windowWidthMm), y)
            }
            if (r > 0) {
                val y = mmToPt(wb + layout.overlapMm)
                page.comment("overlap bottom").line(mmToPt(wl), y, mmToPt(wl + layout.windowWidthMm), y)
            }
            page.solid()
            band(page, layout, "$title. $sheetNoun sheet $n of $totalSheets, column ${c + 1} of $cols, row ${r + 1} of $rows.")
            pages += page
            n++
        }
        return pages
    }

    fun sheetCount(drawing: Drawing, layout: SheetLayout) = layout.sheetsFor(drawing.widthMm, drawing.heightMm)

    private fun render(page: PdfPage, op: DrawOp, dx: Double, dy: Double) {
        when (op) {
            is DrawOp.Line -> {
                page.lineWidth(op.widthPt)
                if (op.dashed) page.dash(2.0, 2.0)
                page.line(mmToPt(op.x1 + dx), mmToPt(op.y1 + dy), mmToPt(op.x2 + dx), mmToPt(op.y2 + dy))
                if (op.dashed) page.solid()
            }
            is DrawOp.Poly -> page.lineWidth(op.widthPt).polyline(op.points.map { mmToPt(it.first + dx) to mmToPt(it.second + dy) })
            is DrawOp.Label -> page.text(mmToPt(op.x + dx), mmToPt(op.y + dy), op.sizePt, op.text)
        }
    }

    /** The bottom band on every sheet: what this sheet is, how to print it, and both calibration bars. */
    fun band(page: PdfPage, layout: SheetLayout, caption: String) {
        val left = layout.marginMm
        page.text(mmToPt(left), mmToPt(left + 22.0), 8.0, caption, bold = true)
        page.text(mmToPt(left), mmToPt(left + 16.0), 7.5, "Print at 100 percent (actual size) with no fit to page. Measure both bars before you wrap and cut. Kickset reference values.")
        val y = mmToPt(left + 6.0)
        val tick = mmToPt(2.0)
        // 100 mm bar: exactly 283.46 pt.
        val x0 = mmToPt(left)
        val x1 = x0 + mmToPt(SheetLayout.BAR_METRIC_MM)
        page.lineWidth(1.0).comment("bar100mm").line(x0, y, x1, y)
        page.lineWidth(0.5)
        for (k in 0..10) {
            val x = x0 + mmToPt(10.0 * k)
            val h = if (k % 5 == 0) tick * 1.5 else tick
            page.line(x, y, x, y + h)
        }
        page.text(x1 + mmToPt(2.0), y - 2.5, 7.5, "100 mm")
        // 4 inch bar: exactly 288 pt.
        val ix0 = x1 + mmToPt(25.0)
        val ix1 = ix0 + 4 * PT_PER_INCH
        page.lineWidth(1.0).comment("bar4in").line(ix0, y, ix1, y)
        page.lineWidth(0.5)
        for (k in 0..16) {
            val x = ix0 + PT_PER_INCH * k / 4
            val h = if (k % 4 == 0) tick * 1.5 else tick * 0.8
            page.line(x, y, x, y + h)
        }
        page.text(ix1 + mmToPt(2.0), y - 2.5, 7.5, "4 in")
    }

    /** Station table so the curve can also be marked by hand with a tape. */
    fun ordinateTable(curve: WrapCurve, layout: SheetLayout, title: String, sheetNo: Int, total: Int): List<PdfPage> {
        val rowsPerCol = 22
        val perPage = rowsPerCol * 2
        val entries = (0 until curve.stations).map { i -> Triple(i + 1, curve.phiDeg[i], curve.ordinatesMm[i]) }
        return entries.chunked(perPage).mapIndexed { pi, chunk ->
            val page = PdfPage(layout.paper.landscapeWidthPt, layout.paper.landscapeHeightPt)
            val top = layout.paper.heightMm - layout.marginMm - 8
            page.text(mmToPt(layout.marginMm), mmToPt(top), 11.0, "Ordinates: cutback from the longest point, girth ${LengthFormatter.millimetresExact(curve.girthMm)} (${LengthFormatter.inches(curve.girthMm, 16)})", bold = true)
            page.text(mmToPt(layout.marginMm), mmToPt(top - 6), 8.0, "Divide the pipe into ${curve.stations} equal stations, number them from the longest point, and mark each cutback back from a square line.")
            chunk.chunked(rowsPerCol).forEachIndexed { ci, col ->
                val x = layout.marginMm + ci * 135.0
                var y = top - 16
                page.text(mmToPt(x), mmToPt(y), 8.5, "Station", bold = true)
                page.text(mmToPt(x + 22), mmToPt(y), 8.5, "Angle", bold = true)
                page.text(mmToPt(x + 45), mmToPt(y), 8.5, "Cutback mm", bold = true)
                page.text(mmToPt(x + 80), mmToPt(y), 8.5, "Cutback in", bold = true)
                for ((st, phi, ord) in col) {
                    y -= 6.0
                    page.text(mmToPt(x), mmToPt(y), 8.5, "$st")
                    page.text(mmToPt(x + 22), mmToPt(y), 8.5, "${LengthFormatter.decimal(phi, 2)}°")
                    page.text(mmToPt(x + 45), mmToPt(y), 8.5, LengthFormatter.decimal(ord, 1))
                    page.text(mmToPt(x + 80), mmToPt(y), 8.5, LengthFormatter.inchParts(ord, 16).toString())
                }
            }
            band(page, layout, "$title. Ordinate sheet ${sheetNo + pi} of $total.")
            page
        }
    }

    fun ordinatePageCount(curve: WrapCurve) = (curve.stations + 43) / 44
}

/** Every page of one template export, in print order. */
object TemplateDocument {
    fun build(curve: WrapCurve, hole: List<Pair<Double, Double>>?, layout: SheetLayout, title: String): List<PdfPage> {
        val wrap = TemplateDrawings.wrap(curve)
        val holeDrawing = hole?.let { TemplateDrawings.hole(it) }
        val wrapCount = TemplatePages.sheetCount(wrap, layout)
        val holeCount = holeDrawing?.let { TemplatePages.sheetCount(it, layout) } ?: 0
        val ordCount = TemplatePages.ordinatePageCount(curve)
        val total = wrapCount + holeCount + ordCount
        val pages = mutableListOf<PdfPage>()
        pages += TemplatePages.tiled(wrap, layout, title, "Wrap", 1, total)
        if (holeDrawing != null) pages += TemplatePages.tiled(holeDrawing, layout, title, "Header hole", wrapCount + 1, total)
        pages += TemplatePages.ordinateTable(curve, layout, title, wrapCount + holeCount + 1, total)
        return pages
    }
}
