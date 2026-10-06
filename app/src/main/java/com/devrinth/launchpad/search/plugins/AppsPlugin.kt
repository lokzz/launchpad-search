package com.devrinth.launchpad.search.plugins

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.devrinth.launchpad.BuildConfig
import com.devrinth.launchpad.adapters.ResultAdapter
import com.devrinth.launchpad.search.SearchPlugin
import com.devrinth.launchpad.utils.IntentUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppsPlugin(mContext: Context) : SearchPlugin(mContext) {

    override var ID = "apps"

    private lateinit var mPackageManager: PackageManager

    /**
     * Labels are resolved once into [cachedApps] on a background thread.
     * Per-keystroke filtering is then pure string matching on the cache --
     * no `loadLabel()` calls, so a package-name (id) match never waits for
     * every app's real name to resolve.
     */
    private var rawApps: List<ResolveInfo> = emptyList()
    private var cachedApps: List<CachedApp> = emptyList()
    @Volatile
    private var cacheReady = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var searchJob: Job? = null

    private data class CachedApp(
        val label: String,
        val labelLower: String,
        val packageName: String,
        val packageLower: String,
        val resolveInfo: ResolveInfo,
    )

    override fun pluginInit() {
        mPackageManager = mContext.packageManager
        try {
            rawApps = mPackageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER), 0
            ).filter { it.activityInfo.packageName != BuildConfig.APPLICATION_ID }
        } catch (_: Exception) {
            rawApps = emptyList()
        }
        // Warm the label cache off the main thread; pluginInit itself runs on
        // Main via SearchManager, so never loadLabel() here.
        scope.launch(Dispatchers.Default) { ensureCache() }
        super.pluginInit()
    }

    private fun ensureCache() {
        if (cacheReady) return
        synchronized(this) {
            if (cacheReady) return
            cachedApps = try {
                rawApps.mapNotNull { ri ->
                    try {
                        val label = ri.loadLabel(mPackageManager)?.toString()?.trim().orEmpty()
                        if (label.isEmpty()) return@mapNotNull null
                        CachedApp(
                            label = label,
                            labelLower = label.lowercase(),
                            packageName = ri.activityInfo.packageName,
                            packageLower = ri.activityInfo.packageName.lowercase(),
                            resolveInfo = ri,
                        )
                    } catch (_: Exception) {
                        null
                    }
                }.sortedBy { it.labelLower }
            } catch (_: Exception) {
                emptyList()
            }
            cacheReady = true
        }
    }

    override fun pluginProcess(query: String) {
        if (!INIT || query.isEmpty() || query.length < 2) {
            searchJob?.cancel()
            pluginResult(emptyList(), "")
            return
        }
        // Cancel the previous keystroke's search so rapid typing can't pile up
        // full-list scans (the old isProcessing flag never actually guarded).
        searchJob?.cancel()
        val currentQuery = query
        searchJob = scope.launch {
            pluginResult(filterApps(currentQuery), currentQuery)
        }
    }

    override fun pluginUnInit() {
        searchJob?.cancel()
        searchJob = null
        synchronized(this) {
            cachedApps = emptyList()
            cacheReady = false
        }
        super.pluginUnInit()
    }

    private suspend fun filterApps(query: String): List<ResultAdapter> {
        return withContext(Dispatchers.Default) {
            ensureCache()
            val q = query.lowercase().trim()
            if (q.isEmpty()) return@withContext emptyList()

            // Cheap pass first: package-name matches cost nothing, label
            // resolution already happened at init. Rank exact > prefix >
            // substring > fuzzy > package-only so "Google" beats
            // "Google Maps" beats "Files by Google" beats package-only hits.
            val ranked = cachedApps.mapNotNull { app ->
                ensureActive()
                val score = AppSearchRanker.score(q, app.labelLower, app.packageLower)
                    ?: return@mapNotNull null
                app to score
            }.sortedWith(compareBy({ it.second }, { it.first.labelLower }))

            // Expensive pass last, and only for matches: icon + launch intent.
            // A null icon is fine -- the adapter shows its placeholder.
            ranked.mapNotNull { (app, _) ->
                ensureActive()
                try {
                    ResultAdapter(
                        app.label,
                        app.packageName,
                        try {
                            app.resolveInfo.activityInfo.loadIcon(mPackageManager)
                        } catch (_: Exception) {
                            null
                        },
                        try {
                            IntentUtils.getAppIntent(mPackageManager, app.packageName)
                        } catch (_: Exception) {
                            null
                        },
                        null
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
