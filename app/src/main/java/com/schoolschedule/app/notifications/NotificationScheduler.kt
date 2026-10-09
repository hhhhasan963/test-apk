package com.schoolschedule.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.schoolschedule.app.data.AppPreferences
import com.schoolschedule.app.data.StorageRepository
import com.schoolschedule.app.domain.Lesson
import com.schoolschedule.app.domain.ScheduleEngine
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object NotificationScheduler {
    const val CHANNEL_ID = "class_notifications"
    const val ACTION_NOTIFY = "com.schoolschedule.app.NOTIFY"
    const val ACTION_REFRESH = "com.schoolschedule.app.REFRESH_ALARMS"
    const val ACTION_TEST = "com.schoolschedule.app.TEST_NOTIFY"
    private const val DAYS_AHEAD = 14

    // Test notifications live outside the normal reschedule/cancelAll bookkeeping
    // (they're one-offs the user schedules by hand from the Notification Lab).
    fun scheduleTest(context: Context, id: String, label: String, atMillis: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_TEST)
            .putExtra("testId", id)
            .putExtra("label", label)
            .putExtra("scheduledAtMillis", atMillis)
        val pi = PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        setAlarm(context, LocalDateTime.ofInstant(Instant.ofEpochMilli(atMillis), ZoneId.systemDefault()), pi)
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
        val now = LocalDateTime.now()
        val ids = mutableSetOf<String>()
        for (offset in 0..DAYS_AHEAD) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            if (engine.isHoliday(date)) continue
            engine.lessonsFor(date, selection).forEach { lesson ->
                if (prefs.notifyBefore) schedule(context, date, lesson, "before", lesson.period.start.minusMinutes(5), ids)
                if (prefs.notifyStart) schedule(context, date, lesson, "start", lesson.period.start, ids)
                if (prefs.notifyEnd) schedule(context, date, lesson, "end", lesson.period.end, ids)
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

    private fun schedule(context: Context, date: LocalDate, lesson: Lesson, type: String, time: java.time.LocalTime, ids: MutableSet<String>) {
        val at = LocalDateTime.of(date, time)
        if (!at.isAfter(LocalDateTime.now())) return
        val token = "${date}|${lesson.period.id}|$type"
        val title = when (type) {
            "before" -> "بعد 5 دقائق تبدأ الحصة ${lesson.period.id}"
            "start" -> "بدأت الحصة ${lesson.period.id}"
            else -> "انتهت الحصة ${lesson.period.id}"
        }
        val target = "$title — ${lesson.subject}" + if (lesson.grades.size > 1) " (${lesson.grades.joinToString(" + ")})" else if (lesson.grades.isNotEmpty()) " (${lesson.grades.first()})" else ""
        val scheduledAtMillis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_NOTIFY)
            .putExtra("title", if (type == "end") "انتهت الحصة" else "تنبيه الدوام")
            .putExtra("message", target)
            .putExtra("id", token.hashCode())
            .putExtra("scheduledAtMillis", scheduledAtMillis)
        val pi = PendingIntent.getBroadcast(context, token.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        setAlarm(context, at, pi)
        ids += "${token.hashCode()}|notify"
    }

    private fun scheduleMaintenance(context: Context, date: LocalDate, ids: MutableSet<String>) {
        val token = "daily_refresh"
        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_REFRESH)
        val pi = PendingIntent.getBroadcast(context, token.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        setAlarm(context, LocalDateTime.of(date, java.time.LocalTime.of(0, 5)), pi)
        ids += "${token.hashCode()}|refresh"
    }

    private fun setAlarm(context: Context, time: LocalDateTime, pi: PendingIntent) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val millis = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.canScheduleExactAlarms()) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
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
