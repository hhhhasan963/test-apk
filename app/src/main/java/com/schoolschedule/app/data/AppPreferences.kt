package com.schoolschedule.app.data

import android.content.Context
import com.schoolschedule.app.domain.UserRole
import com.schoolschedule.app.domain.UserSelection

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("school_schedule_preferences", Context.MODE_PRIVATE)
    var selection: UserSelection?
        get() {
            val role = prefs.getString("role", null) ?: return null
            val value = prefs.getString("value", null) ?: return null
            return UserSelection(UserRole.valueOf(role), value)
        }
        set(value) = prefs.edit().apply {
            if (value == null) { remove("role"); remove("value") }
            else { putString("role", value.role.name); putString("value", value.value) }
        }.apply()
    var notifyStart: Boolean
        get() = prefs.getBoolean("notify_start", true)
        set(value) = prefs.edit().putBoolean("notify_start", value).apply()
    var notifyEnd: Boolean
        get() = prefs.getBoolean("notify_end", false)
        set(value) = prefs.edit().putBoolean("notify_end", value).apply()
    var notifyBefore: Boolean
        get() = prefs.getBoolean("notify_before", true)
        set(value) = prefs.edit().putBoolean("notify_before", value).apply()
    var scheduledIds: Set<String>
        get() = prefs.getStringSet("scheduled_ids", emptySet()).orEmpty()
        set(value) = prefs.edit().putStringSet("scheduled_ids", value).apply()
    var themeMode: String
        get() = prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"
        set(value) = prefs.edit().putString("theme_mode", value).apply()
    var notifyEndOfDay: Boolean
        get() = prefs.getBoolean("notify_end_of_day", false)
        set(value) = prefs.edit().putBoolean("notify_end_of_day", value).apply()
    /** Optional: schedule via setAlarmClock (treated like a clock alarm; shows a status-bar alarm icon). Off by default. */
    var highReliability: Boolean
        get() = prefs.getBoolean("high_reliability", false)
        set(value) = prefs.edit().putBoolean("high_reliability", value).apply()
    /** Last time the app UI was brought to the foreground — logged next to each notification to correlate delays with app usage. */
    var lastOpenedAtMillis: Long
        get() = prefs.getLong("last_opened_at", 0L)
        set(value) = prefs.edit().putLong("last_opened_at", value).apply()
    var batteryBannerRemindAfter: Long
        get() = prefs.getLong("battery_banner_remind_after", 0L)
        set(value) = prefs.edit().putLong("battery_banner_remind_after", value).apply()
    var batteryBannerDismissed: Boolean
        get() = prefs.getBoolean("battery_banner_dismissed", false)
        set(value) = prefs.edit().putBoolean("battery_banner_dismissed", value).apply()
    var delayAlertDismissedAt: Long
        get() = prefs.getLong("delay_alert_dismissed_at", 0L)
        set(value) = prefs.edit().putLong("delay_alert_dismissed_at", value).apply()
}
