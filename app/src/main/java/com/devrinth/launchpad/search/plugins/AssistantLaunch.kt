package com.devrinth.launchpad.search.plugins

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.service.voice.VoiceInteractionService

/**
 * Finds and opens the device's other assistant (whatever provides a
 * VoiceInteractionService that isn't us). No hardcoded assumptions beyond a
 * preference order -- on most phones this is the Google app or Gemini.
 */
object AssistantLaunch {

    private val PREFERRED = listOf(
        "com.google.android.apps.bard",
        "com.google.android.googlequicksearchbox",
    )

    /**
     * Pure pick: preferred known assistants first, then any other candidate.
     * Our own package is never returned. No Android needed -- unit tested.
     */
    fun pickAssistant(candidates: List<String>, selfPackage: String): String? {
        val others = candidates.filter { it != selfPackage }
        return PREFERRED.firstOrNull { it in others } ?: others.firstOrNull()
    }

    fun findAssistantPackage(context: Context): String? {
        return try {
            val services = context.packageManager.queryIntentServices(
                Intent(VoiceInteractionService.SERVICE_INTERFACE), 0
            )
            pickAssistant(services.map { it.serviceInfo.packageName }, context.packageName)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Opens the assistant's voice UI. A third-party app cannot start another
     * app's VoiceInteractionService directly (system-only binding:
     * `com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService`
     * throws SecurityException), so the closest trigger is the public voice
     * search entry point, forced onto the detected assistant package. Falls
     * back to the plain launcher intent when unhandled.
     */
    fun openAssistant(context: Context): Boolean {
        val pkg = findAssistantPackage(context) ?: return false
        return try {
            val voice = Intent(RecognizerIntent.ACTION_WEB_SEARCH)
                .setPackage(pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(voice)
            true
        } catch (_: Exception) {
            try {
                val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
