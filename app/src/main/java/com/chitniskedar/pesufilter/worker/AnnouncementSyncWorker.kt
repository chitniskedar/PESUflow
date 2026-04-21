package com.chitniskedar.pesufilter.worker

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.chitniskedar.pesufilter.network.Fetcher
import com.chitniskedar.pesufilter.parser.HtmlParser
import com.chitniskedar.pesufilter.utils.FilterManager
import com.chitniskedar.pesufilter.utils.NotificationHelper
import com.chitniskedar.pesufilter.utils.PreferencesManager

class AnnouncementSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    private val preferencesManager = PreferencesManager(appContext)
    private val fetcher = Fetcher()
    private val parser = HtmlParser()
    private val filterManager = FilterManager(preferencesManager)
    private val notificationHelper = NotificationHelper(appContext)

    override fun doWork(): Result {
        val cookie = preferencesManager.getBackendCookie()
        if (cookie.isNullOrBlank()) {
            expireSession("PESU session missing")
            return Result.failure()
        }

        return try {
            val backendUrl = preferencesManager.getBackendUrl()
            val html = fetcher.fetchHtml(url = backendUrl, cookie = cookie)
            if (parser.isLoginPage(html)) {
                expireSession("PESU session expired")
                return Result.failure()
            }

            val parsedAnnouncements = parser.parseAnnouncements(html, backendUrl)

            preferencesManager.saveAnnouncements(parsedAnnouncements)

            val relevantAnnouncements = parsedAnnouncements.filter { announcement ->
                filterManager.shouldShow(announcement.fullText)
            }

            relevantAnnouncements.forEach { announcement ->
                val shouldNotify = !preferencesManager.hasSeenAnnouncement(announcement.stableId)
                if (shouldNotify) {
                    notificationHelper.showAnnouncementNotification(
                        announcement = announcement,
                        priority = filterManager.priorityFor(announcement.fullText)
                    )
                    preferencesManager.markAnnouncementSeen(announcement.stableId)
                }
            }

            preferencesManager.setLastSyncError(null)
            preferencesManager.setLastSyncTimestamp(System.currentTimeMillis())
            Result.success()
        } catch (exception: Exception) {
            preferencesManager.setLastSyncError(exception.message ?: "Unknown sync error")
            Result.retry()
        }
    }

    private fun expireSession(message: String) {
        preferencesManager.clearSession()
        preferencesManager.setLastSyncError(message)
        notificationHelper.showSessionExpiredNotification()
    }
}
