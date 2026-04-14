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
        val backendUrl = preferencesManager.getBackendUrl()
        if (backendUrl.isBlank()) {
            preferencesManager.setLastSyncError("Missing backend URL")
            return Result.failure()
        }

        return try {
            val html = fetcher.fetchHtml(
                url = backendUrl,
                cookie = preferencesManager.getBackendCookie()
            )

            val parsedAnnouncements = parser.parseAnnouncements(html)
            val relevantAnnouncements = parsedAnnouncements.filter { announcement ->
                filterManager.shouldShow("${announcement.title} ${announcement.date}")
            }

            preferencesManager.saveAnnouncements(relevantAnnouncements)

            relevantAnnouncements.forEach { announcement ->
                if (!preferencesManager.hasSeenAnnouncement(announcement.stableId)) {
                    notificationHelper.showAnnouncementNotification(announcement)
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
}
