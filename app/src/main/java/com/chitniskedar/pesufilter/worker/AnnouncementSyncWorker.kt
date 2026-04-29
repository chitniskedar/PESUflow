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
                add("https://www.pesuacademy.com/Academy/")
                add("https://www.pesuacademy.com/")
            }.distinct().toMutableList()

            var parsedAnnouncements: List<Announcement> = emptyList()
            var successfulUrl: String? = null
            var loginPageDetectedInThisRun = false

            var index = 0
            while (index < candidateUrls.size) {
                val candidateUrl = candidateUrls[index]
                index += 1
                val result = fetchAndParse(url = candidateUrl, cookie = cookie)
                if (result.isLoginPage) {
                    loginPageDetectedInThisRun = true
                    continue
                }
                result.discoveredUrls.forEach { discovered ->
                    val canonical = canonicalizeAnnouncementUrl(discovered)
                    if (!candidateUrls.contains(canonical)) {
                        candidateUrls.add(canonical)
                    }
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
                filterManager.shouldShow(announcement.title + "\n" + announcement.fullText)
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
        val html = fetcher.fetchHtml(url = url, cookie = cookie)
        val isLoginPage = parser.isLoginPage(html)
        val isUnavailablePage = parser.isUnavailablePage(html)
        val discoveredUrls = parser.extractAnnouncementUrls(html, url)
        val parsedAnnouncements = if (isLoginPage || isUnavailablePage) emptyList() else parser.parseAnnouncements(html, url)
        return ParsedFetchResult(
            isLoginPage = isLoginPage,
            isUnavailablePage = isUnavailablePage,
            discoveredUrls = discoveredUrls,
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
                "https://www.pesuacademy.com/Academy/s/studentProfilePESUAdmin?menuId=$menuId"
            }
        }.getOrDefault(PreferencesManager.DEFAULT_BACKEND_URL)
    }

    private data class ParsedFetchResult(
        val isLoginPage: Boolean,
        val isUnavailablePage: Boolean,
        val discoveredUrls: List<String>,
        val announcements: List<Announcement>
    )
}
