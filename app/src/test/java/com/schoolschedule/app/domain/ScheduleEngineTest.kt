package com.schoolschedule.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ScheduleEngineTest {
    private val engine = ScheduleEngine(ScheduleData(
        schedule = mapOf("الأحد" to mapOf(
            "سابع بنات" to listOf("عربي", "لغة"),
            "ثامن" to listOf("عربي", "علوم")
        )),
        periods = listOf(Period("1", LocalTime.of(7,30), LocalTime.of(8,10)), Period("2", LocalTime.of(8,20), LocalTime.of(9,0))),
        holidays = mapOf(LocalDate.of(2026, 4, 12) to "عطلة بمناسبة يوم العمال العالمي")
    ))

    @Test fun `teacher matches merge grades in same period`() {
        val lessons = engine.lessonsForDay("الأحد", UserSelection(UserRole.TEACHER, "عربي"))
        assertEquals(1, lessons.size)
        assertEquals(listOf("ثامن", "سابع بنات"), lessons.first().grades)
    }

    @Test fun `student sees own grade only`() {
        val lessons = engine.lessonsForDay("الأحد", UserSelection(UserRole.STUDENT, "سابع بنات"))
        assertEquals(listOf("عربي", "لغة"), lessons.map { it.subject })
        assertTrue(lessons.all { it.grades == listOf("سابع بنات") })
    }

    @Test fun `exceptional holiday supersedes active day`() {
        val state = engine.status(LocalDateTime.of(2026, 4, 12, 7, 40), UserSelection(UserRole.STUDENT, "سابع بنات"))
        assertTrue(state is DayStatus.Holiday && state.note == "عطلة بمناسبة يوم العمال العالمي")
    }

    @Test fun `teacher with no lessons that day gets NoLessonsToday`() {
        val state = engine.status(LocalDateTime.of(2026, 3, 2, 8, 0), UserSelection(UserRole.TEACHER, "رياضيات"))
        assertEquals(DayStatus.NoLessonsToday, state)
    }

    @Test fun `break shows next lesson`() {
        val state = engine.status(LocalDateTime.of(2026, 3, 1, 8, 15), UserSelection(UserRole.STUDENT, "سابع بنات"))
        assertTrue(state is DayStatus.Break && state.next?.subject == "لغة")
    }
}
