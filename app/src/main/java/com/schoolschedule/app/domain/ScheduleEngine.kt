package com.schoolschedule.app.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ScheduleEngine(private val data: ScheduleData) {
    fun dayName(date: LocalDate): String = when (date.dayOfWeek) {
        DayOfWeek.SUNDAY -> "الأحد"; DayOfWeek.MONDAY -> "الإثنين"; DayOfWeek.TUESDAY -> "الثلاثاء"
        DayOfWeek.WEDNESDAY -> "الأربعاء"; DayOfWeek.THURSDAY -> "الخميس"; DayOfWeek.FRIDAY -> "الجمعة"; DayOfWeek.SATURDAY -> "السبت"
    }

    fun isHoliday(date: LocalDate): Boolean = date in data.holidays.keys || date.dayOfWeek in setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
    fun holidayNote(date: LocalDate): String? = data.holidays[date]?.trim()?.takeIf { it.isNotEmpty() }

    fun grades(): List<String> = data.schedule.values.flatMap { it.keys }.distinct().sorted()
    fun subjects(): List<String> = data.schedule.values.flatMap { day -> day.values.flatten() }.filter(::isLesson).distinct().sorted()

    fun lessonsFor(date: LocalDate, selection: UserSelection): List<Lesson> = lessonsForDay(dayName(date), selection)

    fun lessonsForDay(day: String, selection: UserSelection): List<Lesson> {
        val grades = data.schedule[day].orEmpty()
        return when (selection.role) {
            UserRole.STUDENT -> grades[selection.value].orEmpty().mapIndexedNotNull { index, subject ->
                data.periods.getOrNull(index)?.takeIf { isLesson(subject) }?.let { Lesson(it, subject.trim(), listOf(selection.value)) }
            }
            UserRole.TEACHER -> data.periods.mapIndexedNotNull { index, period ->
                val matching = grades.entries.filter { (_, values) -> normalizeSubject(values.getOrNull(index).orEmpty()) == normalizeSubject(selection.value) }
                    .map { it.key }.sorted()
                if (matching.isEmpty()) null else Lesson(period, selection.value, matching)
            }
        }
    }

    fun allLessonsForDay(day: String): List<Lesson> = data.periods.mapIndexed { index, period ->
        data.schedule[day].orEmpty().mapNotNull { (grade, subjects) ->
            subjects.getOrNull(index)?.takeIf(::isLesson)?.let { Lesson(period, it.trim(), listOf(grade)) }
        }
    }.flatten()

    fun allLessonsByPeriod(day: String): List<Pair<Period, List<Lesson>>> = data.periods.mapIndexed { index, period ->
        period to data.schedule[day].orEmpty().mapNotNull { (grade, subjects) ->
            subjects.getOrNull(index)?.takeIf(::isLesson)?.let { Lesson(period, it.trim(), listOf(grade)) }
        }
    }

    fun status(now: LocalDateTime, selection: UserSelection): DayStatus {
        val date = now.toLocalDate()
        if (isHoliday(date)) return DayStatus.Holiday(holidayNote(date))
        val lessons = lessonsFor(date, selection)
        if (lessons.isEmpty()) return DayStatus.NoLessonsToday
        val time = now.toLocalTime()
        val current = lessons.firstOrNull { !time.isBefore(it.period.start) && time.isBefore(it.period.end) }
        if (current != null) {
            val next = lessons.firstOrNull { it.period.start.isAfter(current.period.start) }
            return DayStatus.InLesson(current, Duration.between(time, current.period.end).seconds.coerceAtLeast(0), next)
        }
        val next = lessons.firstOrNull { it.period.start.isAfter(time) }
        if (next != null) {
            val seconds = Duration.between(time, next.period.start).seconds.coerceAtLeast(0)
            return if (time.isBefore(data.periods.first().start)) DayStatus.BeforeStart(next) else DayStatus.Break(next, seconds)
        }
        return DayStatus.Finished
    }
}
