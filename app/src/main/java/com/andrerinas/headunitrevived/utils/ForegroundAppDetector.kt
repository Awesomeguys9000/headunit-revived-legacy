package com.andrerinas.headunitrevived.utils

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build

/**
 * Utility to detect which app is currently in the foreground.
 * Uses ActivityManager.getRunningTasks() on API < 21 (fully functional),
 * and UsageStatsManager on API 21+ (requires PACKAGE_USAGE_STATS permission).
 */
object ForegroundAppDetector {

    /**
     * Returns the package name of the current foreground app, or null if it cannot be determined.
     */
    @Suppress("DEPRECATION")
    fun getForegroundPackage(context: Context): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getForegroundPackageViaUsageStats(context)
        } else {
            getForegroundPackageViaRunningTasks(context)
        }
    }

    /**
     * API < 21: Use ActivityManager.getRunningTasks(1) which returns the top activity.
     */
    @Suppress("DEPRECATION")
    private fun getForegroundPackageViaRunningTasks(context: Context): String? {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val tasks = am?.getRunningTasks(1)
            tasks?.firstOrNull()?.topActivity?.packageName
        } catch (e: Exception) {
            AppLog.w("ForegroundAppDetector: getRunningTasks failed: ${e.message}")
            null
        }
    }

    /**
     * API 21+: Use UsageStatsManager to query recent usage events.
     * Requires the user to grant "Usage Access" permission in system settings.
     */
    private fun getForegroundPackageViaUsageStats(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return null
            val now = System.currentTimeMillis()
            // Query the last 5 seconds of usage events
            val events = usm.queryEvents(now - 5000, now)
            var lastPackage: String? = null
            val event = android.app.usage.UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                     event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED)) {
                    lastPackage = event.packageName
                }
            }
            lastPackage
        } catch (e: Exception) {
            AppLog.w("ForegroundAppDetector: UsageStats query failed: ${e.message}")
            null
        }
    }

    /**
     * Check if the app has Usage Access permission (API 21+).
     */
    fun hasUsageStatsPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return true // Not needed
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return false
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60000, now)
            stats != null && stats.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}
