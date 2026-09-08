package com.registry.coach.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import com.registry.coach.monitor.AccessibilityMonitor

/**
 * Used by the native main screen to check and request the accessibility grant,
 * which Android may revoke on APK reinstall/update
 * (an OS security measure; no app-level code can prevent or persist through
 * it, only make re-granting faster).
 */
object AccessibilitySettingsHelper {

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
            val enabledList = am?.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            if (enabledList != null) {
                for (info in enabledList) {
                    val id = info.id
                    if (id.contains(context.packageName, ignoreCase = true) && id.contains("AccessibilityMonitor", ignoreCase = true)) {
                        return true
                    }
                }
            }
        } catch (_: Exception) {}

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return (enabledServices.contains(context.packageName, ignoreCase = true) &&
                enabledServices.contains("AccessibilityMonitor", ignoreCase = true))
    }

    /**
     * Jumps straight to this service's own row in Settings > Accessibility,
     * instead of the generic list the user would otherwise have to scroll
     * through to find SecondGuess.
     *
     * ":settings:fragment_args_key" is undocumented-but-long-standing on
     * stock Android and most OEM skins (including Samsung, this app's
     * primary test target) for landing on a specific accessibility service's
     * detail screen. Not guaranteed on every OEM — if the extras are
     * ignored, this just silently lands on the generic list rather than
     * failing; the try/catch below only guards the (rare) case where
     * ACTION_ACCESSIBILITY_SETTINGS itself has no handler at all.
     */
    fun openAccessibilitySettingsForThisService(activity: Activity) {
        val component = ComponentName(activity, AccessibilityMonitor::class.java)
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            putExtra(":settings:fragment_args_key", component.flattenToString())
            putExtra(
                ":settings:show_fragment_args",
                Bundle().apply { putString(":settings:fragment_args_key", component.flattenToString()) },
            )
        }
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }
}
