package com.schoolschedule.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.schoolschedule.app.R

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** container / on-container pairs — vivid enough to scan quickly, calm enough for daily use. */
private val lightSubjectPalette = listOf(
    Color(0xFFD3E4FA) to Color(0xFF08304F),
    Color(0xFFD7ECE3) to Color(0xFF0E2F26),
    Color(0xFFFBE2C6) to Color(0xFF3E2405),
    Color(0xFFF3D9E4) to Color(0xFF4A1030),
    Color(0xFFE3DDF7) to Color(0xFF2A2060),
    Color(0xFFDCEBC9) to Color(0xFF203611),
    Color(0xFFF7DCC9) to Color(0xFF4A2607),
    Color(0xFFCFEBEA) to Color(0xFF07403D),
)

private val darkSubjectPalette = listOf(
    Color(0xFF17456A) to Color(0xFFD3E4FA),
    Color(0xFF224A3D) to Color(0xFFD7ECE3),
    Color(0xFF5E3E10) to Color(0xFFFBE2C6),
    Color(0xFF5B2439) to Color(0xFFF3D9E4),
    Color(0xFF362A66) to Color(0xFFE3DDF7),
    Color(0xFF31481C) to Color(0xFFDCEBC9),
    Color(0xFF5C3714) to Color(0xFFF7DCC9),
    Color(0xFF124F4B) to Color(0xFFCFEBEA),
)

data class SubjectColor(val container: Color, val onContainer: Color)

fun subjectColor(subject: String, dark: Boolean): SubjectColor {
    val palette = if (dark) darkSubjectPalette else lightSubjectPalette
    val index = (subject.trim().sumOf { it.code } % palette.size).let { if (it < 0) it + palette.size else it }
    val (container, onContainer) = palette[index]
    return SubjectColor(container, onContainer)
}

// Matches the exact subject strings used in schedule.json. French currently
// reuses the English icon and "معلوماتية" (computer science) reuses the
// science icon until dedicated artwork exists for them.
fun subjectIconRes(subject: String): Int? = when (subject.trim()) {
    "عربي" -> R.drawable.subject_arabic
    "لغة", "انجليزي", "إنجليزي", "فرنسي" -> R.drawable.subject_english
    "رياضيات" -> R.drawable.subject_math
    "علوم", "معلوماتية" -> R.drawable.subject_science
    "اجتماعيات" -> R.drawable.subject_social
    "اسلامية", "إسلامية" -> R.drawable.subject_islamic
    "رياضة" -> R.drawable.subject_sports
    else -> null
}
