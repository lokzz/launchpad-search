package com.devrinth.launchpad.search.plugins

/**
 * Simple single-variable equation solver. Pure Kotlin: the evaluator is
 * injected, so this unit-tests on the JVM with Keval (or a fake).
 *
 * Scope is deliberately narrow -- linear equations in `x` only
 * (`2x = 1` -> `x = 0.5`). The two-probe solve is exact for linear input,
 * and the verify gate rejects anything else (e.g. `x^2 = 2` comes back
 * null instead of a wrong answer), leaving room for a numeric fallback later.
 */
object EquationSolver {

    private val VAR = Regex("""(?<![A-Za-z_])x(?![A-Za-z_])""")

    /** True for exactly one bare `=` (not `==`, `!=`, `>=`, `<=`). */
    fun isEquation(query: String): Boolean {
        if ('=' !in query) return false
        var singles = 0
        var i = 0
        while (i < query.length) {
            if (query[i] == '=') {
                val prev = if (i > 0) query[i - 1] else ' '
                val next = if (i + 1 < query.length) query[i + 1] else ' '
                if (prev == '=' || next == '=' || prev == '!' || prev == '>' || prev == '<') {
                    return false
                }
                singles++
            }
            i++
        }
        return singles == 1
    }

    /**
     * Solves `query`, or null when it isn't a (linear) equation in `x`.
     * [evaluate] turns an expression string into a Double, throwing on error.
     */
    fun solve(query: String, evaluate: (String) -> Double): Double? {
        if (!isEquation(query)) return null
        val sides = query.split('=', limit = 2)
        val lhs = sides[0].trim()
        val rhs = sides[1].trim()
        if (lhs.isEmpty() || rhs.isEmpty()) return null
        // Candidate body must mention x; anything else (e.g. `2 + 2 = 4`)
        // is out of scope.
        if (!VAR.containsMatchIn(lhs) && !VAR.containsMatchIn(rhs)) return null

        return try {
            // f(t) = LHS(t) - RHS(t). Linear exact solve, then verify so
            // nonlinear input is rejected instead of mis-solved.
            fun f(t: Double): Double {
                val lit = "($t)"
                return evaluate(VAR.replace(lhs, lit)) - evaluate(VAR.replace(rhs, lit))
            }
            val f0 = f(0.0)
            val f1 = f(1.0)
            val slope = f1 - f0
            val tol = 1e-9 * maxOf(1.0, kotlin.math.abs(f0), kotlin.math.abs(f1))
            if (kotlin.math.abs(slope) <= tol) return null // flat: none or infinite
            val x = -f0 / slope
            if (kotlin.math.abs(f(x)) <= tol) x else null
        } catch (_: Exception) {
            null
        }
    }
}
