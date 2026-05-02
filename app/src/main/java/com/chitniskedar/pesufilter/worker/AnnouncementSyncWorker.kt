package com.chitniskedar.pesufilter.worker

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.chitniskedar.pesufilter.model.Announcement
import com.chitniskedar.pesufilter.network.Fetcher
import com.chitniskedar.pesufilter.parser.HtmlParser
import com.chitniskedar.pesufilter.utils.FilterManager
import com.chitniskedar.pesufilter.utils.NotificationHelper
import com.chitniskedar.pesufilter.utils.PreferencesManager
import java.net.URI

class AnnouncementSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    private val maxLoginPageHitsBeforeExpire = 3

    private val preferencesManager = PreferencesManager(appContext)
    private val fetcher = Fetcher()
    private val parser = HtmlParser()
    private val filterManager = FilterManager(preferencesManager)
    private val notificationHelper = NotificationHelper(appContext)

    override fun doWork(): Result {
        val cookie = preferencesManager.getBackendCookie()
        if (cookie.isNullOrBlank()) {
            preferencesManager.setLastSyncError("Session cookie missing. Please login once and wait a few seconds before refresh.")
            return Result.retry()
        }

        return try {
            val savedUrl = preferencesManager.getBackendUrl()
            val candidateUrls = buildList {
                add(savedUrl)
                add(canonicalizeAnnouncementUrl(savedUrl))
                add(PreferencesManager.DEFAULT_BACKEND_URL)
            }.distinct().filter { isAnnouncementUrl(it) }

            var parsedAnnouncements: List<Announcement> = emptyList()
            var successfulUrl: String? = null
            var loginPageDetectedInThisRun = false

            for (candidateUrl in candidateUrls) {
                val result = fetchAndParse(url = candidateUrl, cookie = cookie)
                if (result.isLoginPage) {
                    loginPageDetectedInThisRun = true
                    continue
                }
                if (result.isUnavailablePage) {
                    continue
                }
                if (result.announcements.isNotEmpty()) {
                    parsedAnnouncements = result.announcements
                    successfulUrl = candidateUrl
                    break
                }
            }

            if (!successfulUrl.isNullOrBlank() && successfulUrl != savedUrl) {
                preferencesManager.saveBackendUrl(successfulUrl)
            }

            if (parsedAnnouncements.isNotEmpty()) {
                preferencesManager.resetLoginPageHitCount()
            } else if (loginPageDetectedInThisRun) {
                val hitCount = preferencesManager.incrementLoginPageHitCount()
                if (hitCount >= maxLoginPageHitsBeforeExpire) {
                    preferencesManager.setLastSyncError("Session refresh needed. Open PESU login once, then refresh.")
                    notificationHelper.showSessionExpiredNotification()
                    return Result.retry()
                }
                preferencesManager.setLastSyncError("Session check pending. Keeping you signed in and retrying.")
                return Result.retry()
            }

            if (parsedAnnouncements.isEmpty()) {
                preferencesManager.setLastSyncError("No announcements found. Open PESU announcements once and refresh again.")
                return Result.retry()
            }
            preferencesManager.saveAnnouncements(parsedAnnouncements)

            val relevantAnnouncements = parsedAnnouncements.filter { announcement ->
                filterManager.shouldShow(
                    title = announcement.title,
                    fullText = announcement.fullText
                )
            }

            relevantAnnouncements.forEach { announcement ->
                val shouldNotify = !preferencesManager.hasSeenAnnouncement(announcement.stableId)
                if (shouldNotify) {
                    val wasPosted = notificationHelper.showAnnouncementNotification(
                        announcement = announcement,
                        priority = filterManager.priorityFor(announcement.title + "\n" + announcement.fullText)
                    )
                    if (wasPosted) {
                        preferencesManager.markAnnouncementSeen(announcement.stableId)
                    }
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

    private fun fetchAndParse(url: String, cookie: String): ParsedFetchResult {
        val response = fetcher.fetchHtml(url = url, cookie = cookie)
        val html = response.html
        val effectiveUrl = response.finalUrl.ifBlank { url }
        val isLoginPage = parser.isLoginPage(html)
        val isUnavailablePage = parser.isUnavailablePage(html)
        val parsedAnnouncements = if (isLoginPage || isUnavailablePage) emptyList() else parser.parseAnnouncements(html, effectiveUrl)
        return ParsedFetchResult(
            isLoginPage = isLoginPage,
            isUnavailablePage = isUnavailablePage,
            announcements = parsedAnnouncements
        )
    }

    private fun canonicalizeAnnouncementUrl(rawUrl: String): String {
        return runCatching {
            val uri = URI(rawUrl)
            val queryPairs = uri.rawQuery
                ?.split("&")
                ?.mapNotNull { pair ->
                    val index = pair.indexOf('=')
                    if (index <= 0) null else pair.substring(0, index) to pair.substring(index + 1)
                }
                .orEmpty()

            val menuId = queryPairs.firstOrNull { it.first == "menuId" }?.second
            if (menuId.isNullOrBlank()) {
                PreferencesManager.DEFAULT_BACKEND_URL
            } else {
                "https://www.pesuacademy.com/Academy/s/studentProfilePESUAdmin?menuId=$menuId&url=studentProfilePESUAdmin&controllerMode=6411&actionType=5&id=0&selectedData=0"
            }
        }.getOrDefault(PreferencesManager.DEFAULT_BACKEND_URL)
    }

    private fun isAnnouncementUrl(url: String): Boolean {
        val normalized = url.lowercase()
        return normalized.startsWith("https://www.pesuacademy.com/") &&
            normalized.contains("menuid=") &&
            (normalized.contains("studentprofilepesuadmin") || normalized.contains("actiontype=5"))
    }

    private data class ParsedFetchResult(
        val isLoginPage: Boolean,
        val isUnavailablePage: Boolean,
        val announcements: List<Announcement>
    )
}
