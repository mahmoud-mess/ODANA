package com.yuzi.odana

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.yuzi.odana.ml.AnomalyResult
import com.yuzi.odana.ml.AnomalySeverity

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * ANOMALY NOTIFIER
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Handles showing system notifications for detected anomalies.
 * Only notifies for HIGH severity by default to avoid notification spam.
 */
object AnomalyNotifier {
    
    private const val CHANNEL_ID = "odana_anomaly_channel"
    private const val CHANNEL_NAME = "Anomaly Alerts"
    private const val CHANNEL_DESCRIPTION = "Notifications for detected network anomalies"
    
    private var notificationId = 1000
    private var context: Context? = null
    
    // Configuration
    var isEnabled = true
    var minSeverity = AnomalySeverity.MEDIUM  // Notify for MEDIUM and HIGH (flood + scan)
    
    // Rate limiting - don't spam notifications
    private var lastNotificationTime = 0L
    private const val MIN_INTERVAL_MS = 3000L  // 3 seconds between notifications
    private var notificationsThisSession = 0
    private const val MAX_NOTIFICATIONS_PER_SESSION = 50  // Increased from 20
    
    /**
     * Initialize the notifier with the app context.
     * Creates the notification channel on Android O+.
     */
    fun initialize(appContext: Context) {
        context = appContext.applicationContext
        createNotificationChannel()
    }
    
    /**
     * Create the notification channel (required for Android O+).
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                enableLights(true)
                lightColor = 0xFF9333EA.toInt()  // Wisteria color
            }
            
            val notificationManager = context?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    /**
     * Show a notification for an anomaly if conditions are met.
     */
    fun notifyIfNeeded(anomaly: AnomalyResult) {
        val ctx = context ?: run {
            android.util.Log.w("AnomalyNotifier", "Context not initialized, skipping notification")
            return
        }
        
        // Check if notifications are enabled
        if (!isEnabled) {
            android.util.Log.d("AnomalyNotifier", "Notifications disabled")
            return
        }
        
        // Check severity threshold
        if (anomaly.severity.ordinal < minSeverity.ordinal) {
            android.util.Log.d("AnomalyNotifier", "Severity ${anomaly.severity} below threshold $minSeverity")
            return
        }
        
        // Rate limiting
        val now = System.currentTimeMillis()
        if (now - lastNotificationTime < MIN_INTERVAL_MS) {
            android.util.Log.d("AnomalyNotifier", "Rate limited (${now - lastNotificationTime}ms since last)")
            return
        }
        if (notificationsThisSession >= MAX_NOTIFICATIONS_PER_SESSION) {
            android.util.Log.d("AnomalyNotifier", "Session limit reached ($notificationsThisSession)")
            return
        }
        
        // Check notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                android.util.Log.w("AnomalyNotifier", "POST_NOTIFICATIONS permission not granted")
                return
            }
        }
        
        // Build and show notification
        android.util.Log.i("AnomalyNotifier", "Showing notification for ${anomaly.appName}: ${anomaly.severity}")
        showNotification(ctx, anomaly)
        lastNotificationTime = now
        notificationsThisSession++
    }
    
    private fun showNotification(context: Context, anomaly: AnomalyResult) {
        // Intent to open the app
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Could add extras to navigate to Alerts tab
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build the notification
        val title = when (anomaly.severity) {
            AnomalySeverity.HIGH -> "⚠️ Security Alert"
            AnomalySeverity.MEDIUM -> "🔔 Suspicious Activity"
            AnomalySeverity.LOW -> "ℹ️ Unusual Activity"
            AnomalySeverity.NONE -> "Network Activity"
        }
        
        val appName = anomaly.appName ?: "Unknown App"
        val reason = anomaly.reasons.firstOrNull() ?: "Anomaly detected"
        val text = "$appName: $reason"
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)  // Use app icon
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$text\n\nScore: ${(anomaly.score * 100).toInt()}%\nDestination: ${anomaly.flowKey}"
            ))
            .setPriority(when (anomaly.severity) {
                AnomalySeverity.HIGH -> NotificationCompat.PRIORITY_HIGH
                AnomalySeverity.MEDIUM -> NotificationCompat.PRIORITY_DEFAULT
                else -> NotificationCompat.PRIORITY_LOW
            })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 100, 250))
        
        // Color based on severity
        when (anomaly.severity) {
            AnomalySeverity.HIGH -> builder.setColor(0xFFEF4444.toInt())  // Red
            AnomalySeverity.MEDIUM -> builder.setColor(0xFFF59E0B.toInt())  // Amber
            else -> builder.setColor(0xFF9333EA.toInt())  // Wisteria
        }
        
        try {
            NotificationManagerCompat.from(context).notify(notificationId++, builder.build())
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }
    
    /**
     * Reset session counter (call on app start).
     */
    fun resetSession() {
        notificationsThisSession = 0
    }
    
    /**
     * Cancel all ODANA notifications.
     */
    fun cancelAll() {
        context?.let {
            NotificationManagerCompat.from(it).cancelAll()
        }
    }
}

