package com.schoolschedule.app.notifications

import android.app.ActivityManager
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Everything that decides whether class alarms reach the user on time:
 * what we can read from the system, and shortcuts to the pages where the
 * user can fix each one. Nothing here is guaranteed to exist on every ROM,
 * so every launcher falls back to a more generic settings page.
 */
object Reliability {
    data class Status(
        val notificationsAllowed: Boolean,
        val exactAlarmsAllowed: Boolean,
        val batteryOptimizationIgnored: Boolean,
        val backgroundRestricted: Boolean,
    ) {
        /** Battery-related limits that can make alarms arrive late (what the home-screen banner is about). */
        val hasBatteryIssue: Boolean get() = !batteryOptimizationIgnored || backgroundRestricted
    }

    fun status(context: Context): Status {
        val power = context.getSystemService(PowerManager::class.java)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val ignoring = runCatching { power?.isIgnoringBatteryOptimizations(context.packageName) ?: false }.getOrDefault(false)
        val exact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { alarms?.canScheduleExactAlarms() ?: false }.getOrDefault(false)
        } else true
        val notifications = runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }.getOrDefault(true)
        val restricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted ?: false }.getOrDefault(false)
        } else false
        return Status(notifications, exact, ignoring, restricted)
    }

    /** Xiaomi / Redmi / POCO phones add their own background limits (Autostart, "Battery saver" per app) on top of Android's. */
    val isXiaomiFamily: Boolean
        get() {
            val maker = (Build.MANUFACTURER ?: "").lowercase()
            val brand = (Build.BRAND ?: "").lowercase()
            return maker in XIAOMI_NAMES || brand in XIAOMI_NAMES
        }

    private val XIAOMI_NAMES = setOf("xiaomi", "redmi", "poco")

    private fun launch(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }

    fun openAppDetails(context: Context): Boolean =
        launch(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))

    /** Asks (system dialog) to exempt the app from battery optimization; falls back to the list, then to app details. */
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        if (launch(context, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))) return true
        if (launch(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) return true
        return openAppDetails(context)
    }

    /** Xiaomi's per-app "Battery saver" page (best effort — component names vary between MIUI/HyperOS builds). */
    fun openXiaomiBatterySaver(context: Context): Boolean {
        val intent = Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
            .putExtra("package_name", context.packageName)
            .putExtra("package_label", "دوامي المدرسي")
        return launch(context, intent) || requestIgnoreBatteryOptimizations(context)
    }

    /** Xiaomi's Autostart manager (best effort), otherwise the app's details page. */
    fun openAutostart(context: Context): Boolean {
        val intent = Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
        return launch(context, intent) || openAppDetails(context)
    }

    fun openNotificationSettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        return launch(context, intent) || openAppDetails(context)
    }

    fun openExactAlarmSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (launch(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))) return true
        }
        return openAppDetails(context)
    }
}
