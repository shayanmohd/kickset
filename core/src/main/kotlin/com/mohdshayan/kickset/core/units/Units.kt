package com.mohdshayan.kickset.core.units

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** The two unit systems the whole app switches between. Every length inside the app is a Double in millimetres. */
enum class UnitSystem { MM, INCH }

const val MM_PER_INCH = 25.4

/** Result of reading what a fitter typed into a length field. */
sealed interface ParsedLength {
    data class Ok(val mm: Double, val readAs: UnitSystem) : ParsedLength
    data object Empty : ParsedLength
    data object Invalid : ParsedLength
}

/**
 * Reads lengths the way they are written on a shop floor:
 * "12 5/16", "12-5/16", "5/16", "1' 0 5/16", "1'-2 1/2\"", "12.3125", "313", "313 mm", "12 in".
 *
 * A foot mark, an inch mark, "in" or a fraction always means inches. "mm" always means millimetres.
 * A bare decimal is read in the unit system the app is set to.
 */
object LengthParser {
    private const val MAX_MM = 1_000_000.0 // one kilometre; anything longer is a typo

    private val feetRe = Regex("""^(\d+(?:\.\d+)?)\s*'\s*-?\s*(.*)$""")
    private val mixedRe = Regex("""^(\d+)(?:\s+|\s*-\s*)(\d+)\s*/\s*(\d+)$""")
    private val fracRe = Regex("""^(\d+)\s*/\s*(\d+)$""")
    private val decRe = Regex("""^(\d+(?:\.\d*)?|\.\d+)$""")

    fun parse(raw: String, system: UnitSystem): ParsedLength {
        var s = raw.trim().lowercase(Locale.ROOT).replace('’', '\'').replace('”', '"')
        if (s.isEmpty()) return ParsedLength.Empty
        var forced: UnitSystem? = null
        when {
            s.endsWith("mm") -> { forced = UnitSystem.MM; s = s.removeSuffix("mm").trim() }
            s.endsWith("in") -> { forced = UnitSystem.INCH; s = s.removeSuffix("in").trim() }
            s.endsWith("\"") -> { forced = UnitSystem.INCH; s = s.removeSuffix("\"").trim() }
        }
        if (s.isEmpty()) return ParsedLength.Invalid

        val feet = feetRe.matchEntire(s)
        if (feet != null) {
            if (forced == UnitSystem.MM) return ParsedLength.Invalid
            val ft = feet.groupValues[1].toDoubleOrNull() ?: return ParsedLength.Invalid
            val rest = feet.groupValues[2].trim()
            val inches = if (rest.isEmpty()) 0.0 else (inchBody(rest) ?: return ParsedLength.Invalid)
            return ok((ft * 12.0 + inches) * MM_PER_INCH, UnitSystem.INCH)
        }
        if (s.contains('\'')) return ParsedLength.Invalid

        if (s.contains('/')) {
            if (forced == UnitSystem.MM) return ParsedLength.Invalid
            val inches = inchBody(s) ?: return ParsedLength.Invalid
            return ok(inches * MM_PER_INCH, UnitSystem.INCH)
        }
        if (decRe.matchEntire(s) == null) return ParsedLength.Invalid
        val value = s.toDoubleOrNull() ?: return ParsedLength.Invalid
        val unit = forced ?: system
        return ok(if (unit == UnitSystem.INCH) value * MM_PER_INCH else value, unit)
    }

    /** Inches from "12", "12.5", "5/16", "12 5/16" or "12-5/16"; null when malformed. */
    private fun inchBody(s: String): Double? {
        mixedRe.matchEntire(s)?.let { m ->
            val whole = m.groupValues[1].toLong()
            val num = m.groupValues[2].toLong()
            val den = m.groupValues[3].toLong()
            if (den == 0L || num >= den) return null
            return whole + num.toDouble() / den
        }
        fracRe.matchEntire(s)?.let { m ->
            val num = m.groupValues[1].toLong()
            val den = m.groupValues[2].toLong()
            if (den == 0L) return null
            return num.toDouble() / den
        }
        if (decRe.matchEntire(s) != null) return s.toDoubleOrNull()
        return null
    }

    private fun ok(mm: Double, readAs: UnitSystem): ParsedLength =
        if (mm.isFinite() && mm <= MAX_MM) ParsedLength.Ok(mm, readAs) else ParsedLength.Invalid
}

/** Inches split for display: whole, then a reduced fraction numerator/denominator (0 when none). */
data class InchParts(val negative: Boolean, val whole: Long, val numerator: Long, val denominator: Long) {
    val hasFraction: Boolean get() = numerator != 0L
    override fun toString(): String {
        val sign = if (negative) "-" else ""
        return when {
            !hasFraction -> "$sign$whole"
            whole == 0L -> "$sign$numerator/$denominator"
            else -> "$sign$whole $numerator/$denominator"
        }
    }
}

object LengthFormatter {
    /** Rounds to the nearest 1/denom inch first, so 15.999 in becomes 16 and never "15 16/16". */
    fun inchParts(mm: Double, denom: Int): InchParts {
        require(denom > 0 && (denom and (denom - 1)) == 0) { "denominator must be a power of two" }
        val inches = mm / MM_PER_INCH
        val units = (abs(inches) * denom).roundToLong()
        var num = units % denom
        var den = denom.toLong()
        while (num != 0L && num % 2L == 0L) { num /= 2; den /= 2 }
        return InchParts(inches < 0 && units != 0L, units / denom, num, if (num == 0L) 1 else den)
    }

    fun inches(mm: Double, denom: Int): String = "${inchParts(mm, denom)} in"

    /** Millimetres rounded to a step of 1.0 or 0.5. */
    fun millimetres(mm: Double, step: Double): String {
        val q = roundTo(mm, step)
        return if (step >= 1.0 || q == Math.floor(q)) "${String.format(Locale.ROOT, "%.0f", q)} mm"
        else "${String.format(Locale.ROOT, "%.1f", q)} mm"
    }

    /** One decimal millimetres, used in working lines so the arithmetic can be checked. */
    fun millimetresExact(mm: Double): String = "${String.format(Locale.ROOT, "%.1f", mm)} mm"

    fun roundTo(value: Double, step: Double): Double = (value / step).roundToLong() * step

    fun format(mm: Double, system: UnitSystem, inchDenom: Int, mmStep: Double): String =
        if (system == UnitSystem.INCH) inches(mm, inchDenom) else millimetres(mm, mmStep)

    /** The same length in the other unit, for the line under every answer. */
    fun other(mm: Double, system: UnitSystem, inchDenom: Int, mmStep: Double): String =
        if (system == UnitSystem.INCH) millimetres(mm, mmStep) else inches(mm, inchDenom)

    fun decimal(value: Double, places: Int): String = String.format(Locale.ROOT, "%.${places}f", value)

    /** The length a fraction at [denom] stands for, so a line can be worked from what it prints. */
    fun inchValue(mm: Double, denom: Int): Double {
        val p = inchParts(mm, denom)
        val inches = p.whole + if (p.hasFraction) p.numerator.toDouble() / p.denominator else 0.0
        return (if (p.negative) -inches else inches) * MM_PER_INCH
    }

    /** [value] rounded the same way [decimal] writes it, so text and number never disagree. */
    fun round(value: Double, places: Int): Double {
        val f = Math.pow(10.0, places.toDouble())
        val q = (abs(value) * f).roundToLong() / f
        return if (value < 0) -q else q
    }

    /** [value] to [places], without the trailing zeros that would claim precision it does not have. */
    fun trim(value: Double, places: Int): String {
        val t = decimal(value, places)
        return if (t.contains('.')) t.trimEnd('0').trimEnd('.') else t
    }

    /**
     * A multiplier written to just enough places that the product a fitter works out on paper is the
     * answer printed beside it. Four places is the table value fitters know, and all but a long
     * multiplier or a very long pipe stops there.
     */
    fun multiplier(value: Double, reads: (Double) -> Boolean): String = multiplierOrNull(value, reads) ?: decimal(value, 4)

    /** The same, but null when no length of multiplier makes the line read true. */
    fun multiplierOrNull(value: Double, reads: (Double) -> Boolean): String? {
        for (places in 4..8) {
            val text = decimal(value, places)
            if (reads(text.toDouble())) return text
        }
        return null
    }
}

/** Two lengths that are the same number, allowing only for the last bit of a Double. */
fun sameLength(a: Double, b: Double): Boolean = abs(a - b) <= 1e-9 + 1e-11 * abs(b)

/**
 * One number as a working line prints it: the text, the length that text stands for, and how fine it
 * is written. Every working line is built out of these and never out of the exact quantity behind
 * them, so a line read straight off the screen gives the figure printed beside it.
 */
data class Figure(val text: String, val value: Double, val level: Int)

/** How lengths should be written for one user: the unit system and both precisions. */
data class UnitPrefs(val system: UnitSystem = UnitSystem.MM, val inchDenom: Int = 16, val mmStep: Double = 1.0) {
    fun primary(mm: Double) = LengthFormatter.format(mm, system, inchDenom, mmStep)
    fun secondary(mm: Double) = LengthFormatter.other(mm, system, inchDenom, mmStep)
    /**
     * Working-line form for an answer. Inches use the fitter's own denominator, so the last line of a
     * subtraction reads exactly the same fraction as the answer above it; millimetres carry one decimal,
     * which reads as more precision rather than as a different number.
     */
    fun working(mm: Double) = if (system == UnitSystem.INCH) LengthFormatter.inches(mm, inchDenom) else LengthFormatter.millimetresExact(mm)

    /** How many ways there are to write one length, from the fitter's own precision down to exact. */
    private val levels: Int get() = if (system == UnitSystem.INCH) 13 else 6

    /**
     * The ways of writing one length, coarsest first: the fitter's own precision, then finer fractions,
     * then decimal inches, then millimetres. A 45 LR takeout of 3 3/4 in is exactly 95.25 mm, and writing
     * it as 95.3 mm is what used to leave the subtraction beside it reading one tenth out.
     */
    private fun write(mm: Double, level: Int): Figure {
        // Level -1 is the fitter's own headline precision, the one place the working can fall back to
        // when nothing finer can be written without reading as a different answer.
        if (level < 0) return Figure(primary(mm), if (system == UnitSystem.INCH) LengthFormatter.inchValue(mm, inchDenom) else LengthFormatter.roundTo(mm, mmStep), -1)
        if (system == UnitSystem.INCH) {
            if (level < 3) {
                val den = inchDenom shl level
                return Figure(LengthFormatter.inches(mm, den), LengthFormatter.inchValue(mm, den), level)
            }
            if (level < 7) {
                val v = LengthFormatter.round(mm / MM_PER_INCH, level)
                return Figure("${LengthFormatter.trim(v, level)} in", v * MM_PER_INCH, level)
            }
        }
        val places = if (system == UnitSystem.INCH) level - 6 else level + 1
        val v = LengthFormatter.round(mm, places)
        return Figure(if (places == 1) LengthFormatter.millimetresExact(v) else "${LengthFormatter.trim(v, places)} mm", v, level)
    }

    /** [mm] written as finely as [level] says, for checking one line against the figure it prints. */
    fun at(mm: Double, level: Int): Figure = write(mm, level)

    /** The coarsest way of writing [mm] that [reads] true, or the fitter's own precision when none does. */
    fun figure(mm: Double, reads: (Figure) -> Boolean): Figure {
        for (l in 0 until levels) {
            val f = write(mm, l)
            if (reads(f)) return f
        }
        return write(mm, 0)
    }

    /** A quantity written exactly, so the line it sits in can be checked digit for digit. */
    fun exact(mm: Double): Figure = figure(mm) { sameLength(it.value, mm) }

    /**
     * An answer as the working prints it: written finely enough that rounding it to the fitter's own
     * precision gives the answer printed above the working, and finely enough that [reads] true. The
     * working may carry more places than the answer, but it must never round to a different answer.
     */
    fun answer(mm: Double, reads: (Figure) -> Boolean = { true }): Figure {
        for (l in 0 until levels) {
            val f = write(mm, l)
            if (primary(f.value) == primary(mm) && reads(f)) return f
        }
        // Nothing finer reads back as the answer above it, which happens when the exact value sits on
        // the half and the answer rounds one way. Then the working prints that answer itself.
        return write(mm, -1)
    }

    /**
     * One "a x factor = answer" line: the answer, and the multiplier written to however many places
     * it takes for a fitter multiplying what he sees to land on what is printed.
     */
    fun product(mm: Double, operand: Double, factor: Double): Pair<Figure, String> {
        val f = answer(mm) { fig -> LengthFormatter.multiplierOrNull(factor) { at(operand * it, fig.level).text == fig.text } != null }
        return f to LengthFormatter.multiplier(factor) { at(operand * it, f.level).text == f.text }
    }
}
