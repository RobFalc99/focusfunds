package com.focusfunds.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.focusfunds.app.ui.MainActivity
import com.focusfunds.app.data.FocusFundsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FocusForegroundService : LifecycleService() {

    private lateinit var repository: FocusFundsRepository
    private var updateJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "focus_mode_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_FOCUS"
        const val ACTION_STOP = "ACTION_STOP_FOCUS"
    }

    override fun onCreate() {
        super.onCreate()
        repository = FocusFundsRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        when (intent?.action) {
            ACTION_START -> startFocus()
            ACTION_STOP -> stopFocus()
        }

        return START_STICKY
    }

    private fun startFocus() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FocusFunds::FocusModeLock").apply {
            acquire(2 * 60 * 60 * 1000L)
        }

        lifecycleScope.launch {
            val state = repository.getWalletState()
            if (!state.inFocusMode) {
                repository.updateWalletState(
                    state.copy(
                        inFocusMode = true,
                        focusStartTimestamp = System.currentTimeMillis()
                    )
                )
            }
            startForeground(NOTIFICATION_ID, buildNotification(0L, 0.0))
            startTickTimer()
        }
    }

    private fun startTickTimer() {
        updateJob?.cancel()
        updateJob = lifecycleScope.launch {
            val state = repository.getWalletState()
            val start = state.focusStartTimestamp
            while (true) {
                val elapsedMs = System.currentTimeMillis() - start
                val elapsedMinutes = elapsedMs / 60000.0
                val earnedFunds = elapsedMinutes * 0.1
                
                val notification = buildNotification(elapsedMs, earnedFunds)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)
                
                delay(6000)
            }
        }
    }

    private fun stopFocus() {
        updateJob?.cancel()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        
        lifecycleScope.launch {
            val state = repository.getWalletState()
            if (state.inFocusMode) {
                val elapsedMs = System.currentTimeMillis() - state.focusStartTimestamp
                val elapsedMinutes = (elapsedMs / 60000.0).coerceAtLeast(0.0)
                val earnedFunds = elapsedMinutes * 0.1
                
                val minutesInt = elapsedMinutes.toInt()
                val secondsInt = ((elapsedMinutes - minutesInt) * 60).toInt()
                val durationStr = if (minutesInt > 0) "${minutesInt}m ${secondsInt}s" else "${secondsInt}s"

                repository.addFocusFunds(earnedFunds, "Focus Session ($durationStr)")
                repository.updateWalletState(
                    state.copy(
                        inFocusMode = false,
                        focusStartTimestamp = 0L
                    )
                )
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildNotification(elapsedMs: Long, earnedFunds: Double): Notification {
        val totalSecs = elapsedMs / 1000
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60
        val timeStr = String.format("%02d:%02d:%02d", hours, minutes, seconds)
        val fundsStr = String.format("%.2f FF", earnedFunds)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Focus Mode Active")
            .setContentText("Duration: $timeStr | +$fundsStr earned")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Focus Mode Notification Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }
}
