package com.devrinth.launchpad.search.plugins

/**
 * Simple single-variable equation solver. Pure Kotlin: the evaluator is
 * injected, so this unit-tests on the JVM with Keval (or a fake).
 *
 * Strategy: exact linear probe first (`2x = 1` -> `x = 0.5`), then numeric
 * fallback (multi-seed Newton with central differences, plus bisection over
 * sign changes found while seeding) for the merely simple nonlinear cases
 * (`x^2 = 2`, `sin(x) = 1`, `(2/x) = 2`). Anything unverifiable comes back
 * null instead of a wrong answer.
 */
object EquationSolver {

    private val VAR = Regex("""(?<![A-Za-z_])x(?![A-Za-z_])""")
    private const val MAX_ABS_X = 1e9
    private const val NEWTON_STEPS = 100
    private const val BISECT_STEPS = 200

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
            // f(t) = LHS(t) - RHS(t). Linear exact solve first, verified so
            // nonlinear input falls through to the numeric stage below.
            fun f(t: Double): Double {
                val lit = "($t)"
                return evaluate(VAR.replace(lhs, lit)) - evaluate(VAR.replace(rhs, lit))
            }
            // Guarded: poles/NaN (e.g. `2/x` at 0) evaluate to null and are
            // skipped, never thrown.
            fun g(t: Double): Double? {
                return try {
                    f(t).takeIf { it.isFinite() }
                } catch (_: Exception) {
                    null
                }
            }
            val f0 = g(0.0)
            val f1 = g(1.0)
            val tol = 1e-9 * maxOf(1.0, kotlin.math.abs(f0 ?: 0.0), kotlin.math.abs(f1 ?: 0.0))
            if (f0 != null && f1 != null) {
                val slope = f1 - f0
                if (kotlin.math.abs(slope) > tol) {
                    val x = -f0 / slope
                    val fx = g(x)
                    if (fx != null && kotlin.math.abs(fx) <= tol) return x
                } else {
                    // Flat: no solution or infinite solutions -- silent either way.
                    return null
                }
            }
            numericSolve(::g, tol)
        } catch (_: Exception) {
            null
        }
    }

    private val SEEDS = listOf(
        1.0, -1.0, 2.0, -2.0, 0.5, -0.5, 5.0, -5.0, 10.0, -10.0, 0.0
    )

    /**
     * Newton with numeric derivatives from several seeds (catches touch roots
     * like `sin(x) = 1` that have no sign change), falling back to bisection
     * over any sign change found while seeding. Returns null when nothing
     * verifies.
     */
    private fun numericSolve(g: (Double) -> Double?, tol: Double): Double? {
        val probed = SEEDS.map { it to g(it) }
        // Zero at 3+ spread-out seeds means flat/identity (e.g. `1/x = 1/x`),
        // where any "answer" would be wrong -- stay silent.
        if (probed.count { it.second != null && kotlin.math.abs(it.second) <= tol } >= 3) {
            return null
        }
        for ((seed, fseed) in probed) {
            if (fseed != null && kotlin.math.abs(fseed) <= tol) return seed
            newton(g, seed, tol)?.let { return it }
        }
        val finite = probed.filter { it.second != null }.sortedBy { it.first }
        for (i in 0 until finite.size - 1) {
            val (a, fa) = finite[i]
            val (b, fb) = finite[i + 1]
            if (fa!! * fb!! < 0) {
                bisect(g, a, b, tol)?.let { return it }
            }
        }
        return null
    }

    private fun newton(g: (Double) -> Double?, x0: Double, tol: Double): Double? {
        var x = x0
        repeat(NEWTON_STEPS) {
            val fx = g(x) ?: return null
            if (kotlin.math.abs(fx) <= tol) return x
            val h = 1e-6 * maxOf(1.0, kotlin.math.abs(x))
            val fp = g(x + h) ?: return null
            val fm = g(x - h) ?: return null
            val d = (fp - fm) / (2 * h)
            if (!d.isFinite() || kotlin.math.abs(d) < 1e-12) return null
            x = x - fx / d
            if (!x.isFinite() || kotlin.math.abs(x) > MAX_ABS_X) return null
        }
        val fx = g(x)
        return if (fx != null && kotlin.math.abs(fx) <= tol) x else null
    }

    private fun bisect(g: (Double) -> Double?, a: Double, b: Double, tol: Double): Double? {
        var lo = a
        var hi = b
        var flo = g(lo) ?: return null
        repeat(BISECT_STEPS) {
            val mid = (lo + hi) / 2
            if (!mid.isFinite()) return null
            val fmid = g(mid) ?: return null
            if (kotlin.math.abs(fmid) <= tol) return mid
            if (flo * fmid < 0) {
                hi = mid
            } else {
                lo = mid
                flo = fmid
            }
        }
        return null
    }
}
