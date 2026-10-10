@file:OptIn(ExperimentalMaterial3Api::class)

package com.schoolschedule.app.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.schoolschedule.app.R
import com.schoolschedule.app.data.AppPreferences
import com.schoolschedule.app.data.StorageRepository
import com.schoolschedule.app.domain.DataLoadResult
import com.schoolschedule.app.domain.DayStatus
import com.schoolschedule.app.domain.Lesson
import com.schoolschedule.app.domain.ScheduleData
import com.schoolschedule.app.domain.ScheduleEngine
import com.schoolschedule.app.domain.UserRole
import com.schoolschedule.app.domain.UserSelection
import com.schoolschedule.app.notifications.NotificationScheduler
import com.schoolschedule.app.ui.theme.LogoDeepBlue
import com.schoolschedule.app.ui.theme.LogoGold
import com.schoolschedule.app.ui.theme.LogoOrange
import com.schoolschedule.app.ui.theme.LogoSkyBlue
import com.schoolschedule.app.ui.theme.SchoolScheduleTheme
import com.schoolschedule.app.ui.theme.ThemeMode
import com.schoolschedule.app.ui.theme.subjectColor
import com.schoolschedule.app.ui.theme.subjectIconRes
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

enum class Screen { HOME, TODAY, TOMORROW, FULL, SETTINGS, NOTIFICATION_LAB }

class AppController(private val context: Context) {
    private val repository = StorageRepository(context)
    private val preferences = AppPreferences(context)
    var data by mutableStateOf<ScheduleData?>(null)
        private set
    var errors by mutableStateOf<List<String>>(emptyList())
        private set
    var selection by mutableStateOf(preferences.selection)
        private set
    var screen by mutableStateOf(Screen.HOME)
    var notifyStart by mutableStateOf(preferences.notifyStart)
    var notifyEnd by mutableStateOf(preferences.notifyEnd)
    var notifyBefore by mutableStateOf(preferences.notifyBefore)
    var themeMode by mutableStateOf(runCatching { ThemeMode.valueOf(preferences.themeMode) }.getOrDefault(ThemeMode.SYSTEM))

    init { reload() }
    fun path() = repository.dataPath()
    fun reload() {
        val result: DataLoadResult = repository.load()
        data = result.data
        errors = result.errors
        if (data != null && selection != null) safeReschedule()
    }
    fun restore() { errors = repository.restoreDefaults(); reload() }
    fun saveSelection(value: UserSelection) { preferences.selection = value; selection = value; screen = Screen.HOME; safeReschedule() }
    fun clearSelection() { preferences.selection = null; selection = null; safeCancelAll() }
    fun updateNotifications(start: Boolean = notifyStart, end: Boolean = notifyEnd, before: Boolean = notifyBefore) {
        notifyStart = start; notifyEnd = end; notifyBefore = before
        preferences.notifyStart = start; preferences.notifyEnd = end; preferences.notifyBefore = before
        safeReschedule()
    }
    // Scheduling notifications is a nice-to-have: a failure here (stale alarm
    // ids, OEM quirks, etc.) must never crash the whole screen on startup.
    private fun safeReschedule() { runCatching { NotificationScheduler.reschedule(context) } }
    private fun safeCancelAll() { runCatching { NotificationScheduler.cancelAll(context) } }
    fun updateThemeMode(mode: ThemeMode) { themeMode = mode; preferences.themeMode = mode.name }

    fun saveHoliday(date: java.time.LocalDate, note: String, previousDate: java.time.LocalDate? = null) {
        val current = data?.holidays.orEmpty().toMutableMap()
        if (previousDate != null && previousDate != date) current.remove(previousDate)
        current[date] = note.takeIf { it.isNotBlank() }
        if (repository.saveHolidays(current)) reload()
    }
    fun removeHoliday(date: java.time.LocalDate) {
        val current = data?.holidays.orEmpty().toMutableMap()
        current.remove(date)
        if (repository.saveHolidays(current)) reload()
    }
}

@Composable
fun SchoolScheduleApp(controller: AppController) {
    val darkTheme = when (controller.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    SchoolScheduleTheme(darkTheme = darkTheme, dynamicColor = false) {
        Crossfade(targetState = whichRoot(controller), label = "root") { root ->
            when (root) {
                Root.PROBLEM -> DataProblemScreen(controller)
                Root.SETUP -> SetupScreen(controller)
                Root.MAIN -> MainScaffold(controller)
            }
        }
    }
}

private enum class Root { PROBLEM, SETUP, MAIN }
private fun whichRoot(controller: AppController): Root = when {
    controller.data == null -> Root.PROBLEM
    controller.selection == null -> Root.SETUP
    else -> Root.MAIN
}

@Composable
private fun DataProblemScreen(controller: AppController) = Scaffold(topBar = { TitleBar("دوامي المدرسي") }) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("تعذر تحميل بيانات الدوام", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        controller.errors.forEach { Text("• $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 4.dp)) }
        Spacer(Modifier.height(18.dp))
        Text("مسار الملفات: ${controller.path()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        Button(onClick = controller::reload, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("إعادة تحميل ملفات البيانات") }
        OutlinedButton(onClick = controller::restore, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), shape = MaterialTheme.shapes.medium) { Icon(Icons.Default.Restore, null); Spacer(Modifier.width(8.dp)); Text("استعادة البيانات الافتراضية") }
    }
}

@Composable
private fun SetupScreen(controller: AppController) {
    val engine = remember(controller.data) { ScheduleEngine(requireNotNull(controller.data)) }
    var role by rememberSaveable { mutableStateOf<UserRole?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    Scaffold(topBar = { TitleBar("إعداد الدوام") }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(
                    if (role == null) "مرحبًا 👋 اختر نوع المستخدم" else if (role == UserRole.STUDENT) "اختر الصف" else "اختر المادة",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                )
            }
            item { Text("سيتم حفظ اختيارك محليًا ويمكن تغييره لاحقًا من الإعدادات.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (role == null) {
                item { OptionCard("طالب", "لعرض جدول صفك فقط", Icons.Default.School) { role = UserRole.STUDENT } }
                item { OptionCard("مدرس", "لعرض برنامج المادة التي تدرّسها", Icons.Default.Person) { role = UserRole.TEACHER } }
            } else {
                val values = if (role == UserRole.STUDENT) engine.grades() else engine.subjects()
                if (values.size > 8) item {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("بحث...") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                        shape = MaterialTheme.shapes.medium, singleLine = true,
                    )
                }
                val filtered = values.filter { it.contains(query.trim(), ignoreCase = true) }
                items(filtered) { value ->
                    OptionCard(
                        value, if (role == UserRole.STUDENT) "عرض جدول $value" else "برنامج مادة $value",
                        if (role == UserRole.STUDENT) Icons.Default.School else Icons.Default.Person,
                        subjectForIcon = if (role == UserRole.TEACHER) value else null,
                    ) {
                        controller.saveSelection(UserSelection(requireNotNull(role), value))
                    }
                }
                if (filtered.isEmpty()) item { EmptyCard("لا توجد نتائج مطابقة") }
                item { TextButton(onClick = { role = null; query = "" }) { Text("رجوع لاختيار النوع") } }
            }
        }
    }
}

private val bottomDestinations = listOf(
    Triple(Screen.HOME, "الرئيسية", Icons.Default.Home),
    Triple(Screen.TODAY, "اليوم", Icons.Default.CalendarToday),
    Triple(Screen.TOMORROW, "غدًا", Icons.Default.EventNote),
    Triple(Screen.FULL, "الجدول", Icons.Default.CalendarMonth),
    Triple(Screen.SETTINGS, "الإعدادات", Icons.Default.Settings),
)

@Composable
private fun MainScaffold(controller: AppController) {
    // The Notification Lab is a secondary/diagnostic screen reached from
    // Settings, not a primary destination — it hides the bottom bar and gets
    // its own back arrow instead, like a normal "sub-page".
    val isSubScreen = controller.screen == Screen.NOTIFICATION_LAB
    Scaffold(
        bottomBar = {
            if (!isSubScreen) NavigationBar {
                bottomDestinations.forEach { (screen, label, icon) ->
                    NavigationBarItem(
                        selected = controller.screen == screen,
                        onClick = { controller.screen = screen },
                        icon = { Icon(icon, label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            AnimatedContent(
                targetState = controller.screen,
                label = "screen",
                transitionSpec = {
                    // Slide toward/away from whichever side the new tab sits on
                    // in the bottom bar, instead of a flat cross-fade.
                    val fromIndex = bottomDestinations.indexOfFirst { it.first == initialState }
                    val toIndex = bottomDestinations.indexOfFirst { it.first == targetState }
                    val forward = toIndex >= fromIndex
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(tween(260)) { dir * it / 4 } + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(tween(200)) { -dir * it / 4 } + fadeOut(tween(200)))
                },
            ) { screen ->
                when (screen) {
                    Screen.HOME -> HomeScreen(controller)
                    Screen.TODAY -> TableScreen(controller, todayOnly = true)
                    Screen.TOMORROW -> DayAheadScreen(controller)
                    Screen.FULL -> TableScreen(controller, todayOnly = false)
                    Screen.SETTINGS -> SettingsScreen(controller)
                    Screen.NOTIFICATION_LAB -> NotificationLabScreen(controller) { controller.screen = Screen.SETTINGS }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(controller: AppController) {
    val data = requireNotNull(controller.data); val selection = requireNotNull(controller.selection); val engine = remember(data) { ScheduleEngine(data) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(1000) } }
    val status = engine.status(now, selection)
    val todayLessons = remember(now.toLocalDate(), data) { engine.lessonsFor(now.toLocalDate(), selection) }
    Scaffold(topBar = { TitleBar("دوامي المدرسي") }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { StaggeredEntrance(0) { HeaderDate(engine, now.toLocalDate(), selection) } }
            item {
                StaggeredEntrance(1) {
                    AnimatedContent(targetState = status::class, label = "status") { _ ->
                        CurrentStatusCard(status, selection.role == UserRole.TEACHER)
                    }
                }
            }
            if (todayLessons.isNotEmpty()) {
                item { StaggeredEntrance(2) { Text("خط سير اليوم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) } }
                item { StaggeredEntrance(3) { DayTimeline(todayLessons, now.toLocalTime(), selection.role == UserRole.TEACHER) } }
            }
        }
    }
}

@Composable
private fun DayTimeline(lessons: List<Lesson>, now: LocalTime, teacher: Boolean) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(lessons) { index, lesson ->
            StaggeredEntrance(index) {
            val isPast = now.isAfter(lesson.period.end)
            val isCurrent = !now.isBefore(lesson.period.start) && now.isBefore(lesson.period.end)
            val colors = subjectColor(lesson.subject, dark)
            val primaryText = if (teacher) lesson.grades.joinToString(" + ") else lesson.subject
            val secondaryText = if (teacher) "${lesson.subject} • ${lesson.period.start}" else "${lesson.period.start}"
            Card(
                modifier = Modifier.width(132.dp),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrent) MaterialTheme.colorScheme.primary else colors.container.copy(alpha = if (isPast) 0.45f else 1f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrent) 4.dp else 0.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isPast) Icon(Icons.Default.CheckCircle, null, tint = if (isCurrent) MaterialTheme.colorScheme.onPrimary else colors.onContainer, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "الحصة ${lesson.period.id}", style = MaterialTheme.typography.labelSmall,
                            color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else colors.onContainer,
                        )
                        Spacer(Modifier.weight(1f))
                        SubjectIcon(lesson.subject, 20.dp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        primaryText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium,
                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else colors.onContainer, maxLines = 1,
                    )
                    Text(
                        secondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f) else colors.onContainer.copy(alpha = 0.75f),
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun DayAheadScreen(controller: AppController) {
    val data = requireNotNull(controller.data); val selection = requireNotNull(controller.selection)
    val engine = remember(data) { ScheduleEngine(data) }
    val date = remember { LocalDate.now().plusDays(1) }
    val lessons = remember(date, data, selection) { engine.lessonsFor(date, selection) }
    Scaffold(topBar = { TitleBar("برنامج غدًا") }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { HeaderDate(engine, date, selection) }
            when {
                engine.isHoliday(date) -> item {
                    StatusMessageCard("غدًا عطلة 🌤️", engine.holidayNote(date) ?: "الجمعة والسبت والعطل الاستثنائية لا تحتوي على حصص.", offDay = true)
                }
                lessons.isEmpty() && selection.role == UserRole.TEACHER -> item {
                    StatusMessageCard("غدًا عطلتك المميزة 🎉", "استمتع بوقت فراغك", offDay = true)
                }
                lessons.isEmpty() -> item { EmptyCard("لا توجد حصص مسجلة") }
                else -> item { DayTable(engine.dayName(date), lessons, selection.role == UserRole.TEACHER, now = null) }
            }
        }
    }
}

@Composable
private fun StatusMessageCard(title: String, subtitle: String, offDay: Boolean) {
    val brush = if (offDay) Brush.linearGradient(listOf(LogoGold, LogoOrange)) else Brush.linearGradient(listOf(LogoSkyBlue, LogoDeepBlue))
    Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(brush)) {
        Image(
            painter = painterResource(R.drawable.pattern_decoration), contentDescription = null,
            modifier = Modifier.matchParentSize(), alpha = 0.16f, contentScale = ContentScale.Crop,
        )
        Column(Modifier.padding(20.dp), horizontalAlignment = if (offDay) Alignment.CenterHorizontally else Alignment.Start, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (offDay) {
                DayOffIllustration(modifier = Modifier.size(120.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White, textAlign = if (offDay) TextAlign.Center else TextAlign.Start)
            Text(subtitle, color = Color.White.copy(alpha = 0.92f), textAlign = if (offDay) TextAlign.Center else TextAlign.Start)
        }
    }
}

@Composable
private fun HeaderDate(engine: ScheduleEngine, date: LocalDate, selection: UserSelection) = Column {
    Text("${engine.dayName(date)} ${arabicDate(date)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (selection.role == UserRole.STUDENT) Icons.Default.School else Icons.Default.Person, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(if (selection.role == UserRole.STUDENT) "الصف: ${selection.value}" else "المادة: ${selection.value}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CurrentStatusCard(status: DayStatus, teacher: Boolean) {
    val isOffDay = status is DayStatus.Holiday || status is DayStatus.NoLessonsToday
    val brush = if (isOffDay) Brush.linearGradient(listOf(LogoGold, LogoOrange)) else Brush.linearGradient(listOf(LogoSkyBlue, LogoDeepBlue))
    val onColor = Color.White
    Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(brush)) {
        Image(
            painter = painterResource(R.drawable.pattern_decoration), contentDescription = null,
            modifier = Modifier.matchParentSize(), alpha = 0.16f, contentScale = ContentScale.Crop,
        )
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (status) {
                is DayStatus.Holiday -> {
                    Text("اليوم عطلة 🌤️", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = onColor)
                    Text(status.note ?: "الجمعة والسبت والعطل الاستثنائية لا تحتوي على حصص.", color = onColor.copy(alpha = 0.92f))
                }
                DayStatus.NoLessonsToday -> if (teacher) {
                    DayOffIllustration(modifier = Modifier.size(120.dp).align(Alignment.CenterHorizontally))
                    Text("اليوم عطلتك المميزة 🎉", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = onColor, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Text("استمتع بوقت فراغك", color = onColor.copy(alpha = 0.92f), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                } else {
                    Text("لا توجد لديك حصص اليوم", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = onColor)
                }
                DayStatus.Finished -> {
                    Text("انتهى الدوام اليوم ✅", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = onColor)
                    Text("يمكنك مراجعة جدول اليوم أو الجدول الكامل من الأسفل.", color = onColor.copy(alpha = 0.92f))
                }
                is DayStatus.BeforeStart -> {
                    Text("لم يبدأ الدوام بعد", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = onColor)
                    NextLesson(status.next, status.next.period.start, remaining = null, teacher = teacher, contentColor = onColor)
                }
                is DayStatus.Break -> {
                    Text("استراحة ☕", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = onColor)
                    status.next?.let { NextLesson(it, it.period.start, status.remainingSeconds, teacher, onColor) } ?: Text("لا توجد حصة قادمة اليوم", color = onColor)
                }
                is DayStatus.InLesson -> {
                    Text("الحصة الحالية", style = MaterialTheme.typography.labelLarge, color = onColor.copy(alpha = 0.85f))
                    LessonDetails(status.current, teacher, onColor, prominent = true)
                    val totalSeconds = java.time.Duration.between(status.current.period.start, status.current.period.end).seconds.coerceAtLeast(1)
                    val targetProgress = (1f - status.remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
                    // Animate between the per-second jumps so the ring sweeps smoothly
                    // instead of visibly ticking in discrete steps.
                    val animatedProgress by animateFloatAsState(targetProgress, animationSpec = tween(900), label = "ringProgress")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        // A clock-face ring, echoing the clock in the app logo, instead of a plain bar.
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(76.dp)) {
                            CircularProgressIndicator(
                                progress = { animatedProgress }, modifier = Modifier.fillMaxSize(), strokeWidth = 7.dp,
                                color = onColor, trackColor = onColor.copy(alpha = 0.25f),
                            )
                            AnimatedContent(
                                targetState = formatDurationCompact(status.remainingSeconds), label = "ringTime",
                                transitionSpec = { (slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(220))) togetherWith (slideOutVertically(tween(220)) { -it / 2 } + fadeOut(tween(220))) },
                            ) { text -> Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = onColor) }
                        }
                        Column {
                            Text("باقٍ على نهاية الحصة", color = onColor.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                            AnimatedContent(
                                targetState = formatDuration(status.remainingSeconds), label = "remainingText",
                                transitionSpec = { (slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(220))) togetherWith (slideOutVertically(tween(220)) { -it / 2 } + fadeOut(tween(220))) },
                            ) { text -> Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = onColor) }
                        }
                    }
                    Divider(Modifier.padding(vertical = 4.dp), color = onColor.copy(alpha = 0.3f))
                    Text("الحصة القادمة", style = MaterialTheme.typography.labelLarge, color = onColor.copy(alpha = 0.85f))
                    status.next?.let { NextLesson(it, it.period.start, null, teacher, onColor) } ?: Text("لا توجد حصة قادمة اليوم", color = onColor)
                }
            }
        }
    }
}

@Composable
private fun NextLesson(lesson: Lesson, starts: LocalTime, remaining: Long?, teacher: Boolean, contentColor: Color) {
    LessonDetails(lesson, teacher, contentColor)
    Text("تبدأ $starts", fontWeight = FontWeight.Medium, color = contentColor)
    if (remaining != null) Text("بعد ${formatDuration(remaining)}", color = contentColor.copy(alpha = 0.92f))
}

// For a teacher, the class/grade matters more in the moment than the subject
// (they already know what they teach) — so it's shown as the headline, with
// the subject as supporting detail. For a student it's the other way round.
@Composable
private fun LessonDetails(lesson: Lesson, teacher: Boolean, contentColor: Color, prominent: Boolean = false) {
    val headline = if (teacher) lesson.grades.joinToString(" + ") else lesson.subject
    val secondary = if (teacher) "${lesson.subject} — الحصة ${lesson.period.id}" else "الحصة ${lesson.period.id}"
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SubjectIcon(lesson.subject, if (prominent) 40.dp else 30.dp)
        Column {
            Text(headline, style = if (prominent) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = contentColor)
            Text(secondary, color = contentColor.copy(alpha = 0.9f))
        }
    }
}

@Composable
private fun TableScreen(controller: AppController, todayOnly: Boolean) {
    val data = requireNotNull(controller.data); val selection = requireNotNull(controller.selection); val engine = remember(data) { ScheduleEngine(data) }
    val days = if (todayOnly) listOf(engine.dayName(LocalDate.now())) else listOf("الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس")
    var teacherTab by rememberSaveable { mutableStateOf(0) }
    val title = if (todayOnly) "جدول اليوم" else if (selection.role == UserRole.TEACHER) "الجدول" else "الجدول الكامل"
    Scaffold(topBar = { TitleBar(title) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!todayOnly && selection.role == UserRole.TEACHER) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SegmentedButton(selected = teacherTab == 0, onClick = { teacherTab = 0 }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("كل الصفوف") }
                    SegmentedButton(selected = teacherTab == 1, onClick = { teacherTab = 1 }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("برنامجي الأسبوعي") }
                }
            }
            val today = LocalDate.now()
            LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (todayOnly && engine.isHoliday(today)) {
                    item { StatusMessageCard("اليوم عطلة 🌤️", engine.holidayNote(today) ?: "الجمعة والسبت والعطل الاستثنائية لا تحتوي على حصص.", offDay = true) }
                } else if (todayOnly && selection.role == UserRole.TEACHER && engine.lessonsForDay(days.first(), selection).isEmpty()) {
                    item { StatusMessageCard("اليوم عطلتك المميزة 🎉", "استمتع بوقت فراغك", offDay = true) }
                } else if (!todayOnly && selection.role == UserRole.TEACHER && teacherTab == 1) {
                    items(days) { day -> TeacherDayCard(day, engine.lessonsForDay(day, selection)) }
                } else items(days) { day ->
                    val now = if (todayOnly) LocalTime.now() else null
                    if (!todayOnly && selection.role == UserRole.TEACHER) PeriodGrid(day, engine.allLessonsByPeriod(day))
                    else DayTable(day, engine.lessonsForDay(day, selection), selection.role == UserRole.TEACHER, now)
                }
            }
        }
    }
}

@Composable
private fun TeacherDayCard(day: String, lessons: List<Lesson>) = Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(day, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            lessons.firstOrNull()?.let { SubjectIcon(it.subject, 28.dp) }
        }
        Spacer(Modifier.height(8.dp))
        if (lessons.isEmpty()) Text("لا توجد حصة للمادة", color = MaterialTheme.colorScheme.onSurfaceVariant)
        lessons.forEach { lesson ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PeriodChip(lesson.period.id)
                Spacer(Modifier.width(10.dp))
                Text(lesson.grades.joinToString(" + "), Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Text("${lesson.period.start}–${lesson.period.end}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PeriodGrid(day: String, rows: List<Pair<com.schoolschedule.app.domain.Period, List<Lesson>>>) = Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
    Column(Modifier.padding(16.dp)) {
        Text(day, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        rows.forEach { (period, lessons) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
                PeriodChip(period.id)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    if (lessons.isEmpty()) Text("لا توجد حصة", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else lessons.groupBy { it.subject }.forEach { (subject, subjectLessons) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SubjectIcon(subject, 20.dp)
                            Text(subject, fontWeight = FontWeight.Bold)
                        }
                        Text(subjectLessons.joinToString(" + ") { it.grades.first() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("${period.start}–${period.end}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (period != rows.last().first) Divider()
        }
    }
}

@Composable
private fun DayTable(day: String, lessons: List<Lesson>, teacher: Boolean, now: LocalTime?) = Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    Column(Modifier.padding(16.dp)) {
        Text(day, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        if (lessons.isEmpty()) Text("لا توجد حصص مسجلة", color = MaterialTheme.colorScheme.onSurfaceVariant)
        lessons.forEachIndexed { i, lesson ->
            val isCurrent = now != null && !now.isBefore(lesson.period.start) && now.isBefore(lesson.period.end)
            val colors = subjectColor(lesson.subject, dark)
            // Teachers care most about which class they're walking into; students care about the subject.
            val primaryText = if (teacher) lesson.grades.joinToString(" + ") else lesson.subject
            val secondaryText = if (teacher) lesson.subject else null
            Row(
                Modifier.fillMaxWidth()
                    .background(if (isCurrent) colors.container else Color.Transparent, RoundedCornerShape(12.dp))
                    .padding(vertical = 8.dp, horizontal = if (isCurrent) 8.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PeriodChip(lesson.period.id)
                Spacer(Modifier.width(10.dp))
                SubjectIcon(lesson.subject, 26.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(primaryText, fontWeight = FontWeight.Bold, color = if (isCurrent) colors.onContainer else MaterialTheme.colorScheme.onSurface)
                    secondaryText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (isCurrent) colors.onContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Text("${lesson.period.start}–${lesson.period.end}", style = MaterialTheme.typography.bodySmall, color = if (isCurrent) colors.onContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (i < lessons.lastIndex) Divider()
        }
    }
}

// Fades + slides each item in with a small per-index delay, so a screen's
// content arrives as a gentle cascade instead of popping in all at once.
@Composable
private fun StaggeredEntrance(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(index * 45L); visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 4 },
    ) { content() }
}

// A light "press" squish instead of (or alongside) the ripple, so every
// tappable card/row gives immediate physical feedback.
private fun Modifier.bounceClick(onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, animationSpec = tween(120), label = "bounce")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}

// The day-off illustration gets a slow, gentle vertical "breathing" float so
// the off-day cards don't feel like a static poster.
@Composable
private fun DayOffIllustration(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "dayOffBreathe")
    val offsetY by infinite.animateFloat(
        initialValue = -6f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dayOffBreatheY",
    )
    Image(
        painter = painterResource(R.drawable.day_off_illustration), contentDescription = null,
        modifier = modifier.offset(y = offsetY.dp),
    )
}

// Shows nothing (not a placeholder box) when the subject has no matching
// artwork yet, so layouts never show an empty/broken-looking square.
@Composable
private fun SubjectIcon(subject: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val res = subjectIconRes(subject)
    if (res != null) {
        Image(painter = painterResource(res), contentDescription = null, modifier = modifier.size(size))
    }
}

@Composable
private fun PeriodChip(id: String) = AssistChip(
    onClick = {}, label = { Text(id) },
    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
)

@Composable
private fun SettingsScreen(controller: AppController) {
    val holidays = controller.data?.holidays.orEmpty()
    var showDatePicker by remember { mutableStateOf(false) }
    // The date currently being edited in the note dialog; null = dialog closed.
    var editingDate by remember { mutableStateOf<LocalDate?>(null) }
    var noteDraft by remember { mutableStateOf("") }

    Scaffold(topBar = { TitleBar("الإعدادات") }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SectionHeader("المظهر") }
            item {
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(16.dp)) {
                        Text("وضع الألوان", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SegmentedButton(selected = controller.themeMode == ThemeMode.SYSTEM, onClick = { controller.updateThemeMode(ThemeMode.SYSTEM) }, shape = SegmentedButtonDefaults.itemShape(0, 3), icon = { Icon(Icons.Default.SettingsBrightness, null, modifier = Modifier.size(18.dp)) }) { Text("تلقائي") }
                            SegmentedButton(selected = controller.themeMode == ThemeMode.LIGHT, onClick = { controller.updateThemeMode(ThemeMode.LIGHT) }, shape = SegmentedButtonDefaults.itemShape(1, 3), icon = { Icon(Icons.Default.LightMode, null, modifier = Modifier.size(18.dp)) }) { Text("فاتح") }
                            SegmentedButton(selected = controller.themeMode == ThemeMode.DARK, onClick = { controller.updateThemeMode(ThemeMode.DARK) }, shape = SegmentedButtonDefaults.itemShape(2, 3), icon = { Icon(Icons.Default.DarkMode, null, modifier = Modifier.size(18.dp)) }) { Text("داكن") }
                        }
                    }
                }
            }
            item { SectionHeader("المستخدم") }
            item { SettingItem(Icons.Default.SwapHoriz, "تغيير نوع المستخدم أو الصف/المادة", controller.selection?.value.orEmpty()) { controller.clearSelection() } }
            item { SectionHeader("الإشعارات") }
            item { ToggleItem(Icons.Default.NotificationsActive, "إشعار عند بداية الحصة", controller.notifyStart) { controller.updateNotifications(start = it) } }
            item { ToggleItem(Icons.Default.NotificationsActive, "إشعار عند نهاية الحصة", controller.notifyEnd) { controller.updateNotifications(end = it) } }
            item { ToggleItem(Icons.Default.NotificationsActive, "إشعار قبل الحصة بـ5 دقائق", controller.notifyBefore) { controller.updateNotifications(before = it) } }
            item { SettingItem(Icons.Default.Science, "اختبار الإشعارات", "جدول تنبيهات تجريبية وسجّل الفرق الزمني بدقة") { controller.screen = Screen.NOTIFICATION_LAB } }

            item { SectionHeader("العطل") }
            item {
                OutlinedButton(
                    onClick = { editingDate = null; noteDraft = ""; showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("إضافة عطلة") }
            }
            if (holidays.isEmpty()) {
                item { Text("لا توجد عطل مضافة بعد.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp)) }
            } else {
                items(holidays.toSortedMap().entries.toList()) { (date, note) ->
                    Card(
                        Modifier.fillMaxWidth().bounceClick { editingDate = date; noteDraft = note.orEmpty() },
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(arabicDateWithYear(date), fontWeight = FontWeight.Bold)
                                Text(note?.takeIf { it.isNotBlank() } ?: "عطلة بدون سبب محدد", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { controller.removeHoliday(date) }) { Icon(Icons.Default.Delete, "حذف", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }

            item { SectionHeader("البيانات") }
            item { SettingItem(Icons.Default.Refresh, "إعادة تحميل ملفات البيانات", "لتطبيق تغييرات JSON الخارجية") { controller.reload() } }
            item { SettingItem(Icons.Default.Restore, "استعادة البيانات الافتراضية", "يستبدل ملفات JSON الحالية") { controller.restore() } }
            item {
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("مسار ملفات البيانات", fontWeight = FontWeight.Bold)
                        Text(controller.path(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        editingDate = picked
                        noteDraft = holidays[picked].orEmpty()
                    }
                    showDatePicker = false
                }) { Text("التالي") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("إلغاء") } },
        ) { DatePicker(state = state) }
    }

    editingDate?.let { date ->
        val isExisting = holidays.containsKey(date)
        AlertDialog(
            onDismissRequest = { editingDate = null },
            title = { Text(if (isExisting) "تعديل العطلة" else "إضافة عطلة") },
            text = {
                Column {
                    Text(arabicDateWithYear(date), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = noteDraft, onValueChange = { noteDraft = it },
                        label = { Text("السبب (اختياري)") }, placeholder = { Text("مثلًا: عطلة بمناسبة يوم العمال العالمي") },
                        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { controller.saveHoliday(date, noteDraft, previousDate = date); editingDate = null }) { Text("حفظ") }
            },
            dismissButton = {
                Row {
                    if (isExisting) TextButton(onClick = { controller.removeHoliday(date); editingDate = null }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { editingDate = null }) { Text("إلغاء") }
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) = Text(
    title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
)

@Composable
private fun SettingItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) = Card(Modifier.fillMaxWidth().bounceClick(onClick), shape = MaterialTheme.shapes.medium) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun ToggleItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, checked: Boolean, onChange: (Boolean) -> Unit) = Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Text(title, Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun OptionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, subjectForIcon: String? = null, click: () -> Unit) = Card(
    Modifier.fillMaxWidth().bounceClick(click), shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
) {
    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        // Prefer the subject's own illustration when we have one; the generic
        // Material icon is only a fallback (role picker, unmapped subjects).
        val subjectRes = subjectForIcon?.let { subjectIconRes(it) }
        Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary), contentAlignment = Alignment.Center) {
            if (subjectRes != null) {
                Image(painter = painterResource(subjectRes), contentDescription = null, modifier = Modifier.size(32.dp))
            } else {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondary)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun EmptyCard(text: String) = Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TitleBar(title: String, action: @Composable (() -> Unit)? = null, onBack: (() -> Unit)? = null) = CenterAlignedTopAppBar(
    title = { Text(title, fontWeight = FontWeight.Bold) },
    navigationIcon = {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
    },
    actions = { action?.invoke() },
    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
)

private fun formatDuration(seconds: Long): String { val m = seconds / 60; val s = seconds % 60; return if (m > 0) "$m دقيقة${if (s > 0) " و$s ثانية" else ""}" else "$s ثانية" }
// Compact "MM:SS" for inside the small progress ring, where a full sentence won't fit.
private fun formatDurationCompact(seconds: Long): String { val m = seconds / 60; val s = seconds % 60; return "%d:%02d".format(m, s) }
private val arabicMonths = listOf("", "كانون الثاني", "شباط", "آذار", "نيسان", "أيار", "حزيران", "تموز", "آب", "أيلول", "تشرين الأول", "تشرين الثاني", "كانون الأول")
private fun arabicDate(date: LocalDate): String = "${date.dayOfMonth} ${arabicMonths[date.monthValue]}"
private fun arabicDateWithYear(date: LocalDate): String = "${date.dayOfMonth} ${arabicMonths[date.monthValue]} ${date.year}"
