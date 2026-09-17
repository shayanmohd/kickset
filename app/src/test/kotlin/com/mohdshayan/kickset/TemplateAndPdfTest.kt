package com.mohdshayan.kickset

import com.mohdshayan.kickset.core.pdf.Paper
import com.mohdshayan.kickset.core.pdf.PdfWriter
import com.mohdshayan.kickset.core.template.SheetLayout
import com.mohdshayan.kickset.core.template.TemplateDocument
import com.mohdshayan.kickset.core.template.TemplateOutcome
import com.mohdshayan.kickset.core.template.WrapTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateAndPdfTest {

    private fun ok(o: TemplateOutcome) = (o as TemplateOutcome.Ok)

    @Test
    fun equalSaddleCutsBackOneRadiusAtNinetyAndNothingAtZero() {
        val r = 57.15
        val c = ok(WrapTemplates.saddle(114.3, r, r, 16)).curve
        assertEquals(0.0, c.ordinatesMm[0], 1e-9)
        assertEquals(r, c.ordinatesMm[4], 1e-9)   // station 5 is phi 90
        assertEquals(0.0, c.ordinatesMm[8], 1e-9)
        assertEquals(r, c.ordinatesMm[12], 1e-9)
        assertEquals(Math.PI * 114.3, c.girthMm, 1e-9)
        // Reduced branch NPS 4 on NPS 6: R - sqrt(R^2 - r^2) at phi 90.
        val red = ok(WrapTemplates.saddle(114.3, 57.15, 84.1375, 32)).curve
        assertEquals(84.1375 - Math.sqrt(84.1375 * 84.1375 - 57.15 * 57.15), red.maxOrdinateMm, 1e-9)
        assertEquals(TemplateOutcome.BranchTooLarge, WrapTemplates.saddle(168.3, 84.1, 57.15, 16))
    }

    @Test
    fun twoPieceNinetyMiterCutsBackOneOdAndLateralAtNinetyIsASaddle() {
        assertEquals(22.5, WrapTemplates.miterCutAngle(90.0, 3), 1e-12)
        assertEquals(45.0, WrapTemplates.miterCutAngle(90.0, 2), 1e-12)
        val m = ok(WrapTemplates.miter(114.3, 57.15, 45.0, 16)).curve
        assertEquals(114.3, m.maxOrdinateMm, 1e-9)
        assertEquals(114.3, m.ordinatesMm[8], 1e-9)
        val s = ok(WrapTemplates.saddle(114.3, 50.0, 84.0, 32)).curve
        val l = ok(WrapTemplates.lateral(114.3, 50.0, 84.0, 89.0, 32)).curve
        val l90 = WrapTemplates.lateral(114.3, 50.0, 84.0, 90.0, 32)
        assertEquals(TemplateOutcome.BadAngle, l90)
        for (i in 0..32) assertEquals(s.ordinatesMm[i], l.ordinatesMm[i], 2.0)
        // Lateral at 45: phi 180 cutback is 2r / tan 45.
        val l45 = ok(WrapTemplates.lateral(114.3, 50.0, 84.0, 45.0, 16)).curve
        assertEquals(100.0, l45.ordinatesMm[8], 1e-9)
    }

    @Test
    fun longStripTilesToEightA4Sheets() {
        val a4 = SheetLayout(Paper.A4)
        assertEquals(277.0, a4.windowWidthMm, 1e-9)
        assertEquals(8, a4.sheetsFor(1915.0, 150.0))
        assertEquals(1, a4.sheetsFor(277.0, 150.0))
        assertEquals(2, a4.sheetsFor(277.5, 150.0))
        assertEquals(2, SheetLayout(Paper.LETTER).rowsFor(200.0))
    }

    /** Reads the calibration bars back out of the PDF bytes and measures them. */
    @Test
    fun calibrationBarsInTheWrittenPdfMeasureTrue() {
        val o = ok(WrapTemplates.saddle(114.3, 57.15, 84.1375, 16))
        for (paper in Paper.entries) {
            val pages = TemplateDocument.build(o.curve, o.hole, SheetLayout(paper), "Saddle NPS 4 on NPS 6")
            val bytes = PdfWriter.write(pages, "test")
            java.io.File("build").mkdirs()
            java.io.File("build/sample-saddle-4-on-6-${paper.name.lowercase()}.pdf").writeBytes(bytes)
            val pdf = String(bytes, Charsets.ISO_8859_1)
            assertTrue(pdf.startsWith("%PDF-1.4") && pdf.trimEnd().endsWith("%%EOF"))
            val w = String.format(java.util.Locale.ROOT, "%.2f", paper.widthMm * 72 / 25.4)
            val h = String.format(java.util.Locale.ROOT, "%.2f", paper.heightMm * 72 / 25.4)
            assertTrue(pdf.contains("/MediaBox [0 0 $w $h]"))
            if (paper == Paper.A4) assertTrue(pdf.contains("/MediaBox [0 0 841.89 595.28]"))
            val lineRe = Regex("""^% (bar100mm|bar4in)\n([-\d.]+) ([-\d.]+) m ([-\d.]+) ([-\d.]+) l S$""", RegexOption.MULTILINE)
            val bars = lineRe.findAll(pdf).toList()
            assertEquals("one pair of bars per page", pages.size * 2, bars.size)
            for (m in bars) {
                val len = m.groupValues[4].toDouble() - m.groupValues[2].toDouble()
                assertEquals(m.groupValues[3], m.groupValues[5])
                if (m.groupValues[1] == "bar100mm") assertEquals(283.46, len, 0.005) else assertEquals(288.0, len, 0.001)
            }
            // xref offsets point at real objects.
            val startxref = Regex("""startxref\n(\d+)""").find(pdf)!!.groupValues[1].toInt()
            assertTrue(pdf.substring(startxref).startsWith("xref"))
            val firstOffset = Regex("""\n(\d{10}) 00000 n """).find(pdf)!!.groupValues[1].toInt()
            assertTrue(pdf.substring(firstOffset).startsWith("1 0 obj"))
        }
    }
}
