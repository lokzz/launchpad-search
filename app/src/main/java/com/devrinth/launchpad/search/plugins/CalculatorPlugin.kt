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
     * - Magnitude words expand to millions (`1mil`, `1M`, `1million`,
     *   `1 million` all become `(1*1000000)`), so `log10(1mil)` == 6.
     */
    object CalculatorSyntax {
        data class Normalized(val expression: String, val expanded: Boolean)

        private val SCIENTIFIC =
            Regex("""(?<![\w.])(\d+(?:\.\d+)?)[eE]([+-]?\d+)(?![\w])""")
        private val MAGNITUDE =
            Regex("""(\d+(?:\.\d+)?)\s*(millions|million|mil|m)\b""", RegexOption.IGNORE_CASE)

        fun normalize(query: String): Normalized {
            val caret = query.replace("**", "^")
            var expr = caret
            expr = SCIENTIFIC.replace(expr) { m -> "(${m.groupValues[1]}*10^${m.groupValues[2]})" }
            expr = MAGNITUDE.replace(expr) { m -> "(${m.groupValues[1]}*1000000)" }
            return Normalized(expr, expr != caret)
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
                    query,
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