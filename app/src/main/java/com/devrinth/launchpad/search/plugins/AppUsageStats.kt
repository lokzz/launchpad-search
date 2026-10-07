package com.devrinth.launchpad.search.plugins

/**
 * Pure-Kotlin usage statistics for app launch history. No Android
 * dependencies so it can be unit tested on the JVM.
 *
 * History is a map of package name -> list of launch timestamps (epoch millis).
 * All functions take an explicit `nowMillis` so tests are deterministic.
 */
object AppUsageStats {

    const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /** An app is "frequent" if launched this many times in the last 24h. */
    const val FREQUENT_MIN_COUNT = 2
    const val FREQUENT_WINDOW_MILLIS = DAY_MILLIS

    /** Empty-query top hits: strictly more than this many launches in 2 days. */
    const val TOP_HIT_MIN_COUNT_EXCLUSIVE = 3
    const val TOP_HIT_WINDOW_MILLIS = 2 * DAY_MILLIS
    const val TOP_HIT_LIMIT = 8

    /** Entries older than this are dropped on write to bound storage growth. */
    const val PRUNE_OLDER_THAN_MILLIS = 7 * DAY_MILLIS

    /** Max stored timestamps per package. */
    const val MAX_ENTRIES_PER_APP = 50

    fun countSince(hits: List<Long>, sinceMillis: Long): Int {
        return hits.count { it >= sinceMillis }
    }

    fun isFrequent(history: Map<String, List<Long>>, packageName: String, nowMillis: Long): Boolean {
        val hits = history[packageName] ?: return false
        return countSince(hits, nowMillis - FREQUENT_WINDOW_MILLIS) >= FREQUENT_MIN_COUNT
    }

    /**
     * Packages with more than [TOP_HIT_MIN_COUNT_EXCLUSIVE] launches inside the
     * 2-day window, sorted by launch count (desc) then most recent launch (desc).
     */
    fun topHits(
        history: Map<String, List<Long>>,
        nowMillis: Long,
        limit: Int = TOP_HIT_LIMIT,
    ): List<String> {
        val windowStart = nowMillis - TOP_HIT_WINDOW_MILLIS
        return history.mapNotNull { (pkg, hits) ->
            val recent = hits.filter { it >= windowStart }
            if (recent.size > TOP_HIT_MIN_COUNT_EXCLUSIVE) {
                pkg to Pair(recent.size, recent.max())
            } else {
                null
            }
        }.sortedWith(
            compareByDescending<Pair<String, Pair<Int, Long>>> { it.second.first }
                .thenByDescending { it.second.second }
        ).take(limit).map { it.first }
    }

    /** Drop stale entries and cap per-app list size. Returns pruned copy. */
    fun prune(history: Map<String, List<Long>>, nowMillis: Long): Map<String, List<Long>> {
        val cutoff = nowMillis - PRUNE_OLDER_THAN_MILLIS
        return history.mapNotNull { (pkg, hits) ->
            val kept = hits.filter { it >= cutoff }.takeLast(MAX_ENTRIES_PER_APP)
            if (kept.isEmpty()) null else pkg to kept
        }.toMap()
    }
}
