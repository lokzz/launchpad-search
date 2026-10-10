package com.devrinth.launchpad

import com.devrinth.launchpad.search.plugins.AppSearchRanker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppSearchRankerTest {

    private data class App(val label: String, val pkg: String)

    private fun ranked(query: String, apps: List<App>): List<String> {
        return apps.mapNotNull { app ->
            val score = AppSearchRanker.score(query, app.label, app.pkg)
                ?: return@mapNotNull null
            app to score
        }.sortedWith(compareBy({ it.second }, { it.first.label.lowercase() }))
            .map { it.first.label }
    }

    @Test
    fun google_exactAppRanksFirst() {
        val apps = listOf(
            App("Google Play Services", "com.google.android.gms"),
            App("Files by Google", "com.google.android.apps.nbu.files"),
            App("Google Maps", "com.google.android.apps.maps"),
            App("Google", "com.google.android.googlequicksearchbox"),
            App("Gboard", "com.google.android.inputmethod.latin"),
            App("Calculator", "com.google.android.calculator"),
        )

        val result = ranked("Google", apps)

        // Exact label match first, then prefix, then substring, package-only last.
        assertEquals(
            listOf(
                "Google",
                "Google Maps",
                "Google Play Services",
                "Files by Google",
                "Calculator",
                "Gboard"
            ),
            result
        )
    }

    @Test
    fun packageOnlyMatchIncludedButLast() {
        // "Gboard" label has nothing to do with "google", only its package does.
        val score = AppSearchRanker.score(
            "google", "Gboard", "com.google.android.inputmethod.latin"
        )
        assertEquals(4, score)
    }

    @Test
    fun unrelatedAppDoesNotMatch() {
        assertNull(AppSearchRanker.score("google", "Calculator", "com.android.calculator"))
    }

    @Test
    fun prefixBeatsSubstringBeatsFuzzy() {
        assertEquals(
            1,
            AppSearchRanker.score("goo", "Google Maps", "com.google.android.apps.maps")
        )
        assertEquals(
            2,
            AppSearchRanker.score("goo", "Files by Google", "com.google.android.apps.nbu.files")
        )
    }

    @Test
    fun exactMatchTierBeatsFrequent() {
        // Exact display-name match wins no matter what: tier 0 with or
        // without usage, ahead of any frequent-but-inexact match.
        assertEquals(0, AppSearchRanker.tier(0, true))
        assertEquals(0, AppSearchRanker.tier(0, false))
        assertEquals(1, AppSearchRanker.tier(1, true))
        assertEquals(1, AppSearchRanker.tier(4, true))
        assertEquals(2, AppSearchRanker.tier(1, false))
        assertEquals(2, AppSearchRanker.tier(4, false))
    }
}
