package com.devrinth.launchpad

import com.devrinth.launchpad.search.plugins.CalculatorPlugin.CalculatorSyntax
import com.notkamui.keval.Keval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorSyntaxTest {

    @Test
    fun doubleStarBecomesCaret() {
        assertEquals("2^3", CalculatorSyntax.normalize("2**3").expression)
        assertEquals("2^3", CalculatorSyntax.normalize("2^3").expression)
    }

    @Test
    fun doubleStarEvaluatesAsPower() {
        assertEquals(
            Keval.eval("2^3").toString(),
            Keval.eval(CalculatorSyntax.normalize("2**3").expression).toString()
        )
        assertEquals("8.0", Keval.eval(CalculatorSyntax.normalize("2**3").expression).toString())
    }

    @Test
    fun doubleStarIsNotAnExpansion() {
        assertFalse(CalculatorSyntax.normalize("2**3").expanded)
        assertFalse(CalculatorSyntax.normalize("2+2").expanded)
    }

    @Test
    fun scientificNotationExpands() {
        val n = CalculatorSyntax.normalize("1e7 / 42")
        assertEquals("(1*10^7) / 42", n.expression)
        assertTrue(n.expanded)
    }

    @Test
    fun scientificNotationEvaluates() {
        val result = Keval.eval(CalculatorSyntax.normalize("1e7 / 42").expression)
        assertEquals(10000000.0 / 42, result, 1e-6)
    }

    @Test
    fun scientificVariants() {
        assertEquals("(2*10^5)", CalculatorSyntax.normalize("2E5").expression)
        assertEquals("(2.5*10^-3)", CalculatorSyntax.normalize("2.5e-3").expression)
        assertEquals("(3*10^+4)", CalculatorSyntax.normalize("3e+4").expression)
        assertEquals(0.0025, Keval.eval(CalculatorSyntax.normalize("2.5e-3").expression), 1e-9)
    }

    @Test
    fun eulerRewritesToValueWithAlt() {
        // Bare `e` is Euler's number: expand it explicitly so the alt row
        // shows the parse. Same value Keval would compute implicitly.
        assertEquals("(2.718281828459045)", CalculatorSyntax.normalize("e").expression)
        assertTrue(CalculatorSyntax.normalize("e").expanded)
        val n = CalculatorSyntax.normalize("2e")
        assertEquals("2*(2.718281828459045)", n.expression)
        assertTrue(n.expanded)
        assertEquals(Math.E * 2, Keval.eval(n.expression), 1e-12)
    }

    @Test
    fun eulerBareEvaluatesWithAltRow() {
        // `e * 2` form: Euler rewrites to its value and flags expansion.
        val n = CalculatorSyntax.normalize("e * 2")
        assertEquals("(2.718281828459045) * 2", n.expression)
        assertTrue(n.expanded)
        assertEquals(
            Math.E * 2,
            Keval.eval(n.expression),
            1e-12
        )
    }

    @Test
    fun eulerInsideWordsUntouched() {
        assertFalse(CalculatorSyntax.normalize("ceil(2)").expanded)
        assertEquals("ceil(2)", CalculatorSyntax.normalize("ceil(2)").expression)
    }

    @Test
    fun magnitudeWordsExpand() {
        assertEquals("(1*1000000) * 2", CalculatorSyntax.normalize("1mil * 2").expression)
        assertEquals("(1*1000000) * 2", CalculatorSyntax.normalize("1M * 2").expression)
        assertEquals("(1*1000000) * 2", CalculatorSyntax.normalize("1million * 2").expression)
        assertEquals("(1*1000000) * 2", CalculatorSyntax.normalize("1 million * 2").expression)
        assertEquals("(1.5*1000000)", CalculatorSyntax.normalize("1.5mil").expression)
    }

    @Test
    fun thousandBillionTrillionExpand() {
        assertEquals("(2*1000)", CalculatorSyntax.normalize("2k").expression)
        assertEquals("(1*1000)", CalculatorSyntax.normalize("1 thousand").expression)
        assertEquals("(3*1000000000)", CalculatorSyntax.normalize("3b").expression)
        assertEquals("(3*1000000000)", CalculatorSyntax.normalize("3billion").expression)
        assertEquals("(1*1000000000000)", CalculatorSyntax.normalize("1T").expression)
        assertEquals("(1*1000000000000)", CalculatorSyntax.normalize("1 trillion").expression)
        assertEquals(
            2000.0,
            Keval.eval(CalculatorSyntax.normalize("2k").expression),
            1e-6
        )
        assertEquals(
            1e12,
            Keval.eval(CalculatorSyntax.normalize("1T").expression),
            1.0
        )
    }

    @Test
    fun magnitudeEvaluates() {
        assertEquals(
            2000000.0,
            Keval.eval(CalculatorSyntax.normalize("1mil * 2").expression),
            1e-6
        )
    }

    @Test
    fun log10OfMillion() {
        val n = CalculatorSyntax.normalize("log10(1mil)")
        assertEquals("log10((1*1000000))", n.expression)
        assertEquals(6.0, Keval.eval(n.expression), 1e-9)
    }

    @Test
    fun lookalikesUntouched() {
        // Units/words that merely start like magnitudes must not rewrite.
        assertFalse(CalculatorSyntax.normalize("10mm").expanded)
        assertEquals("10mm", CalculatorSyntax.normalize("10mm").expression)
        assertFalse(CalculatorSyntax.normalize("1mile").expanded)
    }

    @Test
    fun fullLadderEnds() {
        assertEquals("(5*100)", CalculatorSyntax.normalize("5 hundred").expression)
        assertEquals(
            "(2*1000000000000000)",
            CalculatorSyntax.normalize("2 quadrillion").expression
        )
        assertEquals(
            "(1*1000000000000000000)",
            CalculatorSyntax.normalize("1quintillion").expression
        )
        assertEquals("500", CalculatorSyntax.displayExpand("5 hundred"))
        assertEquals(
            "2000000000000000",
            CalculatorSyntax.displayExpand("2 quadrillion")
        )
        assertEquals(
            5e2,
            Keval.eval(CalculatorSyntax.normalize("5 hundred").expression),
            1e-6
        )
    }

    @Test
    fun displayExpandDecompressesToFullNumbers() {
        assertEquals("1000000 * 2", CalculatorSyntax.displayExpand("1mil * 2"))
        assertEquals("1000000", CalculatorSyntax.displayExpand("1e6"))
        assertEquals("10000000", CalculatorSyntax.displayExpand("1e7"))
        assertEquals("0.0025", CalculatorSyntax.displayExpand("2.5e-3"))
        assertEquals("2000", CalculatorSyntax.displayExpand("2k"))
        assertEquals("1000000000000", CalculatorSyntax.displayExpand("1T"))
        // Plain queries pass through untouched.
        assertEquals("2+2", CalculatorSyntax.displayExpand("2+2"))
        assertEquals("2^3", CalculatorSyntax.displayExpand("2^3"))
    }
}
