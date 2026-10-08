package com.devrinth.launchpad.search.plugins

import android.content.Context
import androidx.appcompat.content.res.AppCompatResources
import com.devrinth.launchpad.R
import com.devrinth.launchpad.adapters.ResultAdapter
import com.devrinth.launchpad.search.SearchPlugin
import com.notkamui.keval.Keval

class CalculatorPlugin(mContext: Context) : SearchPlugin(mContext) {

    override var ID = "calculator"
    override var PRIORITY = 1

    /**
     * Friendly math shorthands, rewritten to plain Keval before evaluation:
     * - Python-style `**` becomes `^` (`2**3` == `2^3` == 8).
     * - Scientific notation becomes explicit powers (`1e7` -> `(1*10^7)`).
     *   Keval would otherwise read the `e` as Euler's number.
     * - Magnitude words expand (`1mil`, `1M`, `1million`, `1 million` all
     *   become `(1*1000000)`; `k`/`b`/`t` families likewise), so
     *   `log10(1mil)` == 6.
     * - A bare `e` (Euler's number) becomes its value (`2e` evaluates, and
     *   gets an alt row like everything else that rewrites).
     *
     * [displayExpand] is the human-readable sibling: same tokens, but rendered
     * as plain numbers (`1mil * 2` -> `1000000 * 2`) for the result subtitle.
     */
    object CalculatorSyntax {
        data class Normalized(val expression: String, val expanded: Boolean)

        private const val EULER = "2.718281828459045"

        private val SCIENTIFIC =
            Regex("""(?<![\w.])(\d+(?:\.\d+)?)[eE]([+-]?\d+)(?![\w])""")
        private val MAGNITUDE =
            Regex(
                """(\d+(?:\.\d+)?)\s*(hundreds|hundred|thousands|thousand|millions|million|mil|m|billions|billion|bil|b|trillions|trillion|t|quadrillions|quadrillion|quintillions|quintillion)\b""",
                RegexOption.IGNORE_CASE
            )
        private val EULER_TOKEN = Regex("""(?<![A-Za-z_.])e(?![A-Za-z_])""")

        private val MULTIPLIERS = mapOf(
            "hundred" to "100", "hundreds" to "100",
            "k" to "1000", "thousand" to "1000", "thousands" to "1000",
            "m" to "1000000", "mil" to "1000000", "million" to "1000000", "millions" to "1000000",
            "b" to "1000000000", "bil" to "1000000000", "billion" to "1000000000", "billions" to "1000000000",
            "t" to "1000000000000", "trillion" to "1000000000000", "trillions" to "1000000000000",
            "quadrillion" to "1000000000000000", "quadrillions" to "1000000000000000",
            "quintillion" to "1000000000000000000", "quintillions" to "1000000000000000000",
        )

        fun normalize(query: String): Normalized {
            val caret = query.replace("**", "^")
            var expr = caret
            expr = SCIENTIFIC.replace(expr) { m -> "(${m.groupValues[1]}*10^${m.groupValues[2]})" }
            expr = MAGNITUDE.replace(expr) { m ->
                "(${m.groupValues[1]}*${MULTIPLIERS[m.groupValues[2].lowercase()]})"
            }
            expr = EULER_TOKEN.replace(expr, "($EULER)")
            return Normalized(expr, expr != caret)
        }

        fun displayExpand(query: String): String {
            var expr = query
            expr = SCIENTIFIC.replace(expr) { m ->
                java.math.BigDecimal(m.groupValues[1])
                    .scaleByPowerOfTen(m.groupValues[2].toInt())
                    .stripTrailingZeros().toPlainString()
            }
            expr = MAGNITUDE.replace(expr) { m ->
                java.math.BigDecimal(m.groupValues[1])
                    .multiply(java.math.BigDecimal(MULTIPLIERS[m.groupValues[2].lowercase()]))
                    .stripTrailingZeros().toPlainString()
            }
            expr = EULER_TOKEN.replace(expr, EULER)
            return expr
        }
    }

    override fun pluginProcess(query: String) {
        super.pluginProcess(query)

        try {
            val normalized = CalculatorSyntax.normalize(query)
            val icon = AppCompatResources.getDrawable(mContext, R.drawable.baseline_calculate_24)
            val rows = arrayListOf(
                ResultAdapter(
                    Keval.eval(normalized.expression).toString(),
                    CalculatorSyntax.displayExpand(query),
                    icon,
                    null,
                    null
                )
            )
            // Alt row shows the expanded parse, e.g. `1mil * 2` also lists
            // `(1*1000000) * 2` so the rewrite is visible.
            if (normalized.expanded) {
                rows.add(
                    ResultAdapter(
                        normalized.expression,
                        query,
                        icon,
                        null,
                        null
                    )
                )
            }
            pluginResult(rows, query)
        } catch (e: Exception) {
            pluginResult(emptyList(), "")
        }
    }

}