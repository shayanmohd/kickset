package com.mohdshayan.kickset

import com.mohdshayan.kickset.core.offset.Multipliers
import com.mohdshayan.kickset.core.offset.ParallelOffset
import com.mohdshayan.kickset.core.offset.RollingOffset
import com.mohdshayan.kickset.core.offset.SimpleOffset
import com.mohdshayan.kickset.core.units.LengthFormatter
import com.mohdshayan.kickset.core.units.MM_PER_INCH
import com.mohdshayan.kickset.core.units.UnitPrefs
import com.mohdshayan.kickset.core.units.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Textbook offset values. A wrong multiplier here is pipe in the scrap bin. */
class OffsetGoldenTest {

    @Test
    fun travelMultipliersMatchTextbookTablesToFourAndThreePlaces() {
        val table = mapOf(45.0 to "1.4142", 22.5 to "2.6131", 60.0 to "1.1547", 30.0 to "2.0000", 11.25 to "5.1258")
        for ((angle, four) in table) assertEquals("csc $angle", four, LengthFormatter.decimal(Multipliers(angle).csc, 4))
        val three = mapOf(45.0 to "1.414", 22.5 to "2.613", 60.0 to "1.155", 30.0 to "2.000", 11.25 to "5.126")
        for ((angle, v) in three) assertEquals("csc $angle", v, LengthFormatter.decimal(Multipliers(angle).csc, 3))
        // Run multipliers: cot 45 is 1, cot 30 is 1.7321, cot 60 is 0.5774.
        assertEquals(1.0, Multipliers(45.0).cot, 1e-12)
        assertEquals("1.7321", LengthFormatter.decimal(Multipliers(30.0).cot, 4))
        assertEquals("0.5774", LengthFormatter.decimal(Multipliers(60.0).cot, 4))
    }

    @Test
    fun simpleOffsetOfTenInchesAtFortyFive() {
        val r = SimpleOffset.solve(10 * MM_PER_INCH, 45.0)
        assertEquals(14.1421, r.travelMm / MM_PER_INCH, 1e-4)
        assertEquals(10.0, r.runMm / MM_PER_INCH, 1e-9)
        val r22 = SimpleOffset.solve(10 * MM_PER_INCH, 22.5)
        assertEquals(26.1313, r22.travelMm / MM_PER_INCH, 1e-4)
        assertEquals(24.1421, r22.runMm / MM_PER_INCH, 1e-4)
    }

    @Test
    fun rollingOffsetTwelveBySixteenGivesTrueTwentyAndTravel28AndFiveSixteenths() {
        val r = RollingOffset.solve(12 * MM_PER_INCH, 16 * MM_PER_INCH, 45.0)
        assertEquals(20.0, r.trueOffsetMm / MM_PER_INCH, 1e-9)
        assertEquals(28.2843, r.travelMm / MM_PER_INCH, 1e-4)
        assertEquals(20.0, r.runMm / MM_PER_INCH, 1e-9)
        assertEquals("28 5/16 in", LengthFormatter.inches(r.travelMm, 16))
        val lines = RollingOffset.working(r, UnitPrefs(UnitSystem.INCH, 16, 1.0)).map { it.text }
        assertTrue(lines.joinToString("\n"), lines.any { it.contains("(1.4142)") && it.contains("travel") })
    }

    @Test
    fun inverseRollingOffsetSolvesTheAngleFromTheRun() {
        val r = RollingOffset.solveFromRun(12 * MM_PER_INCH, 16 * MM_PER_INCH, 20 * MM_PER_INCH)
        assertEquals(45.0, r.angleDeg, 1e-9)
        assertEquals(28.2843, r.travelMm / MM_PER_INCH, 1e-4)
        val steep = RollingOffset.solveFromRun(300.0, 0.0, 519.615)
        assertEquals(30.0, steep.angleDeg, 1e-3)
    }

    @Test
    fun parallelOffsetsAdvanceBySpreadTimesTanHalfAngle() {
        val r = ParallelOffset.solve(300.0, 45.0, 3)
        assertEquals(0.0, r.lines[0].advanceMm, 0.0)
        assertEquals(124.26, r.lines[1].advanceMm, 0.005)
        assertEquals(248.53, r.lines[2].advanceMm, 0.005)
        assertEquals(59.67, ParallelOffset.solve(300.0, 22.5, 2).lines[1].advanceMm, 0.005)
    }
}
