package com.mohdshayan.kickset

import com.mohdshayan.kickset.core.units.LengthFormatter
import com.mohdshayan.kickset.core.units.LengthParser
import com.mohdshayan.kickset.core.units.MM_PER_INCH
import com.mohdshayan.kickset.core.units.ParsedLength
import com.mohdshayan.kickset.core.units.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitsTest {
    private fun inches(s: String, sys: UnitSystem = UnitSystem.INCH): Double {
        val p = LengthParser.parse(s, sys)
        assertTrue("$s should parse, got $p", p is ParsedLength.Ok)
        return (p as ParsedLength.Ok).mm / MM_PER_INCH
    }

    @Test
    fun parserAcceptsEveryShopFormat() {
        assertEquals(12.3125, inches("12 5/16"), 1e-12)
        assertEquals(12.3125, inches("12-5/16"), 1e-12)
        assertEquals(12.3125, inches("1' 0 5/16"), 1e-12)
        assertEquals(14.5, inches("1'-2 1/2\""), 1e-12)
        assertEquals(12.3125, inches("12.3125"), 1e-12)
        assertEquals(0.3125, inches("5/16"), 1e-12)
        assertEquals(313.0, inches("313"), 1e-12)
        assertEquals(24.0, inches("2'"), 1e-12)
        // In millimetre mode a bare number is millimetres, but a fraction still means inches.
        assertEquals(313.0, (LengthParser.parse("313", UnitSystem.MM) as ParsedLength.Ok).mm, 1e-12)
        assertEquals(12.3125, inches("12 5/16", UnitSystem.MM), 1e-12)
        assertEquals(313.0, (LengthParser.parse("313 mm", UnitSystem.INCH) as ParsedLength.Ok).mm, 1e-12)
        assertEquals(16.0, inches("16 in", UnitSystem.MM), 1e-12)
    }

    @Test
    fun parserRejectsHalfTypedAndNonsense() {
        for (bad in listOf("12 5/", "abc", "5/0", "12 17/16", "1.2.3", "--4", "12 5/16 mm", "'", "12''", "/16", "4 mm mm"))
            assertEquals("'$bad'", ParsedLength.Invalid, LengthParser.parse(bad, UnitSystem.INCH))
        assertEquals(ParsedLength.Empty, LengthParser.parse("   ", UnitSystem.MM))
    }

    @Test
    fun fractionRoundingCarriesIntoTheWholeInch() {
        assertEquals("16", LengthFormatter.inchParts(15.99 * MM_PER_INCH, 16).toString())
        assertEquals("16", LengthFormatter.inchParts(15.97 * MM_PER_INCH, 16).toString())
        assertEquals("15 15/16", LengthFormatter.inchParts(15.94 * MM_PER_INCH, 16).toString())
        assertEquals("3", LengthFormatter.inchParts(2.96875 * MM_PER_INCH, 16).toString())
        assertEquals("2 31/32", LengthFormatter.inchParts(2.96875 * MM_PER_INCH, 32).toString())
        assertEquals("1/2", LengthFormatter.inchParts(0.5 * MM_PER_INCH, 32).toString())
        assertEquals("0", LengthFormatter.inchParts(0.03 * MM_PER_INCH, 16).toString())
        assertEquals("1/32", LengthFormatter.inchParts(0.03 * MM_PER_INCH, 32).toString())
        val p = LengthFormatter.inchParts(28.2843 * MM_PER_INCH, 16)
        assertEquals(28L, p.whole); assertEquals(5L, p.numerator); assertEquals(16L, p.denominator)
    }

    @Test
    fun metricAndImperialRoundTripExactly() {
        for (u in 0..48 * 32) {
            val inch = u / 32.0
            val text = LengthFormatter.inchParts(inch * MM_PER_INCH, 32).toString()
            val back = (LengthParser.parse(text, UnitSystem.INCH) as ParsedLength.Ok).mm
            assertEquals("round trip of $text", inch * MM_PER_INCH, back, 1e-9)
        }
        assertEquals("424 mm", LengthFormatter.millimetres(424.26, 1.0))
        assertEquals("424.5 mm", LengthFormatter.millimetres(424.26, 0.5))
        assertEquals("425 mm", LengthFormatter.millimetres(424.76, 0.5))
        assertEquals("1 in", LengthFormatter.inches(25.4, 16))
        assertEquals("16 11/16 in", LengthFormatter.inches(424.26, 16))
    }
}
