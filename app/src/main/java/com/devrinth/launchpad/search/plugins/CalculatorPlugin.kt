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
     * Python-style `**` is rewritten to Keval's `^` before evaluation, so
     * `2**3` and `2^3` both give 8.
     */
    object CalculatorSyntax {
        fun normalize(query: String): String = query.replace("**", "^")
    }

    override fun pluginProcess(query: String) {
        super.pluginProcess(query)

        try {
            pluginResult(arrayListOf(ResultAdapter(
                    Keval.eval(CalculatorSyntax.normalize(query)).toString(),
                    query,
                    AppCompatResources.getDrawable(mContext, R.drawable.baseline_calculate_24),
                    null,
                    null
                ))
            , query)
        } catch (e: Exception) {
            pluginResult(emptyList(), "")
        }
    }

}