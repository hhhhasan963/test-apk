package com.schoolschedule.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.schoolschedule.app.MainActivity
import com.schoolschedule.app.data.AppPreferences
import com.schoolschedule.app.data.StorageRepository
import com.schoolschedule.app.domain.Lesson
import com.schoolschedule.app.domain.ScheduleEngine
import com.schoolschedule.app.domain.UserRole
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object NotificationScheduler {
    const val CHANNEL_ID = "class_notifications"
    const val ACTION_NOTIFY = "com.schoolschedule.app.NOTIFY"
    const val ACTION_REFRESH = "com.schoolschedule.app.REFRESH_ALARMS"
    const val ACTION_TEST = "com.schoolschedule.app.TEST_NOTIFY"

    /** Normal exact alarm (setExactAndAllowWhileIdle). */
    const val ALARM_EXACT = "exact"
    /** setAlarmClock: treated by the system like a clock-app alarm. Shows an alarm icon in the status bar. */
    const val ALARM_CLOCK = "clock"
    /** Fallback when the "Alarms & reminders" permission is missing. */
    const val ALARM_INEXACT = "inexact"

    private const val DAYS_AHEAD = 14

    /** Tapping a notification (or the system's "next alarm" entry) opens the app. */
    fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun resolveAlarmType(context: Context, wantClock: Boolean): String {
        if (wantClock) return ALARM_CLOCK
        // Before Android 12 exact alarms need no special permission.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return ALARM_EXACT
        val manager = context.getSystemService(AlarmManager::class.java)
        return if (manager.canScheduleExactAlarms()) ALARM_EXACT else ALARM_INEXACT
    }

    // Test notifications live outside the normal reschedule/cancelAll bookkeeping
    // (they're one-offs the user schedules by hand from the Notification Lab).
    // Returns the alarm type that was really used, so the lab can show/log it.
    fun scheduleTest(context: Context, id: String, label: String, atMillis: Long, wantClock: Boolean = false): String {
        val alarmType = resolveAlarmType(context, wantClock)
        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_TEST)
            .putExtra("testId", id)
            .putExtra("label", label)
            .putExtra("scheduledAtMillis", atMillis)
            .putExtra("alarmType", alarmType)
        val pi = PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        setAlarmAt(context, atMillis, pi, alarmType)
        return alarmType
    }

    fun cancelTest(context: Context, id: String) {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_TEST)
        val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        PendingIntent.getBroadcast(context, id.hashCode(), intent, flags)?.let {
            context.getSystemService(AlarmManager::class.java).cancel(it)
        }
    }

    fun reschedule(context: Context) {
        val prefs = AppPreferences(context)
        val selection = prefs.selection ?: return cancelAll(context)
        val result = StorageRepository(context).load()
        val data = result.data ?: return
        val engine = ScheduleEngine(data)
        cancelAll(context)
        val teacher = selection.role == UserRole.TEACHER
        val alarmType = resolveAlarmType(context, prefs.highReliability)
        val now = LocalDateTime.now()
        val ids = mutableSetOf<String>()
        for (offset in 0..DAYS_AHEAD) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            if (engine.isHoliday(date)) continue
            val lessons = engine.lessonsFor(date, selection)
            val lastPeriodId = lessons.maxByOrNull { it.period.end }?.period?.id
            lessons.forEach { lesson ->
                if (prefs.notifyBefore) schedule(context, date, lesson, "before", lesson.period.start.minusMinutes(5), ids, teacher, alarmType, false)
                if (prefs.notifyStart) schedule(context, date, lesson, "start", lesson.period.start, ids, teacher, alarmType, false)
                val isLastOfDay = lesson.period.id == lastPeriodId
                if (isLastOfDay && prefs.notifyEndOfDay) {
                    // One merged notification for the last lesson instead of two at the same instant.
                    schedule(context, date, lesson, "dayEnd", lesson.period.end, ids, teacher, alarmType, prefs.notifyEnd)
                } else if (prefs.notifyEnd) {
                    schedule(context, date, lesson, "end", lesson.period.end, ids, teacher, alarmType, false)
                }
            }
        }
        scheduleMaintenance(context, now.toLocalDate().plusDays(1), ids)
        prefs.scheduledIds = ids
    }

    fun cancelAll(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val prefs = AppPreferences(context)
        prefs.scheduledIds.forEach { raw ->
            val parts = raw.split("|")
            if (parts.size == 2) {
                pendingIntent(context, parts[0], parts[1], false)?.let { manager.cancel(it) }
            }
        }
        prefs.scheduledIds = emptySet()
    }

    private fun schedule(
        context: Context, date: LocalDate, lesson: Lesson, type: String, time: java.time.LocalTime,
        ids: MutableSet<String>, teacher: Boolean, alarmType: String, endAlsoOn: Boolean,
    ) {
        val at = LocalDateTime.of(date, time)
        if (!at.isAfter(LocalDateTime.now())) return
        val token = "${date}|${lesson.period.id}|$type"
        // A teacher already knows their subject — what matters is which class they're walking into.
        // A student already knows their class — what matters is which subject is next.
        val detail = (if (teacher) lesson.grades.joinToString(" + ") else lesson.subject).ifBlank { lesson.subject }
        val pid = lesson.period.id
        val (title, message) = when (type) {
            "before" -> "تنبيه الدوام" to "بعد 5 دقائق تبدأ الحصة $pid — $detail"
            "start" -> "تنبيه الدوام" to "بدأت الحصة $pid — $detail"
            "end" -> "انتهت الحصة" to "انتهت الحصة $pid — $detail"
            else -> "انتهى الدوام 🎉" to
                if (endAlsoOn) "انتهت الحصة $pid — $detail. بالتوفيق ورجوعًا سالمًا 🌿"
                else "انتهت آخر حصة لك اليوم. استمتع ببقية يومك 🌿"
        }
        val scheduledAtMillis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_NOTIFY)
            .putExtra("title", title)
            .putExtra("message", message)
            .putExtra("id", token.hashCode())
            .putExtra("scheduledAtMillis", scheduledAtMillis)
            .putExtra("alarmType", alarmType)
        val pi = PendingIntent.getBroadcast(context, token.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        setAlarmAt(context, scheduledAtMillis, pi, alarmType)
        ids += "${token.hashCode()}|notify"
    }

    private fun scheduleMaintenance(context: Context, date: LocalDate, ids: MutableSet<String>) {
        val token = "daily_refresh"
        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_REFRESH)
        val pi = PendingIntent.getBroadcast(context, token.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val millis = LocalDateTime.of(date, java.time.LocalTime.of(0, 5)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Housekeeping must never show up as the user's "next alarm", so it never uses setAlarmClock.
        setAlarmAt(context, millis, pi, resolveAlarmType(context, false))
        ids += "${token.hashCode()}|refresh"
    }

    private fun setAlarmAt(context: Context, millis: Long, pi: PendingIntent, alarmType: String) {
        val manager = context.getSystemService(AlarmManager::class.java)
        try {
            when (alarmType) {
                ALARM_CLOCK -> manager.setAlarmClock(AlarmManager.AlarmClockInfo(millis, openAppPendingIntent(context)), pi)
                ALARM_EXACT -> manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
                else -> manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        } catch (_: SecurityException) {
            // Exact-alarm permission was revoked between the check and the call: degrade instead of crashing.
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    // PendingIntent recreation uses a stable token/request code for alarm cancellation.
    // With FLAG_NO_CREATE, Android returns null when no matching PendingIntent is
    // currently registered (e.g. stale ids left over from a previous install) —
    // that is expected, not an error, so the return type must be nullable.
    private fun pendingIntent(context: Context, code: String, type: String, create: Boolean): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(if (type == "refresh") ACTION_REFRESH else ACTION_NOTIFY)
        val flags = (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE) or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, code.toIntOrNull() ?: code.hashCode(), intent, flags)
    }
}
