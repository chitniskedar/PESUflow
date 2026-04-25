package com.chitniskedar.pesufilter.utils

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
import com.chitniskedar.pesufilter.R
import com.chitniskedar.pesufilter.auth.LoginActivity
import com.chitniskedar.pesufilter.model.Announcement
import com.chitniskedar.pesufilter.ui.MainActivity
import com.chitniskedar.pesufilter.utils.FilterManager.AnnouncementPriority

class NotificationHelper(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createNotificationChannel()
    }

    fun showAnnouncementNotification(
        announcement: Announcement,
        priority: AnnouncementPriority = AnnouncementPriority.HIGH
    ): Boolean {
        if (!canPostNotifications()) {
            return false
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            1001,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = listOf(announcement.date, announcement.fullText)
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .trim()
            .take(MAX_BODY_LENGTH)
            .ifBlank { context.getString(R.string.announcement_details_unavailable) }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_filter)
            .setContentTitle(announcement.title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(
                when (priority) {
                    AnnouncementPriority.HIGH -> NotificationCompat.PRIORITY_HIGH
                    AnnouncementPriority.MEDIUM -> NotificationCompat.PRIORITY_DEFAULT
                    AnnouncementPriority.LOW -> NotificationCompat.PRIORITY_LOW
                }
            )
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(announcement.stableId.hashCode(), notification)
        return true
    }

    fun showSessionExpiredNotification(): Boolean {
        if (!canPostNotifications()) {
            return false
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            1002,
            Intent(context, LoginActivity::class.java).apply {
                putExtra(LoginActivity.EXTRA_FORCE_RELOGIN, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_filter)
            .setContentTitle(context.getString(R.string.session_expired_title))
            .setContentText(context.getString(R.string.session_expired_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        notificationManager.notify(SESSION_EXPIRED_NOTIFICATION_ID, notification)
        return true
    }

    fun canPostNotifications(): Boolean {
        val hasRuntimePermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        if (!hasRuntimePermission || !notificationManager.areNotificationsEnabled()) {
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channelImportance = manager.getNotificationChannel(CHANNEL_ID)?.importance
            if (channelImportance == NotificationManager.IMPORTANCE_NONE) {
                return false
            }
        }

        return true
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_description)
            enableLights(true)
            enableVibration(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "pesuflow_announcements"
        private const val MAX_BODY_LENGTH = 280
        private const val SESSION_EXPIRED_NOTIFICATION_ID = 10_002
    }
}
