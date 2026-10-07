package com.devrinth.launchpad.search.plugins

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists app launch timestamps so AppsPlugin can rank frequent apps first
 * and show top hits on an empty query.
 *
 * Storage: one SharedPreferences file, one key per package, value is a
 * comma-separated list of epoch-millis timestamps. Pruned on every write.
 */
class AppLaunchHistory(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordLaunch(packageName: String, nowMillis: Long = System.currentTimeMillis()) {
        val current = readAll().toMutableMap()
        val updated = (current[packageName] ?: emptyList()) + nowMillis
        val pruned = AppUsageStats.prune(mapOf(packageName to updated), nowMillis)
        val kept = pruned[packageName] ?: emptyList()
        prefs.edit().putString(packageName, kept.joinToString(",")).apply()
    }

    /** Full snapshot: package name -> launch timestamps. */
    fun snapshot(): Map<String, List<Long>> = readAll()

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun readAll(): Map<String, List<Long>> {
        return prefs.all.mapNotNull { (key, value) ->
            val raw = value as? String ?: return@mapNotNull null
            if (raw.isEmpty()) return@mapNotNull null
            val hits = raw.split(",").mapNotNull { it.toLongOrNull() }
            if (hits.isEmpty()) null else key to hits
        }.toMap()
    }

    companion object {
        private const val PREFS_NAME = "app_launch_history"
    }
}
