@file:OptIn(ExperimentalMaterial3Api::class)

package com.schoolschedule.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.schoolschedule.app.data.NotificationTestLogEntry
import com.schoolschedule.app.data.NotificationTestStore
import com.schoolschedule.app.data.PendingNotificationTest
import com.schoolschedule.app.notifications.NotificationScheduler
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")
private fun epochToLocal(millis: Long): LocalDateTime = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()

// Composable is defined as a top-level (not private) function in SchoolScheduleApp.kt's
// package so MainScaffold there can call it directly.
@Composable
fun NotificationLabScreen(controller: AppController, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var pending by remember { mutableStateOf(NotificationTestStore.pending(context)) }
    var log by remember { mutableStateOf(NotificationTestStore.readLog()) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Poll every second: cheap (tiny local file/prefs) and it's what lets a test
    // that just fired — while the user is sitting on this exact screen — show
    // up in the log automatically, no manual refresh needed.
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            pending = NotificationTestStore.pending(context)
            log = NotificationTestStore.readLog()
            delay(1000)
        }
    }

    fun addTest(atMillis: Long, label: String) {
        val id = UUID.randomUUID().toString()
        NotificationTestStore.addPending(context, PendingNotificationTest(id, label, atMillis))
        NotificationScheduler.scheduleTest(context, id, label, atMillis)
        pending = NotificationTestStore.pending(context)
    }

    fun cancelTest(id: String) {
        NotificationScheduler.cancelTest(context, id)
        NotificationTestStore.removePending(context, id)
        pending = NotificationTestStore.pending(context)
    }

    Scaffold(topBar = { TitleBar("اختبار الإشعارات", onBack = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxWidth().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text(
                    "جدول تنبيهًا تجريبيًا، أو راقب السجل بالأسفل — فيه الآن كل إشعارات الدوام الحقيقية تلقائيًا كمان (بداية/نهاية/قبل 5 دقائق)، مو بس الاختبارات. وقت الجدولة ووقت الوصول الفعلي يُسجَّلان بدقة الثانية لكل إشعار.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                )
            }

            item { LabSectionHeader("إضافة اختبار") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(1 to "دقيقة", 3 to "3 دقائق", 5 to "5 دقائق", 10 to "10 دقائق")) { (minutes, label) ->
                        AssistChip(onClick = { addTest(now + minutes * 60_000L, "بعد $label") }, label = { Text("بعد $label") })
                    }
                }
            }
            item {
                OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("اختر وقتًا مخصصًا")
                }
            }

            item { LabSectionHeader("اختبارات مجدولة (${pending.size})") }
            if (pending.isEmpty()) {
                item { Text("لا يوجد اختبار مجدول حاليًا.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(pending, key = { it.id }) { test ->
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(test.label, fontWeight = FontWeight.Bold)
                                val remaining = ((test.scheduledAtMillis - now) / 1000).coerceAtLeast(0)
                                Text(
                                    "الموعد: ${timeFmt.format(epochToLocal(test.scheduledAtMillis))} — متبقٍ ${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { cancelTest(test.id) }) { Icon(Icons.Default.Delete, "إلغاء", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }

            item { LabSectionHeader("سجل النتائج (${log.size})") }
            item {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    TextButton(onClick = { NotificationTestStore.clearLog(); log = emptyList() }) {
                        Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(4.dp)); Text("مسح السجل")
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("ملف السجل (لإرساله لي)", fontWeight = FontWeight.Bold)
                            Text(NotificationTestStore.logFilePath(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { clipboard.setText(AnnotatedString(NotificationTestStore.logFilePath())) }) { Icon(Icons.Default.ContentCopy, "نسخ المسار") }
                    }
                }
            }
            if (log.isEmpty()) {
                item { Text("لا توجد نتائج بعد.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(log, key = { it.id }) { entry -> LogRow(entry) }
            }
        }
    }

    if (showTimePicker) {
        val nowTime = LocalTime.now()
        val state = rememberTimePickerState(initialHour = nowTime.hour, initialMinute = nowTime.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("اختر وقت الاختبار") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val today = LocalDate.now()
                    var target = LocalDateTime.of(today, LocalTime.of(state.hour, state.minute))
                    if (!target.isAfter(LocalDateTime.now())) target = target.plusDays(1) // already passed today -> schedule for tomorrow
                    val millis = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    addTest(millis, "مخصص ${"%02d".format(state.hour)}:${"%02d".format(state.minute)}")
                    showTimePicker = false
                }) { Text("جدولة") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun LogRow(entry: NotificationTestLogEntry) {
    val delta = entry.deltaSeconds
    val deltaColor = when {
        delta <= 5 -> MaterialTheme.colorScheme.primary
        delta <= 60 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = deltaColor, modifier = Modifier.height(18.dp))
                Spacer(Modifier.width(6.dp))
                AssistChip(
                    onClick = {}, label = { Text(if (entry.isTest) "اختبار" else "حقيقي", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(24.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(entry.label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                Text(
                    if (delta < 60) "+${delta}ث" else "+${delta / 60}د ${delta % 60}ث",
                    fontWeight = FontWeight.Bold, color = deltaColor,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("الموعد: ${timeFmt.format(epochToLocal(entry.scheduledAtMillis))}   ←→   الوصول: ${timeFmt.format(epochToLocal(entry.deliveredAtMillis))}", style = MaterialTheme.typography.bodySmall)
            Divider(Modifier.padding(vertical = 6.dp))
            Text(
                "${entry.deviceManufacturer} ${entry.deviceModel} • أندرويد ${entry.androidRelease} • استثناء توفير البطارية: ${if (entry.batteryOptimizationIgnored) "نعم" else "لا"} • تنبيهات دقيقة: ${if (entry.canScheduleExactAlarms) "مفعّلة" else "غير مفعّلة"}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LabSectionHeader(title: String) = Text(
    title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
)
