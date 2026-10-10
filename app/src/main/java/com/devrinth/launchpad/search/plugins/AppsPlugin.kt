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
    private var appLaunchHistory: AppLaunchHistory? = null

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
        appLaunchHistory = AppLaunchHistory(mContext.applicationContext)
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
        if (!INIT) {
            searchJob?.cancel()
            pluginResult(emptyList(), "")
            return
        }
        // Empty query: show top hits instead of nothing. SearchManager only
        // routes the empty query to this plugin.
        if (query.isEmpty()) {
            searchJob?.cancel()
            val currentQuery = query
            searchJob = scope.launch {
                pluginResult(topHits(), currentQuery)
            }
            return
        }
        // Single-char queries search too: the cache makes a full scan cheap,
        // so the first keystroke already shows matches instead of nothing.
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
        appLaunchHistory = null
        synchronized(this) {
            cachedApps = emptyList()
            cacheReady = false
        }
        super.pluginUnInit()
    }

    /**
     * Top hits for the empty query: apps launched more than
     * [AppUsageStats.TOP_HIT_MIN_COUNT_EXCLUSIVE] times in the last 2 days,
     * most used first.
     */
    private suspend fun topHits(): List<ResultAdapter> {
        return withContext(Dispatchers.Default) {
            ensureCache()
            val history = try {
                appLaunchHistory?.snapshot() ?: emptyMap()
            } catch (_: Exception) {
                emptyMap<String, List<Long>>()
            }
            val topPackages = AppUsageStats.topHits(history, System.currentTimeMillis())
            if (topPackages.isEmpty()) return@withContext emptyList()
            val byPackage = cachedApps.associateBy { it.packageName }
            topPackages.mapNotNull { pkg ->
                ensureActive()
                val app = byPackage[pkg] ?: return@mapNotNull null
                try {
                    buildResult(app)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    private fun buildResult(app: CachedApp): ResultAdapter {
        return ResultAdapter(
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
            null,
            ID,
        )
    }

    private suspend fun filterApps(query: String): List<ResultAdapter> {
        return withContext(Dispatchers.Default) {
            ensureCache()
            val q = query.lowercase().trim()
            if (q.isEmpty()) return@withContext emptyList()

            val history = try {
                appLaunchHistory?.snapshot() ?: emptyMap()
            } catch (_: Exception) {
                emptyMap<String, List<Long>>()
            }
            val now = System.currentTimeMillis()

            // Cheap pass first: package-name matches cost nothing, label
            // resolution already happened at init. Rank exact > prefix >
            // substring > fuzzy > package-only so "Google" beats
            // "Google Maps" beats "Files by Google" beats package-only hits.
            // Tiering: an exact display-name match always takes the very top,
            // even above frequent apps; otherwise apps launched >=2x in the
            // last 24h jump ahead of the pack.
            val ranked = cachedApps.mapNotNull { app ->
                ensureActive()
                val score = AppSearchRanker.score(q, app.labelLower, app.packageLower)
                    ?: return@mapNotNull null
                val frequent =
                    AppUsageStats.isFrequent(history, app.packageName, now)
                Triple(app, score, frequent)
            }.sortedWith(
                compareBy(
                    { AppSearchRanker.tier(it.second, it.third) },
                    { it.second },
                    { it.first.labelLower }
                )
            )

            // Expensive pass last, and only for matches: icon + launch intent.
            // A null icon is fine -- the adapter shows its placeholder.
            ranked.mapNotNull { (app, _, _) ->
                ensureActive()
                try {
                    buildResult(app)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
