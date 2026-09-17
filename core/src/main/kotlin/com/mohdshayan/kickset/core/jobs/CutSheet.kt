package com.mohdshayan.kickset.core.jobs

import com.mohdshayan.kickset.core.pdf.Paper
import com.mohdshayan.kickset.core.pdf.PdfPage
import com.mohdshayan.kickset.core.pdf.mmToPt

data class CutSheetEntry(val label: String, val kind: String, val headline: String, val working: List<String>)

/** A portrait cut sheet a shop printer can take: one block per saved calculation. */
object CutSheetPdf {
    fun pages(jobName: String, dateText: String, entries: List<CutSheetEntry>, paper: Paper): List<PdfPage> {
        val w = paper.heightMm
        val h = paper.widthMm
        val margin = 15.0
        val pages = mutableListOf<PdfPage>()
        var page = PdfPage(mmToPt(w), mmToPt(h))
        var y = h - margin
        fun newPage() {
            pages += page
            page = PdfPage(mmToPt(w), mmToPt(h))
            y = h - margin
        }
        fun wrap(text: String, sizePt: Double, widthMm: Double): List<String> {
            val maxChars = (mmToPt(widthMm) / (sizePt * 0.52)).toInt().coerceAtLeast(10)
            val out = mutableListOf<String>()
            var line = StringBuilder()
            for (word in text.split(' ')) {
                if (line.isNotEmpty() && line.length + 1 + word.length > maxChars) { out += line.toString(); line = StringBuilder() }
                if (line.isNotEmpty()) line.append(' ')
                line.append(word)
            }
            if (line.isNotEmpty()) out += line.toString()
            return out
        }
        page.text(mmToPt(margin), mmToPt(y - 6), 16.0, jobName.take(80), bold = true)
        y -= 12
        val cutCount = if (entries.size == 1) "1 saved cut" else "${entries.size} saved cuts"
        page.text(mmToPt(margin), mmToPt(y - 4), 9.0, "Cut sheet, $dateText. $cutCount. Reference values: check against drawings and fittings before cutting.")
        y -= 12
        for (e in entries) {
            val workLines = e.working.flatMap { wrap(it, 8.5, w - 2 * margin - 6) }
            val need = 7 + 6 + workLines.size * 4.5 + 6
            if (y - need < margin) newPage()
            page.lineWidth(0.3).gray(0.6).line(mmToPt(margin), mmToPt(y), mmToPt(w - margin), mmToPt(y)).gray(0.0)
            y -= 6
            page.text(mmToPt(margin), mmToPt(y), 11.0, e.label.take(90), bold = true)
            page.text(mmToPt(w - margin - 40), mmToPt(y), 9.0, e.kind)
            y -= 6
            for (hl in wrap(e.headline, 10.0, w - 2 * margin)) { page.text(mmToPt(margin), mmToPt(y), 10.0, hl); y -= 5 }
            for (wl in workLines) { page.text(mmToPt(margin + 6), mmToPt(y), 8.5, wl); y -= 4.5 }
            y -= 4
        }
        pages += page
        return pages
    }
}
