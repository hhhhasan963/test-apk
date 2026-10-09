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
}
