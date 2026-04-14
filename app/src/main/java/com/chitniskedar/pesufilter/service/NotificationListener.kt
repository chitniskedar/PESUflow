package com.chitniskedar.pesufilter.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.chitniskedar.pesufilter.data.NotificationItem
import com.chitniskedar.pesufilter.utils.FilterManager
import com.chitniskedar.pesufilter.utils.NotificationHelper
import com.chitniskedar.pesufilter.utils.PreferencesManager

class NotificationListener : NotificationListenerService() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var filterManager: FilterManager
    private lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(applicationContext)
        filterManager = FilterManager(preferencesManager)
        notificationHelper = NotificationHelper(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) {
            return
        }

        val extras = sbn.notification.extras
        val title = extras.getString("android.title").orEmpty()
        val text = extras.getString("android.text").orEmpty()
        val packageName = sbn.packageName.orEmpty()
        val timestamp = sbn.postTime

        val result = filterManager.process(
            packageName = packageName,
            title = title,
            text = text
        )

        if (!result.isPesuNotification) {
            return
        }

        val message = buildString {
            append(title)
            if (title.isNotBlank() && text.isNotBlank()) {
                append(" - ")
            }
            append(text)
        }.ifBlank { "No notification text available" }

        val category = result.category ?: PreferencesManager.CATEGORY_GENERAL

        if (result.shouldShow) {
            preferencesManager.saveNotification(
                NotificationItem(
                    text = message,
                    category = category,
                    timestamp = timestamp,
                    isShown = true
                )
            )
        } else {
            cancelNotification(sbn.key)
            preferencesManager.saveNotification(
                NotificationItem(
                    text = message,
                    category = category,
                    timestamp = timestamp,
                    isShown = false
                )
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val packageName = sbn?.packageName.orEmpty()
        Log.d(TAG, "Notification removed from package=$packageName")
    }

    companion object {
        private const val TAG = "NotificationListener"
    }
}
