package com.schoolschedule.app.data

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class PendingNotificationTest(val id: String, val label: String, val scheduledAtMillis: Long)

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
    /** true = scheduled by hand from the Notification Lab; false = a real class notification (start/end/before). */
    val isTest: Boolean = true,
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
        val raw = prefs(context).getString(KEY_PENDING, null) ?: return emptyList()
        val array = JSONArray(raw)
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            PendingNotificationTest(o.getString("id"), o.getString("label"), o.getLong("scheduledAtMillis"))
        }.sortedBy { it.scheduledAtMillis }
    } catch (e: Exception) { emptyList() }

    private fun savePending(context: Context, tests: List<PendingNotificationTest>) {
        val array = JSONArray()
        tests.forEach { t -> array.put(JSONObject().put("id", t.id).put("label", t.label).put("scheduledAtMillis", t.scheduledAtMillis)) }
        prefs(context).edit().putString(KEY_PENDING, array.toString()).apply()
    }

    fun addPending(context: Context, test: PendingNotificationTest) {
        savePending(context, pending(context) + test)
    }

    fun removePending(context: Context, id: String) {
        savePending(context, pending(context).filterNot { it.id == id })
    }

    /** Called from AlarmReceiver the instant any alarm actually fires — test or real. */
    fun recordDelivery(context: Context, id: String, label: String, scheduledAtMillis: Long, isTest: Boolean) {
        if (isTest) removePending(context, id)
        val deliveredAtMillis = System.currentTimeMillis()
        val powerManager = context.getSystemService(PowerManager::class.java)
        val batteryOptimizationIgnored = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(android.app.AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
        } else true
        val entry = NotificationTestLogEntry(
            id = id, label = label, scheduledAtMillis = scheduledAtMillis, deliveredAtMillis = deliveredAtMillis,
            deviceManufacturer = Build.MANUFACTURER ?: "", deviceModel = Build.MODEL ?: "",
            androidRelease = Build.VERSION.RELEASE ?: "", androidSdkInt = Build.VERSION.SDK_INT,
            batteryOptimizationIgnored = batteryOptimizationIgnored, canScheduleExactAlarms = canExact,
            isTest = isTest,
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
                        .put("isTest", e.isTest),
                )
            }
            file.writeText(array.toString(2), Charsets.UTF_8)
        } catch (_: Exception) { /* best-effort diagnostic log; never crash the app over it */ }
    }

    fun clearLog() = writeLog(emptyList())
}
