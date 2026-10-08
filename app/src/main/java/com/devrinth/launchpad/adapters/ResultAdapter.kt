package com.devrinth.launchpad.adapters

import android.content.Intent
import android.graphics.drawable.Drawable

data class ResultAdapter(
    var value: String,
    var extra: String?,
    var image: Drawable?,
    var action1: Intent?,
    var action2: Intent?,
    /**
     * ID of the plugin that produced this result (e.g. "apps").
     * Used for per-plugin bookkeeping such as app launch history.
     * Null = unknown / not tracked.
     */
    var sourcePlugin: String? = null,
)
