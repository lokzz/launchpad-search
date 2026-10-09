package com.devrinth.launchpad

import com.devrinth.launchpad.search.plugins.EquationSolver
import com.notkamui.keval.Keval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class EquationSolverTest {

    private fun solve(query: String): Double? = EquationSolver.solve(query, Keval::eval)

    @Test
    fun isEquationShape() {
        assertTrue(EquationSolver.isEquation("2x = 1"))
        assertTrue(EquationSolver.isEquation("2x=1"))
        assertFalse(EquationSolver.isEquation("2 + 2"))
        assertFalse(EquationSolver.isEquation("2x == 1"))
        assertFalse(EquationSolver.isEquation("x != 1"))
        assertFalse(EquationSolver.isEquation("x >= 1"))
        assertFalse(EquationSolver.isEquation("x = 1 = 2"))
        // Barely-shaped but unsolvable sides still count as shape;
        // solve() rejects them (covered in degenerateIsSilent).
        assertTrue(EquationSolver.isEquation("x ="))
        assertTrue(EquationSolver.isEquation("= 1"))
    }

    @Test
    fun linearEquations() {
        assertEquals(0.5, solve("2x = 1")!!, 1e-9)
        assertEquals(4.0, solve("x + 3 = 7")!!, 1e-9)
        assertEquals(2.0, solve("5 = 2x + 1")!!, 1e-9)
        assertEquals(-3.0, solve("x = -3")!!, 1e-9)
        assertEquals(2.0, solve("(x + 1) * 2 = 6")!!, 1e-9)
    }

    @Test
    fun degenerateIsSilent() {
        // Infinite solutions.
        assertNull(solve("0x = 0"))
        assertNull(solve("2x + 1 = 2x + 1"))
        assertNull(solve("1/x = 1/x"))
        // No solution.
        assertNull(solve("0x = 1"))
        assertNull(solve("1/x = 0"))
        // No variable at all.
        assertNull(solve("2 + 2 = 4"))
        // Garbage.
        assertNull(solve("x = "))
        assertNull(solve("= 1"))
    }

    @Test
    fun numericFallback() {
        // Touch root with no sign change.
        assertEquals(Math.PI / 2, solve("sin(x) = 1")!!, 1e-6)
        // Pole at the origin; seed 1 hits immediately.
        assertEquals(1.0, solve("(2/x) = 2")!!, 1e-9)
        // Plain quadratic finds the positive root.
        assertEquals(Math.sqrt(2.0), solve("x^2 = 2")!!, 1e-6)
        assertEquals(Math.PI / 2, solve("cos(x) = 0")!!, 1e-6)
        assertEquals(2.0, solve("x^3 = 8")!!, 1e-6)
    }
}
