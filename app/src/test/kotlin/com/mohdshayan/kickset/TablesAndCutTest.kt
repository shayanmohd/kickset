package com.mohdshayan.kickset

import com.mohdshayan.kickset.core.fittings.CutElbow
import com.mohdshayan.kickset.core.fittings.CutEnd
import com.mohdshayan.kickset.core.fittings.CutLength
import com.mohdshayan.kickset.core.fittings.CutLengthOutcome
import com.mohdshayan.kickset.core.fittings.EndFitting
import com.mohdshayan.kickset.core.fittings.TableSet
import com.mohdshayan.kickset.core.pipe.PipeSchedule
import com.mohdshayan.kickset.core.offset.SimpleOffset
import com.mohdshayan.kickset.core.units.LengthFormatter
import com.mohdshayan.kickset.core.units.UnitPrefs
import com.mohdshayan.kickset.core.units.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Reads the real bundled JSON, so a bad row in the shipped asset fails here. */
class TablesAndCutTest {
    private val tables: TableSet by lazy {
        val dir = listOf(File("src/main/assets/tables"), File("app/src/main/assets/tables")).first { it.isDirectory }
        TableSet.parse(
            File(dir, "asme_b16_9.json").readText(), File(dir, "asme_b16_11.json").readText(),
            File(dir, "asme_b16_5_bolts.json").readText(), File(dir, "asme_b36_10_19.json").readText(),
        )
    }

    private fun end(f: EndFitting, nps: String) = CutEnd(f, f.takeoutMm(tables.series(f.seriesId)!!.row(nps)!!))

    @Test
    fun oneCatalogueRowPerTableAndEveryTableNamesTwoSources() {
        assertEquals(3.0, tables.series("BW_90LR")!!.row("2")!!.`in`, 0.0)
        assertEquals(76.2, tables.series("BW_90LR")!!.row("2")!!.mm, 1e-9)
        assertEquals(0.625, tables.series("BW_45LR")!!.row("1/2")!!.`in`, 0.0)
        assertEquals(20.0, tables.series("BW_REDUCER")!!.row("24")!!.`in`, 0.0)
        assertNull("NPS 22 has only one source and must not ship", tables.series("BW_90LR")!!.row("22"))
        assertEquals(0.625, tables.series("SW_90")!!.row("1/2")!!.`in`, 0.0)
        assertEquals(0.75, tables.series("SW_COUPLING")!!.row("2")!!.`in`, 0.0)
        val c300 = tables.flanges.classes.first { it.`class` == 300 }.rows.first { it.nps == "16" }
        assertEquals(22.5, c300.boltCircleIn, 0.0); assertEquals(20, c300.bolts); assertEquals(7.5, c300.studLengthIn, 0.0)
        val c150 = tables.flanges.classes.first { it.`class` == 150 }.rows.first { it.nps == "10" }
        assertEquals(14.25, c150.boltCircleIn, 0.0)
        assertEquals(0.432, tables.pipe.sizes.first { it.nps == "6" }.wallsIn["80"]!!, 0.0)
        for (std in listOf(tables.buttWeld.sources, tables.socketWeld.sources, tables.flanges.sources, tables.pipe.sources))
            assertTrue(std.size >= 2 && std.all { it.url.startsWith("https://") })
    }

    @Test
    fun buttWeldCutAfterBothTakeoutsAndTwoRootGaps() {
        val o = CutLength.solve(1000.0, end(EndFitting.BW_90LR, "2"), end(EndFitting.BW_45LR, "2"), 3.0, 1.6)
        val r = (o as CutLengthOutcome.Ok).result
        assertEquals(882.875, r.cutMm, 1e-9)
        assertEquals("883 mm", LengthFormatter.millimetres(r.cutMm, 1.0))
        assertEquals(CutLengthOutcome.TooShort, CutLength.solve(100.0, end(EndFitting.BW_90LR, "4"), end(EndFitting.BW_90LR, "4"), 3.0, 1.6))
        assertEquals(CutLengthOutcome.MixedJoins, CutLength.solve(1000.0, end(EndFitting.BW_90LR, "2"), end(EndFitting.SW_90, "2"), 3.0, 1.6))
    }

    @Test
    fun socketWeldCutUsesCentreToSocketBottomAndHalfACoupling() {
        // NPS 1: 90 elbow 7/8 in (22.225 mm), coupling socket gap 1/2 in so 6.35 mm per side, 1.6 mm engagement gap each end.
        val r = (CutLength.solve(500.0, end(EndFitting.SW_90, "1"), end(EndFitting.SW_COUPLING, "1"), 3.0, 1.6) as CutLengthOutcome.Ok).result
        assertEquals(500.0 - 22.225 - 6.35 - 3.2, r.cutMm, 1e-9)
    }

    /**
     * The shown working has to end on the same number as the answer above it. When the working used a
     * finer denominator than the readout, a 1/16 cut of 19 9/16 was explained by a line reading 19 17/32,
     * which is exactly the sort of contradiction that loses a fitter's trust.
     */
    @Test
    fun workingLinesEndOnTheSameNumberAsTheAnswer() {
        for (denom in listOf(16, 32)) {
            val u = UnitPrefs(UnitSystem.INCH, denom, 1.0)
            val r = (CutLength.solve(28.284 * 25.4, end(EndFitting.BW_90LR, "4"), end(EndFitting.BW_45LR, "4"), 0.125 * 25.4, 1.6) as CutLengthOutcome.Ok).result
            val last = CutLength.working(r, u).last().text
            assertTrue("$last should end on ${u.primary(r.cutMm)}", last.endsWith("cut ${u.primary(r.cutMm)}"))
            val o = SimpleOffset.solve(20 * 25.4, 45.0)
            val line = SimpleOffset.working(o, u).first().text
            assertTrue("$line should end on ${u.primary(o.travelMm)}", line.endsWith("travel ${u.primary(o.travelMm)}"))
        }
    }

    @Test
    fun cutElbowTakeoutAndArcs() {
        val r = CutElbow.solve(76.0, 45.0, 60.325)
        assertEquals(31.48, r.takeoutMm, 0.005)
        assertEquals(59.69, r.centrelineArcMm, 0.005)
        assertEquals(76.0 * Math.PI / 4 + 30.1625 * Math.PI / 4, r.outsideArcMm, 1e-9)
    }

    @Test
    fun pipeWeightFromTheB3610Formula() {
        val two = PipeSchedule.detail(tables.pipe.sizes.first { it.nps == "2" }, "40")!!
        assertEquals(5.44, two.weightKgPerM, 0.005)
        assertEquals(52.50, two.idMm, 0.01)
        // Weldbend lists 28.60 lb/ft and 11.292 lb/ft of water for NPS 6 Sch 80.
        val six = PipeSchedule.detail(tables.pipe.sizes.first { it.nps == "6" }, "80")!!
        assertEquals(28.60, six.weightLbPerFt, 0.15)
        assertEquals(28.60 + 11.29, six.waterFilledLbPerFt, 0.2)
        assertNull(PipeSchedule.detail(tables.pipe.sizes.first { it.nps == "1/2" }, "30"))
    }
}
