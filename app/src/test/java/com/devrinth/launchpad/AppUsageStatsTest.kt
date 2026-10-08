package com.devrinth.launchpad

import com.devrinth.launchpad.search.plugins.AppUsageStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUsageStatsTest {

    private val hour = 60L * 60 * 1000
    private val now = 1_700_000_000_000L

    @Test
    fun frequent_requiresTwoHitsInLast24h() {
        val history = mapOf(
            "com.example.a" to listOf(now - hour, now - 2 * hour),
            "com.example.b" to listOf(now - hour),
            "com.example.c" to listOf(now - 25 * hour, now - 26 * hour),
        )
        assertTrue(AppUsageStats.isFrequent(history, "com.example.a", now))
        assertFalse(AppUsageStats.isFrequent(history, "com.example.b", now))
        assertFalse(AppUsageStats.isFrequent(history, "com.example.c", now))
        assertFalse(AppUsageStats.isFrequent(history, "com.example.missing", now))
        assertFalse(AppUsageStats.isFrequent(emptyMap(), "com.example.a", now))
    }

    @Test
    fun topHits_requiresMoreThanThreeHitsInTwoDays() {
        val history = mapOf(
            "com.example.often" to listOf(now - hour, now - 2 * hour, now - 3 * hour, now - 4 * hour),
            "com.example.three" to listOf(now - hour, now - 2 * hour, now - 3 * hour),
            "com.example.stale" to listOf(now - 50 * hour, now - 51 * hour, now - 52 * hour, now - 53 * hour),
        )
        assertEquals(listOf("com.example.often"), AppUsageStats.topHits(history, now))
    }

    @Test
    fun topHits_sortedByCountThenRecency() {
        val history = mapOf(
            "com.example.older" to List(5) { now - (10 + it) * hour },
            "com.example.newer" to List(5) { now - (1 + it) * hour },
            "com.example.most" to List(6) { now - (20 + it) * hour },
        )
        assertEquals(
            listOf("com.example.most", "com.example.newer", "com.example.older"),
            AppUsageStats.topHits(history, now)
        )
    }

    @Test
    fun topHits_respectsLimit() {
        val history = (0 until 20).associate { i ->
            "com.example.app$i" to List(4) { now - (i + it) * hour }
        }
        assertEquals(AppUsageStats.TOP_HIT_LIMIT, AppUsageStats.topHits(history, now).size)
        assertEquals(3, AppUsageStats.topHits(history, now, limit = 3).size)
    }

    @Test
    fun prune_dropsStaleAppsAndCapsSize() {
        val old = now - 8 * AppUsageStats.DAY_MILLIS
        val history = mapOf(
            "com.example.stale" to listOf(old, old + 1),
            "com.example.big" to List(100) { now - it * 1000 },
        )
        val pruned = AppUsageStats.prune(history, now)
        assertFalse(pruned.containsKey("com.example.stale"))
        assertEquals(AppUsageStats.MAX_ENTRIES_PER_APP, pruned["com.example.big"]!!.size)
    }
}
