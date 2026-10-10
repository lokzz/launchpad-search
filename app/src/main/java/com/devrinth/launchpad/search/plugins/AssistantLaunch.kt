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
     * Opens the assistant UI. A third-party app cannot start another app's
     * VoiceInteractionService directly (system-only binding:
     * `com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService`
     * throws SecurityException), so we fire the intents its own gateway
     * activities export, forced onto the detected package:
     * 1. Lens, resolved from known candidates (first present one wins, so
     *    GSA renames don't break this).
     * 2. ACTION_ASSIST -> the assistant gateway (the real entry point).
     * 3. ACTION_WEB_SEARCH -> voice search UI.
     * 4. Plain launcher intent (opens the app, no assistant UI).
     */
    fun openAssistant(context: Context): Boolean {
        val pkg = findAssistantPackage(context) ?: return false
        if (pkg == PKG_GSA && openLens(context, pkg)) return true
        val attempts = listOf(
            Intent(Intent.ACTION_ASSIST),
            Intent(RecognizerIntent.ACTION_WEB_SEARCH),
        )
        for (attempt in attempts) {
            try {
                attempt.setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(attempt)
                return true
            } catch (_: Exception) {
            }
        }
        return try {
            val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            true
        } catch (_: Exception) {
            false
        }
    }

    private const val PKG_GSA = "com.google.android.googlequicksearchbox"

    /** Known Lens entry points, newest first. First resolvable one wins. */
    private val LENS_CANDIDATES = listOf(
        "com.google.android.apps.search.lens.LensExportedActivity",
        "com.google.android.apps.search.lens.LensActivity",
        "com.google.android.apps.lens.MainActivity",
    )

    private fun openLens(context: Context, pkg: String): Boolean {
        for (cls in LENS_CANDIDATES) {
            try {
                val component = android.content.ComponentName(pkg, cls)
                // Throws if absent; startActivity throws if not exported.
                context.packageManager.getActivityInfo(component, 0)
                val lens = Intent(Intent.ACTION_MAIN)
                    .setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(lens)
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }
}
