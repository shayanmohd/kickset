package com.mohdshayan.kickset.core.template

import com.mohdshayan.kickset.core.pdf.Paper
import kotlin.math.ceil
import kotlin.math.max

/**
 * Tiles a drawing larger than one sheet across landscape pages. Every sheet has 10 mm margins, a band at
 * the bottom for the calibration bars, and repeats the last 10 mm of its neighbour so the sheets can be
 * lined up on the overlap marks.
 */
data class SheetLayout(val paper: Paper) {
    val marginMm = 10.0
    val overlapMm = 10.0
    val bandMm = 30.0

    val windowWidthMm get() = paper.widthMm - 2 * marginMm
    val windowHeightMm get() = paper.heightMm - 2 * marginMm - bandMm
    val windowLeftMm get() = marginMm
    val windowBottomMm get() = marginMm + bandMm

    fun columnsFor(widthMm: Double) = tiles(widthMm, windowWidthMm)
    fun rowsFor(heightMm: Double) = tiles(heightMm, windowHeightMm)
    fun sheetsFor(widthMm: Double, heightMm: Double) = columnsFor(widthMm) * rowsFor(heightMm)

    private fun tiles(length: Double, window: Double): Int =
        1 + max(0, ceil((length - window - 1e-9) / (window - overlapMm)).toInt())

    /** Left edge, in drawing millimetres, of column c (0 based). */
    fun originX(c: Int) = c * (windowWidthMm - overlapMm)
    fun originY(r: Int) = r * (windowHeightMm - overlapMm)

    companion object {
        const val BAR_METRIC_MM = 100.0
        const val BAR_INCH_MM = 101.6
    }
}
