package com.devrinth.launchpad

import com.devrinth.launchpad.search.plugins.AssistantLaunch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantLaunchTest {

    @Test
    fun prefersGeminiOverGoogleApp() {
        assertEquals(
            "com.google.android.apps.bard",
            AssistantLaunch.pickAssistant(
                listOf(
                    "com.devrinth.launchpad",
                    "com.google.android.googlequicksearchbox",
                    "com.google.android.apps.bard"
                ),
                "com.devrinth.launchpad"
            )
        )
    }

    @Test
    fun fallsBackToGoogleApp() {
        assertEquals(
            "com.google.android.googlequicksearchbox",
            AssistantLaunch.pickAssistant(
                listOf("com.devrinth.launchpad", "com.google.android.googlequicksearchbox"),
                "com.devrinth.launchpad"
            )
        )
    }

    @Test
    fun fallsBackToAnyOtherAssistant() {
        assertEquals(
            "com.example.assistant",
            AssistantLaunch.pickAssistant(
                listOf("com.devrinth.launchpad", "com.example.assistant"),
                "com.devrinth.launchpad"
            )
        )
    }

    @Test
    fun neverPicksItself() {
        assertNull(
            AssistantLaunch.pickAssistant(
                listOf("com.devrinth.launchpad"),
                "com.devrinth.launchpad"
            )
        )
        assertNull(AssistantLaunch.pickAssistant(emptyList(), "com.devrinth.launchpad"))
    }
}
