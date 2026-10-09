package com.schoolschedule.app

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import com.schoolschedule.app.notifications.AlarmReceiver
import com.schoolschedule.app.ui.AppController
import com.schoolschedule.app.ui.SchoolScheduleApp

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private var controller: AppController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AlarmReceiver.ensureChannel(this)
        requestDataAccessIfNeeded()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestExactAlarmPermissionIfNeeded()
        val appController = AppController(applicationContext)
        controller = appController
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                SchoolScheduleApp(appController)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the "Alarms & reminders" settings screen (or any
        // other trip outside the app) is the right time to re-schedule with
        // exact alarms if the permission was just granted.
        controller?.reload()
    }

    private fun requestDataAccessIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }

    // Without this, Android silently downgrades every alarm to an inexact one
    // that the OS is free to batch/delay by minutes — the cause of
    // notifications for two different periods arriving at the same time.
    private fun requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(AlarmManager::class.java)
            if (manager?.canScheduleExactAlarms() == false) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                } catch (_: Exception) { /* device doesn't expose this screen; alarms will fall back to inexact */ }
            }
        }
    }
}
