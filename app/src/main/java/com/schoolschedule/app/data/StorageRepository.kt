package com.schoolschedule.app.data

import android.content.Context
import android.os.Environment
import com.schoolschedule.app.domain.DataLoadResult
import com.schoolschedule.app.domain.Period
import com.schoolschedule.app.domain.ScheduleData
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** Reads public, user-editable JSON files. Assets only seed missing files. */
class StorageRepository(private val context: Context) {
    private val dataDir: File
        get() = File(Environment.getExternalStorageDirectory(), "SchoolSchedule/data")

    fun dataPath(): String = dataDir.absolutePath

    fun ensureSeedFiles(): List<String> {
        val issues = mutableListOf<String>()
        try {
            if (!dataDir.exists() && !dataDir.mkdirs()) issues += "تعذر إنشاء مجلد البيانات."
            listOf("schedule.json", "periods.json", "holidays.json").forEach { name ->
                val output = File(dataDir, name)
                if (!output.exists()) {
                    context.assets.open("default_data/$name").use { input -> output.outputStream().use(input::copyTo) }
                }
            }
        } catch (e: Exception) { issues += "تعذر إنشاء ملفات البيانات: ${e.message ?: "خطأ غير معروف"}" }
        return issues
    }

    fun restoreDefaults(): List<String> {
        val issues = mutableListOf<String>()
        try {
            if (!dataDir.exists()) dataDir.mkdirs()
            listOf("schedule.json", "periods.json", "holidays.json").forEach { name ->
                context.assets.open("default_data/$name").use { input ->
                    File(dataDir, name).outputStream().use(input::copyTo)
                }
            }
        } catch (e: Exception) { issues += "تعذر استعادة البيانات الافتراضية: ${e.message}" }
        return issues
    }

    fun load(): DataLoadResult {
        val errors = ensureSeedFiles().toMutableList()
        if (errors.isNotEmpty()) return DataLoadResult(null, errors)
        val schedule = readSchedule(errors)
        val periods = readPeriods(errors)
        val holidays = readHolidays(errors)
        return if (errors.isEmpty() && schedule != null && periods != null && holidays != null) {
            DataLoadResult(ScheduleData(schedule, periods, holidays), emptyList())
        } else DataLoadResult(null, errors)
    }

    private fun text(name: String, errors: MutableList<String>): String? = try {
        val file = File(dataDir, name)
        if (!file.exists()) { errors += "الملف $name غير موجود في ${dataDir.absolutePath}"; null } else file.readText(Charsets.UTF_8)
    } catch (e: Exception) { errors += "تعذر قراءة $name. تحقق من صلاحية الوصول للملف."; null }

    private fun readSchedule(errors: MutableList<String>): Map<String, Map<String, List<String>>>? = try {
        val root = JSONObject(text("schedule.json", errors) ?: return null)
        val days = root.optJSONObject("جدول_الحصص") ?: throw IllegalArgumentException("المفتاح جدول_الحصص غير موجود")
        buildMap {
            days.keys().forEach { day ->
                val grades = days.getJSONObject(day)
                put(day, buildMap {
                    grades.keys().forEach { grade ->
                        val lessons = grades.getJSONArray(grade)
                        put(grade, List(lessons.length()) { lessons.optString(it, "-") })
                    }
                })
            }
        }
    } catch (e: Exception) { errors += "تعذر قراءة schedule.json. تحقق من صحة الملف."; null }

    private fun readPeriods(errors: MutableList<String>): List<Period>? = try {
        val root = JSONObject(text("periods.json", errors) ?: return null)
        root.keys().asSequence().map { id ->
            val item = root.getJSONObject(id)
            Period(id, LocalTime.parse(item.getString("start")), LocalTime.parse(item.getString("end")))
        }.sortedWith(compareBy<Period> { it.start }.thenBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }).toList().also {
            require(it.isNotEmpty()) { "لا توجد حصص" }
            require(it.all { p -> p.end.isAfter(p.start) }) { "وقت نهاية الحصة يجب أن يكون بعد البداية" }
        }
    } catch (e: Exception) { errors += "تعذر قراءة periods.json. تحقق من صحة الملف."; null }

    // Each holiday entry is either a plain "YYYY-MM-DD" string, or an object
    // {"date": "YYYY-MM-DD", "note": "..."} carrying an optional reason.
    private fun readHolidays(errors: MutableList<String>): Map<LocalDate, String?>? = try {
        val root = JSONObject(text("holidays.json", errors) ?: return null)
        val list: JSONArray = root.optJSONArray("holidays") ?: JSONArray()
        buildMap {
            for (i in 0 until list.length()) {
                val entry = list.get(i)
                if (entry is JSONObject) {
                    put(LocalDate.parse(entry.getString("date")), entry.optString("note", "").takeIf { it.isNotBlank() })
                } else {
                    put(LocalDate.parse(entry.toString()), null)
                }
            }
        }
    } catch (e: Exception) { errors += "تعذر قراءة holidays.json. استخدم تاريخ YYYY-MM-DD."; null }

    fun saveHolidays(holidays: Map<LocalDate, String?>): Boolean = try {
        val array = JSONArray()
        holidays.toSortedMap().forEach { (date, note) ->
            if (note.isNullOrBlank()) array.put(date.toString())
            else array.put(JSONObject().put("date", date.toString()).put("note", note))
        }
        val root = JSONObject().put("holidays", array)
        if (!dataDir.exists()) dataDir.mkdirs()
        File(dataDir, "holidays.json").writeText(root.toString(2), Charsets.UTF_8)
        true
    } catch (e: Exception) { false }
}

private fun <T> java.util.Iterator<T>.asSequence(): Sequence<T> = sequence { while (hasNext()) yield(next()) }
