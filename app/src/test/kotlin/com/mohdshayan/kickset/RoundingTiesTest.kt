package com.mohdshayan.kickset

import com.mohdshayan.kickset.calc.CutSolver
import com.mohdshayan.kickset.calc.Outcome
import com.mohdshayan.kickset.core.fittings.TableSet
import com.mohdshayan.kickset.core.jobs.CutInputs
import com.mohdshayan.kickset.core.units.LengthFormatter
import com.mohdshayan.kickset.core.units.MM_PER_INCH
import com.mohdshayan.kickset.core.units.UnitPrefs
import com.mohdshayan.kickset.core.units.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * A quantity that lands exactly on a rounding boundary has to round the way the decimal it stands
 * for rounds, and it has to round the same way everywhere it is printed. A cut of a quarter of a
 * millimetre does not arrive as a quarter of a millimetre: an NPS 4 socket coupling is 0.75 in
 * between socket bottoms and each end takes half of it, and `0.75 x 25.4 / 2` comes out of a double
 * as 9.524999999999999. Rounding that a hair below the boundary downwards printed one answer in the
 * readout and a different one in the working line beneath it.
 */
class RoundingTiesTest {

    /** Both millimetre steps the app offers. */
    private val mmSteps = listOf(1.0, 0.5)

    /** Both fraction settings the app offers, and the finer ones a working line falls back to. */
    private val inchDenoms = listOf(16, 32, 64, 128)

    private val prefs = listOf(
        UnitPrefs(UnitSystem.MM, 16, 1.0),
        UnitPrefs(UnitSystem.MM, 32, 0.5),
        UnitPrefs(UnitSystem.INCH, 16, 1.0),
        UnitPrefs(UnitSystem.INCH, 32, 0.5),
    )

    /** Small, ordinary, and longer than any pipe the app will take: the whole range of a length. */
    private val magnitudes = listOf(0L, 1L, 2L, 7L, 3_955L, 19_777L, 1_999_998L)

    /**
     * One exact quantity as arithmetic actually hands it over: on the boundary, a last bit either
     * side of it, and the kind of drift a chain of multiplications and subtractions leaves behind.
     */
    private fun asDelivered(exact: Double): List<Double> = listOf(
        exact,
        Math.nextAfter(exact, Double.NEGATIVE_INFINITY),
        Math.nextAfter(exact, Double.POSITIVE_INFINITY),
        exact - abs(exact) * 1e-13,
        exact + abs(exact) * 1e-13,
    )

    // ------------------------------------------------------------------ millimetres

    @Test
    fun millimetreTiesRoundAwayFromZeroAtBothSteps() {
        for (step in mmSteps) {
            for (n in magnitudes) {
                val base = n * step
                val tie = base + step / 2.0
                val away = base + step
                for (v in asDelivered(tie)) {
                    assertEquals("$v at $step mm", away, LengthFormatter.roundTo(v, step), 1e-9)
                    assertEquals("${-v} at $step mm", -away, LengthFormatter.roundTo(-v, step), 1e-9)
                }
            }
        }
    }

    @Test
    fun theMillimetreReadoutPrintsTheTieItRoundedTo() {
        assertEquals("1978 mm", LengthFormatter.millimetres(1977.75, 0.5))
        assertEquals("1978 mm", LengthFormatter.millimetres(2000.0 - 2 * (0.75 * MM_PER_INCH / 2) - 2 * 1.6, 0.5))
        assertEquals("953 mm", LengthFormatter.millimetres(952.5, 1.0))
        assertEquals("318 mm", LengthFormatter.millimetres(317.5, 1.0))
        assertEquals("698.5 mm", LengthFormatter.millimetres(698.25, 0.5))
        assertEquals("-1978 mm", LengthFormatter.millimetres(-1977.75, 0.5))
        assertEquals("-953 mm", LengthFormatter.millimetres(-952.5, 1.0))
        // A quantity that is not on a boundary is left exactly where it was.
        assertEquals("1977 mm", LengthFormatter.millimetres(1977.4, 1.0))
        assertEquals("1977.5 mm", LengthFormatter.millimetres(1977.6, 0.5))
        assertEquals("0 mm", LengthFormatter.millimetres(-0.2, 1.0))
    }

    // ------------------------------------------------------------------ inches

    @Test
    fun inchFractionTiesRoundAwayFromZeroAtEveryDenominator() {
        for (denom in inchDenoms) {
            for (n in magnitudes) {
                val tieMm = (n + 0.5) / denom * MM_PER_INCH
                val awayMm = (n + 1).toDouble() / denom * MM_PER_INCH
                for (v in asDelivered(tieMm)) {
                    assertEquals("$v at 1/$denom in", awayMm, LengthFormatter.inchValue(v, denom), 1e-9)
                    assertEquals("${-v} at 1/$denom in", -awayMm, LengthFormatter.inchValue(-v, denom), 1e-9)
                }
            }
        }
    }

    @Test
    fun theInchReadoutPrintsTheFractionItRoundedTo() {
        assertEquals("1/16", LengthFormatter.inchParts(0.03125 * MM_PER_INCH, 16).toString())
        assertEquals("1/8", LengthFormatter.inchParts(0.09375 * MM_PER_INCH, 16).toString())
        assertEquals("-1/16", LengthFormatter.inchParts(-0.03125 * MM_PER_INCH, 16).toString())
        assertEquals("1/32", LengthFormatter.inchParts(0.015625 * MM_PER_INCH, 32).toString())
        assertEquals("-1/32", LengthFormatter.inchParts(-0.015625 * MM_PER_INCH, 32).toString())
        // Half a sixteenth below the whole inch still carries into it, in both directions.
        assertEquals("16", LengthFormatter.inchParts(15.96875 * MM_PER_INCH, 16).toString())
        assertEquals("-16", LengthFormatter.inchParts(-15.96875 * MM_PER_INCH, 16).toString())
    }

    // ------------------------------------------------------------------ decimal places

    @Test
    fun decimalTiesRoundAwayFromZeroAtEveryPlaceTheAppWrites() {
        for (places in 1..8) {
            val f = Math.pow(10.0, places.toDouble())
            for (n in magnitudes) {
                val tie = (n + 0.5) / f
                val away = (n + 1).toDouble() / f
                val text = LengthFormatter.decimal(away, places)
                for (v in asDelivered(tie)) {
                    assertEquals("$v to $places places", away, LengthFormatter.round(v, places), away * 1e-12 + 1e-12)
                    assertEquals("$v to $places places", text, LengthFormatter.decimal(v, places))
                    assertEquals("${-v} to $places places", "-$text", LengthFormatter.decimal(-v, places))
                }
            }
        }
    }

    @Test
    fun aWorkingLineInMillimetresPrintsTheTieItRoundedTo() {
        assertEquals("1977.8 mm", LengthFormatter.millimetresExact(1977.75))
        assertEquals("-1977.8 mm", LengthFormatter.millimetresExact(-1977.75))
        assertEquals("952.5 mm", LengthFormatter.millimetresExact(952.5))
        // A working line never prints a negative zero for a length just under nothing.
        assertEquals("0.0 mm", LengthFormatter.millimetresExact(-0.04))
    }

    // ------------------------------------------------------------------ answer against working

    /** Every tie the fitter's own precision has, in the unit he is working in. */
    private fun tiesFor(u: UnitPrefs): List<Double> = magnitudes.map { n ->
        if (u.system == UnitSystem.INCH) (n + 0.5) / u.inchDenom * MM_PER_INCH else n * u.mmStep + u.mmStep / 2.0
    }

    @Test
    fun onEveryTieTheWorkingRoundsBackToTheAnswerAboveIt() {
        for (u in prefs) {
            for (tie in tiesFor(u)) {
                for (v in asDelivered(tie)) {
                    for (signed in listOf(v, -v)) {
                        val f = u.answer(signed)
                        assertEquals("$u on $signed", u.primary(signed), u.primary(f.value))
                        assertTrue("$u on $signed fell back to the headline", f.level >= 0)
                        // The same length written exactly is still the same length.
                        assertEquals("$u on $signed", u.primary(signed), u.primary(u.exact(signed).value))
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ the whole screen

    private val tables: TableSet by lazy {
        val dir = listOf(File("src/main/assets/tables"), File("app/src/main/assets/tables")).first { it.isDirectory }
        TableSet.parse(
            File(dir, "asme_b16_9.json").readText(), File(dir, "asme_b16_11.json").readText(),
            File(dir, "asme_b16_5_bolts.json").readText(), File(dir, "asme_b36_10_19.json").readText(),
        )
    }

    /**
     * The screen the defect was found on. Two NPS 4 socket couplings 2000 mm apart leave an exact cut
     * of 1977.75 mm, which is a tie at the 0.5 mm setting. The readout and the working under it have
     * to name the same cut.
     */
    @Test
    fun theQuarterMillimetreCutAgreesWithItsOwnWorking() {
        val u = UnitPrefs(UnitSystem.MM, 32, 0.5)
        val i = CutInputs(endA = "SW_COUPLING", endB = "SW_COUPLING", swNps = "4", centreToCentre = "2000")
        val o = CutSolver.solve(i, u, 3.0, 1.6, tables).outcome as Outcome.Solved
        assertTrue(o.headline, o.headline.startsWith("Cut 1978 mm"))
        assertEquals("Cut length", o.readouts.first().label)
        assertEquals("1978 mm", o.readouts.first().primary)
        val last = o.working.last()
        assertTrue(last, last.endsWith("= cut 1977.8 mm"))
        assertEquals("1978 mm", LengthFormatter.millimetres(1977.8, 0.5))
    }
}
