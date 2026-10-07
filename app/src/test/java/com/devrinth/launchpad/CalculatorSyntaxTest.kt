package com.devrinth.launchpad

import com.devrinth.launchpad.search.plugins.CalculatorPlugin.CalculatorSyntax
import com.notkamui.keval.Keval
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorSyntaxTest {

    @Test
    fun doubleStarBecomesCaret() {
        assertEquals("2^3", CalculatorSyntax.normalize("2**3"))
        assertEquals("2^3", CalculatorSyntax.normalize("2^3"))
    }

    @Test
    fun doubleStarEvaluatesAsPower() {
        assertEquals(
            Keval.eval("2^3").toString(),
            Keval.eval(CalculatorSyntax.normalize("2**3")).toString()
        )
        assertEquals("8.0", Keval.eval(CalculatorSyntax.normalize("2**3")).toString())
    }
}
