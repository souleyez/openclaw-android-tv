package com.openclaw.assistant.openclaw_android_tv_client

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class VoiceStandbyForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                updateStandbyState(false, "Background standby disabled")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_SIMULATE_HOTWORD -> {
                val commandText =
                    intent.getStringExtra(EXTRA_COMMAND_TEXT)
                        ?: "Open YouTube"
                simulateHotwordTrigger(commandText)
                startForeground(NOTIFICATION_ID, buildNotification(lastStatus))
            }
            ACTION_UPDATE_STATUS -> {
                val statusText =
                    intent.getStringExtra(EXTRA_STATUS_TEXT)
                        ?: lastStatus
                updateStandbyState(true, statusText)
                startForeground(NOTIFICATION_ID, buildNotification(statusText))
            }
            else -> {
                val statusText =
                    intent?.getStringExtra(EXTRA_STATUS_TEXT)
                        ?: "Background standby armed. Waiting for voice input."
                updateStandbyState(true, statusText)
                startForeground(NOTIFICATION_ID, buildNotification(statusText))
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRunning) {
            updateStandbyState(false, "Background standby stopped")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(statusText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("OpenClaw Standby")
            .setContentText(statusText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            "OpenClaw Standby",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows when OpenClaw is waiting in background standby mode."
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun updateStandbyState(enabled: Boolean, statusText: String) {
        isRunning = enabled
        lastStatus = statusText
    }

    private fun simulateHotwordTrigger(commandText: String) {
        updateStandbyState(true, "Hotword detected. Executing $commandText")
        pendingWakeCommand = commandText
        pendingWakeSource =
            if (hasPrivilegedHotwordPath) "system_hotword_stub" else "foreground_hotword_stub"

        val packageManager = applicationContext.packageManager
        val launchIntent =
            packageManager.getLaunchIntentForPackage(applicationContext.packageName)?.apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP,
                )
                putExtra(EXTRA_WAKE_COMMAND, commandText)
                putExtra(EXTRA_WAKE_SOURCE, pendingWakeSource)
            }
        if (launchIntent != null) {
            startActivity(launchIntent)
        }
    }

    companion object {
        private const val CHANNEL_ID = "openclaw_voice_standby"
        private const val NOTIFICATION_ID = 3107
        private const val EXTRA_STATUS_TEXT = "status_text"
        private const val EXTRA_COMMAND_TEXT = "command_text"
        private const val ACTION_START =
            "com.openclaw.assistant.openclaw_android_tv_client.action.START_STANDBY"
        private const val ACTION_STOP =
            "com.openclaw.assistant.openclaw_android_tv_client.action.STOP_STANDBY"
        private const val ACTION_SIMULATE_HOTWORD =
            "com.openclaw.assistant.openclaw_android_tv_client.action.SIMULATE_HOTWORD"
        private const val ACTION_UPDATE_STATUS =
            "com.openclaw.assistant.openclaw_android_tv_client.action.UPDATE_STATUS"
        const val EXTRA_WAKE_COMMAND =
            "com.openclaw.assistant.openclaw_android_tv_client.extra.WAKE_COMMAND"
        const val EXTRA_WAKE_SOURCE =
            "com.openclaw.assistant.openclaw_android_tv_client.extra.WAKE_SOURCE"

        @Volatile
        var isRunning: Boolean = false

        @Volatile
        var lastStatus: String = "Background standby disabled"

        @Volatile
        var pendingWakeCommand: String? = null

        @Volatile
        var pendingWakeSource: String = "none"

        @Volatile
        var hasPrivilegedHotwordPath: Boolean = false

        fun start(context: Context, statusText: String) {
            val intent = Intent(context, VoiceStandbyForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_STATUS_TEXT, statusText)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateStatus(context: Context, statusText: String) {
            val intent = Intent(context, VoiceStandbyForegroundService::class.java).apply {
                action = ACTION_UPDATE_STATUS
                putExtra(EXTRA_STATUS_TEXT, statusText)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun simulateHotwordTrigger(context: Context, commandText: String) {
            val intent = Intent(context, VoiceStandbyForegroundService::class.java).apply {
                action = ACTION_SIMULATE_HOTWORD
                putExtra(EXTRA_COMMAND_TEXT, commandText)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, VoiceStandbyForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
