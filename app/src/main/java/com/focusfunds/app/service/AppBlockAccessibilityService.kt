package com.focusfunds.app.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.focusfunds.app.data.ActiveUnlockSession
import com.focusfunds.app.data.FocusFundsRepository
import com.focusfunds.app.ui.MainActivity
import com.focusfunds.app.ui.POSOverlayContent
import kotlinx.coroutines.*

class AppBlockAccessibilityService : AccessibilityService() {

    private lateinit var repository: FocusFundsRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var overlayView: View? = null
    private val windowManager by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private var currentActivePackage: String? = null
    private val activeJobs = mutableMapOf<String, List<Job>>()

    companion object {
        const val WARNING_CHANNEL_ID = "app_expiry_warning_channel"
        const val WARNING_NOTIFICATION_ID = 2002
        
        @Volatile
        var instance: AppBlockAccessibilityService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        repository = FocusFundsRepository(applicationContext)
        instance = this
        createNotificationChannel()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        
        serviceScope.launch {
            val sessions = repository.getAllSessions()
            val now = System.currentTimeMillis()
            for (session in sessions) {
                val remainingMs = session.unlockEndTime - now
                if (remainingMs > 0) {
                    scheduleUnlockTimer(session.packageName, (remainingMs / 60000.0).coerceAtLeast(0.1))
                } else {
                    repository.deleteSession(session.packageName)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            currentActivePackage = packageName

            if (packageName == "com.focusfunds.app" || 
                packageName == "com.android.systemui" || 
                packageName == "android" || 
                packageName.contains("launcher")
            ) {
                return
            }

            serviceScope.launch {
                val isBlocked = repository.isAppBlocked(packageName)
                if (isBlocked) {
                    val session = repository.getSession(packageName)
                    val now = System.currentTimeMillis()
                    if (session == null || session.unlockEndTime <= now) {
                        withContext(Dispatchers.Main) {
                            showPOSOverlay(packageName)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            dismissPOSOverlay()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        dismissPOSOverlay()
                    }
                }
            }
        }
    }

    fun showPOSOverlay(packageName: String) {
        if (overlayView != null) return

        val layoutParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }

        val lifecycleOwner = MyServiceLifecycleOwner()
        lifecycleOwner.onCreate()
        lifecycleOwner.onStart()
        lifecycleOwner.onResume()

        val savedStateRegistryOwner = MySavedStateRegistryOwner(lifecycleOwner)

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
            
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) {
                        goHome()
                        dismissPOSOverlay()
                    }
                    true
                } else {
                    false
                }
            }

            setContent {
                POSOverlayContent(
                    packageName = packageName,
                    appName = getAppName(packageName),
                    onUnlockSuccess = { minutes ->
                        serviceScope.launch {
                            val durationMs = minutes * 60 * 1000L
                            val endTime = System.currentTimeMillis() + durationMs
                            repository.insertSession(
                                ActiveUnlockSession(
                                    packageName = packageName,
                                    unlockEndTime = endTime,
                                    isEmergency = false
                                )
                            )
                            scheduleUnlockTimer(packageName, minutes.toDouble())
                            withContext(Dispatchers.Main) {
                                dismissPOSOverlay()
                            }
                        }
                    },
                    onEmergencyUnlock = {
                        serviceScope.launch {
                            val durationMs = 5 * 60 * 1000L // 5 minutes standard emergency
                            val endTime = System.currentTimeMillis() + durationMs
                            repository.insertSession(
                                ActiveUnlockSession(
                                    packageName = packageName,
                                    unlockEndTime = endTime,
                                    isEmergency = true
                                )
                            )
                            repository.deductFocusFunds(5.0, "Emergency Unlock ($packageName)", isEmergency = true)
                            scheduleUnlockTimer(packageName, 5.0)
                            withContext(Dispatchers.Main) {
                                dismissPOSOverlay()
                            }
                        }
                    },
                    onCancel = {
                        goHome()
                        dismissPOSOverlay()
                    }
                )
            }
        }

        overlayView = composeView
        windowManager.addView(composeView, layoutParams)
    }

    fun dismissPOSOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Ignore if view not attached
            }
            overlayView = null
        }
    }

    private fun scheduleUnlockTimer(packageName: String, minutes: Double) {
        // Cancel previous timers for this app
        activeJobs[packageName]?.forEach { it.cancel() }

        val durationMs = (minutes * 60000L).toLong()
        val appName = getAppName(packageName)

        // 1. Warning Notification Job (1 min before expiry)
        val warningDelay = durationMs - 60000L
        val warningJob = serviceScope.launch {
            if (warningDelay > 0) {
                delay(warningDelay)
                sendWarningNotification(packageName, appName)
            }
        }

        // 2. Lock Job
        val lockJob = serviceScope.launch {
            delay(durationMs)
            repository.deleteSession(packageName)
            if (currentActivePackage == packageName) {
                withContext(Dispatchers.Main) {
                    showPOSOverlay(packageName)
                }
            }
        }

        activeJobs[packageName] = listOf(warningJob, lockJob)
    }

    private fun sendWarningNotification(packageName: String, appName: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, WARNING_CHANNEL_ID)
            .setContentTitle("FocusFunds Lock Warning")
            .setContentText("$appName will lock in 1 minute. Save your work!")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(packageName.hashCode(), notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                WARNING_CHANNEL_ID,
                "App Lock Expiry Warning Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun goHome() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast('.')
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        dismissPOSOverlay()
        instance = null
    }
}


