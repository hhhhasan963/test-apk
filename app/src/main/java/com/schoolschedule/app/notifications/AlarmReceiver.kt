package com.schoolschedule.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.schoolschedule.app.R
import com.schoolschedule.app.data.NotificationTestStore

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == NotificationScheduler.ACTION_REFRESH) {
            NotificationScheduler.reschedule(context)
            return
        }
        if (intent.action == NotificationScheduler.ACTION_TEST) {
            handleTest(context, intent)
            return
        }
        ensureChannel(context)
        val title = intent.getStringExtra("title") ?: "تنبيه الدوام"
        val message = intent.getStringExtra("message") ?: ""
        val notification = NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        val id = intent.getIntExtra("id", 1)
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
        // Log real class notifications the same way as test ones, so the delivery
        // delay can be checked against actual daily usage, not just manual tests.
        val scheduledAtMillis = intent.getLongExtra("scheduledAtMillis", -1L)
        if (scheduledAtMillis > 0) {
            NotificationTestStore.recordDelivery(context, id.toString(), "$title — $message", scheduledAtMillis, isTest = false)
        }
    }

    // Records exactly when this fired (vs. when it was scheduled for) into the
    // diagnostic log, then shows a small confirmation so the user gets instant
    // feedback without having to reopen the Notification Lab.
    private fun handleTest(context: Context, intent: Intent) {
        val testId = intent.getStringExtra("testId") ?: return
        val label = intent.getStringExtra("label") ?: "اختبار"
        val scheduledAtMillis = intent.getLongExtra("scheduledAtMillis", System.currentTimeMillis())
        val deliveredAtMillis = System.currentTimeMillis()
        NotificationTestStore.recordDelivery(context, testId, label, scheduledAtMillis, isTest = true)
        ensureChannel(context)
        val deltaSeconds = (deliveredAtMillis - scheduledAtMillis) / 1000
        val body = if (deltaSeconds <= 2) "وصل بالوقت تمامًا ✅" else "تأخر عن الموعد بـ $deltaSeconds ثانية"
        val notification = NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🧪 نتيجة اختبار: $label")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(testId.hashCode(), notification)
    }

    companion object {
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(NotificationScheduler.CHANNEL_ID, context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.notification_channel_description)
                }
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }
        }
    }
}
