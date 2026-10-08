package com.devrinth.launchpad.search.plugins

/**
 * Pure-Kotlin ranking for app search results. No Android dependencies so it
 * can be unit tested on the JVM.
 *
 * Lower score = better match. `null` = no match.
 *
 * - 0 exact label match ("Google" == "google")
 * - 1 label prefix ("Google Maps" for "google")
 * - 2 label substring ("Files by Google" for "google")
 * - 3 fuzzy label subsequence ("Goggle" typo-ish for "google")
 * - 4 package-name substring only (label itself doesn't match,
 *   e.g. "Gboard" via `com.google.android.inputmethod.latin`)
 */
object AppSearchRanker {

    fun score(query: String, label: String, packageName: String): Int? {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return null
        val labelLower = label.lowercase().trim()
        if (labelLower.isEmpty()) return null
        val packageLower = packageName.lowercase().trim()

        if (labelLower == q) return 0
        if (labelLower.startsWith(q)) return 1
        if (labelLower.contains(q)) return 2
        if (fuzzyContains(q, labelLower)) return 3
        if (packageLower.contains(q)) return 4
        return null
    }

    fun fuzzyContains(queryLower: String, targetLower: String): Boolean {
        if (queryLower.isEmpty()) return true
        if (targetLower.isEmpty()) return false
        var queryIndex = 0
        for (char in targetLower) {
            if (queryIndex < queryLower.length && char == queryLower[queryIndex]) {
                queryIndex++
            }
        }
        return queryIndex == queryLower.length
    }
}
