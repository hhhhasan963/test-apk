package com.schoolschedule.app.data

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** alarmType: "exact" (setExactAndAllowWhileIdle), "clock" (setAlarmClock) or "inexact" (fallback when exact alarms aren't allowed). */
data class PendingNotificationTest(val id: String, val label: String, val scheduledAtMillis: Long, val alarmType: String = "exact")

data class NotificationTestLogEntry(
    val id: String,
    val label: String,
    val scheduledAtMillis: Long,
    val deliveredAtMillis: Long,
    val deviceManufacturer: String,
    val deviceModel: String,
    val androidRelease: String,
    val androidSdkInt: Int,
    val batteryOptimizationIgnored: Boolean,
    val canScheduleExactAlarms: Boolean,
    /** true = scheduled by hand from the Notification Lab; false = a real class notification (start/end/before/day end). */
    val isTest: Boolean = true,
    val alarmType: String = "",
    // --- Device/app state captured the moment the alarm was actually delivered ---
    val screenOn: Boolean = false,
    val deviceIdle: Boolean = false,
    val powerSave: Boolean = false,
    val charging: Boolean = false,
    /** 10 active, 20 working set, 30 frequent, 40 rare, 45 restricted; -1 = unknown. */
    val standbyBucket: Int = -1,
    /** How old this app process was when the alarm arrived. A few seconds = the process had to be started for this alarm. */
    val processAgeSeconds: Long = -1,
    /** Minutes since the user last brought the app to the foreground; -1 = never recorded. */
    val minutesSinceAppOpened: Long = -1,
    val backgroundRestricted: Boolean = false,
) {
    val deltaSeconds: Long get() = (deliveredAtMillis - scheduledAtMillis) / 1000
}

/**
 * Everything the Notification Lab needs to persist:
 *  - the small list of "pending" test alarms (SharedPreferences, ephemeral)
 *  - the results log (a plain JSON file under /SchoolSchedule/, next to the
 *    schedule data, so the user can find it in any file manager and send it
 *    over for inspection — same pattern already used for schedule.json etc.)
 */
object NotificationTestStore {
    private const val PREFS_NAME = "notification_tests"
    private const val KEY_PENDING = "pending"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun logFile(): File = File(Environment.getExternalStorageDirectory(), "SchoolSchedule/notification_test_log.json")
    fun logFilePath(): String = logFile().absolutePath

    fun pending(context: Context): List<PendingNotificationTest> = try {
        val raw = prefs(context).getString(KEY_PENDING, null) ?: throw java.io.FileNotFoundException()
        val array = JSONArray(raw)
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            PendingNotificationTest(o.getString("id"), o.getString("label"), o.getLong("scheduledAtMillis"), o.optString("alarmType", "exact"))
        }.sortedBy { it.scheduledAtMillis }
    } catch (e: Exception) { emptyList() }

    private fun savePending(context: Context, tests: List<PendingNotificationTest>) {
        val array = JSONArray()
        tests.forEach { t ->
            array.put(JSONObject().put("id", t.id).put("label", t.label).put("scheduledAtMillis", t.scheduledAtMillis).put("alarmType", t.alarmType))
        }
        prefs(context).edit().putString(KEY_PENDING, array.toString()).apply()
    }

    fun addPending(context: Context, test: PendingNotificationTest) {
        savePending(context, pending(context) + test)
    }

    fun removePending(context: Context, id: String) {
        savePending(context, pending(context).filterNot { it.id == id })
    }

    /** Called from AlarmReceiver the instant any alarm actually fires — test or real. */
    fun recordDelivery(context: Context, id: String, label: String, scheduledAtMillis: Long, isTest: Boolean, alarmType: String = "") {
        if (isTest) removePending(context, id)
        val deliveredAtMillis = System.currentTimeMillis()

        val powerManager = context.getSystemService(PowerManager::class.java)
        val batteryOptimizationIgnored = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(android.app.AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
        } else true

        // Every probe below is best-effort: a vendor ROM that throws on one of them must never cost us the log entry.
        val screenOn = runCatching { powerManager?.isInteractive ?: false }.getOrDefault(false)
        val deviceIdle = runCatching { powerManager?.isDeviceIdleMode ?: false }.getOrDefault(false)
        val powerSave = runCatching { powerManager?.isPowerSaveMode ?: false }.getOrDefault(false)
        val charging = runCatching { context.getSystemService(BatteryManager::class.java)?.isCharging ?: false }.getOrDefault(false)
        val bucket = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                (context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager)?.appStandbyBucket ?: -1
            } else -1
        }.getOrDefault(-1)
        val processAge = runCatching { (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()) / 1000 }.getOrDefault(-1L)
        val lastOpened = runCatching { AppPreferences(context).lastOpenedAtMillis }.getOrDefault(0L)
        val minutesSinceOpen = if (lastOpened > 0) (deliveredAtMillis - lastOpened) / 60_000 else -1L
        val bgRestricted = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted ?: false else false
        }.getOrDefault(false)

        val entry = NotificationTestLogEntry(
            id = id, label = label, scheduledAtMillis = scheduledAtMillis, deliveredAtMillis = deliveredAtMillis,
            deviceManufacturer = Build.MANUFACTURER ?: "", deviceModel = Build.MODEL ?: "",
            androidRelease = Build.VERSION.RELEASE ?: "", androidSdkInt = Build.VERSION.SDK_INT,
            batteryOptimizationIgnored = batteryOptimizationIgnored, canScheduleExactAlarms = canExact,
            isTest = isTest, alarmType = alarmType,
            screenOn = screenOn, deviceIdle = deviceIdle, powerSave = powerSave, charging = charging,
            standbyBucket = bucket, processAgeSeconds = processAge, minutesSinceAppOpened = minutesSinceOpen,
            backgroundRestricted = bgRestricted,
        )
        // Keep the file from growing forever: real notifications fire constantly,
        // so cap the log at the most recent 200 entries.
        writeLog((listOf(entry) + readLog()).take(200))
    }

    fun readLog(): List<NotificationTestLogEntry> = try {
        val file = logFile()
        if (!file.exists()) throw java.io.FileNotFoundException()
        val array = JSONArray(file.readText(Charsets.UTF_8))
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            NotificationTestLogEntry(
                id = o.getString("id"), label = o.getString("label"),
                scheduledAtMillis = o.getLong("scheduledAtMillis"), deliveredAtMillis = o.getLong("deliveredAtMillis"),
                deviceManufacturer = o.optString("deviceManufacturer", ""), deviceModel = o.optString("deviceModel", ""),
                androidRelease = o.optString("androidRelease", ""), androidSdkInt = o.optInt("androidSdkInt", 0),
                batteryOptimizationIgnored = o.optBoolean("batteryOptimizationIgnored", false),
                canScheduleExactAlarms = o.optBoolean("canScheduleExactAlarms", false),
                isTest = o.optBoolean("isTest", true),
                alarmType = o.optString("alarmType", ""),
                screenOn = o.optBoolean("screenOn", false),
                deviceIdle = o.optBoolean("deviceIdle", false),
                powerSave = o.optBoolean("powerSave", false),
                charging = o.optBoolean("charging", false),
                standbyBucket = o.optInt("standbyBucket", -1),
                processAgeSeconds = o.optLong("processAgeSeconds", -1L),
                minutesSinceAppOpened = o.optLong("minutesSinceAppOpened", -1L),
                backgroundRestricted = o.optBoolean("backgroundRestricted", false),
            )
        }
    } catch (e: Exception) { emptyList() }

    private fun writeLog(entries: List<NotificationTestLogEntry>) {
        try {
            val file = logFile()
            file.parentFile?.mkdirs()
            val array = JSONArray()
            entries.forEach { e ->
                array.put(
                    JSONObject()
                        .put("id", e.id).put("label", e.label)
                        .put("scheduledAtMillis", e.scheduledAtMillis).put("deliveredAtMillis", e.deliveredAtMillis)
                        .put("deviceManufacturer", e.deviceManufacturer).put("deviceModel", e.deviceModel)
                        .put("androidRelease", e.androidRelease).put("androidSdkInt", e.androidSdkInt)
                        .put("batteryOptimizationIgnored", e.batteryOptimizationIgnored)
                        .put("canScheduleExactAlarms", e.canScheduleExactAlarms)
                        .put("isTest", e.isTest)
                        .put("alarmType", e.alarmType)
                        .put("screenOn", e.screenOn)
                        .put("deviceIdle", e.deviceIdle)
                        .put("powerSave", e.powerSave)
                        .put("charging", e.charging)
                        .put("standbyBucket", e.standbyBucket)
                        .put("processAgeSeconds", e.processAgeSeconds)
                        .put("minutesSinceAppOpened", e.minutesSinceAppOpened)
                        .put("backgroundRestricted", e.backgroundRestricted),
                )
            }
            file.writeText(array.toString(2), Charsets.UTF_8)
        } catch (_: Exception) { /* best-effort diagnostic log; never crash the app over it */ }
    }

    fun clearLog() = writeLog(emptyList())
}
