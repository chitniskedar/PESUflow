package com.chitniskedar.pesufilter.parser

import com.chitniskedar.pesufilter.model.Announcement
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class HtmlParser {

    fun parseAnnouncements(html: String, baseUrl: String = ""): List<Announcement> {
        if (html.isBlank()) {
            return emptyList()
        }
        if (!isLikelyAnnouncementPage(html, baseUrl)) {
            return emptyList()
        }

        val doc = Jsoup.parse(html, baseUrl)
        val announcements = doc.select(
            ".elem-info-wrapper, .announcement-item, .media, .list-group-item, .card, tr, li, .panel-body > div"
        )
            .toList()
            .filter { element -> isLikelyAnnouncementEntry(element) }

        return announcements.mapNotNull { element ->
            val rawTitle = extractTitle(element)
            val date = extractDate(element)
            val fullText = cleanFullText(element.text(), rawTitle, date)
            val cleanTitle = cleanTitle(rawTitle)

            if (cleanTitle.isBlank() && date.isBlank() && fullText.isBlank()) {
                null
            } else {
                Announcement(
                    title = cleanTitle.ifBlank { "Untitled announcement" },
                    date = date.ifBlank { "Date unavailable" },
                    fullText = fullText.ifBlank { cleanTitle.ifBlank { "Announcement details unavailable" } }
                )
            }
        }.distinctBy { it.stableId }
    }

    fun isLoginPage(html: String): Boolean {
        if (html.isBlank()) {
            return false
        }

        val doc = Jsoup.parse(html)
        val hasPasswordField = doc.select("input[type=password]").isNotEmpty()
        val hasUsernameField = doc.select("input[name*=user i], input[id*=user i], input[name*=login i], input[id*=login i], input[type=email]").isNotEmpty()
        if (hasPasswordField && hasUsernameField) {
            return true
        }

        val normalized = html.lowercase()
        val loginSignals = listOf(
            "forgot your password",
            "username",
            "password",
            "otp",
            "captcha"
        )
        val positiveAnnouncementSignals = listOf(
            "announcement",
            "circular",
            "notice",
            "timetable",
            "retest",
            "internship"
        )

        val hasLoginFormSignal = normalized.contains("sign in") || normalized.contains("login")
        val loginSignalCount = loginSignals.count { marker -> normalized.contains(marker) }
        val announcementSignalCount = positiveAnnouncementSignals.count { marker -> normalized.contains(marker) }

        return hasLoginFormSignal && loginSignalCount >= 2 && announcementSignalCount == 0
    }

    fun isUnavailablePage(html: String): Boolean {
        if (html.isBlank()) {
            return false
        }
        val normalized = html.lowercase()
        return normalized.contains("sorry the page you are looking for is not available") ||
            normalized.contains("page you are looking for is not available") ||
            normalized.contains("requested page is not available")
    }

    fun extractAnnouncementUrls(html: String, baseUrl: String = ""): List<String> {
        if (html.isBlank()) {
            return emptyList()
        }
        val doc = Jsoup.parse(html, baseUrl)
        return doc.select("a[href]")
            .mapNotNull { link ->
                val href = link.absUrl("href").ifBlank { link.attr("href") }
                val normalized = href.trim()
                if (normalized.isBlank()) {
                    null
                } else {
                    normalized
                }
            }
            .filter { url ->
                url.startsWith("https://www.pesuacademy.com/", ignoreCase = true) &&
                    url.contains("menuId=") &&
                    (url.contains("studentProfilePESUAdmin", ignoreCase = true) ||
                        url.contains("announcement", ignoreCase = true) ||
                        url.contains("notice", ignoreCase = true))
            }
            .distinct()
            .take(10)
    }

    private fun cleanTitle(rawTitle: String): String {
        return rawTitle
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun cleanFullText(rawText: String, rawTitle: String, date: String): String {
        val normalizedTitle = cleanTitle(rawTitle)
        val cleanedLines = rawText
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.equals("read more", ignoreCase = true) }
            .filterNot { it.equals(date, ignoreCase = true) }
            .fold(mutableListOf<String>()) { acc, line ->
                val collapsed = line.replace(Regex("\\s+"), " ")
                val duplicateTitle = normalizedTitle.isNotBlank() && collapsed.equals(normalizedTitle, ignoreCase = true)
                if (!duplicateTitle || acc.none { it.equals(normalizedTitle, ignoreCase = true) }) {
                    acc.add(collapsed)
                }
                acc
            }

        return cleanedLines.joinToString("\n")
    }

    private fun extractTitle(element: Element): String {
        val titleSelectors = listOf(
            "h4.text-info",
            "h4",
            "h3",
            ".media-heading",
            ".announcement-title",
            ".card-title",
            "strong",
            "a"
        )
        titleSelectors.forEach { selector ->
            val title = element.select(selector).firstOrNull()?.text()?.trim().orEmpty()
            if (title.isNotBlank()) {
                return title
            }
        }
        return element.ownText().trim().ifBlank { element.text().lineSequence().firstOrNull().orEmpty().trim() }
    }

    private fun extractDate(element: Element): String {
        val dateSelectors = listOf(
            ".text-date",
            ".date",
            ".announcement-date",
            "time",
            "small"
        )
        dateSelectors.forEach { selector ->
            val value = element.select(selector).firstOrNull()?.text()?.trim().orEmpty()
            if (looksLikeDate(value)) {
                return value
            }
        }

        return element.text()
            .lines()
            .map { it.trim() }
            .firstOrNull { looksLikeDate(it) }
            .orEmpty()
    }

    private fun isLikelyAnnouncementEntry(element: Element): Boolean {
        val rawText = element.text().trim()
        val normalized = rawText.lowercase()
        if (rawText.isBlank()) {
            return false
        }
        if (isLikelyNavigationOrUtilityText(normalized)) {
            return false
        }

        val wordCount = rawText.split(Regex("\\s+")).size
        val hasDate = looksLikeDate(rawText)
        val hasLink = element.select("a[href]").isNotEmpty()
        val hasSignal = announcementSignals.any { normalized.contains(it) }

        return (wordCount >= 6 && (hasDate || hasSignal)) ||
            (wordCount >= 10 && hasLink) ||
            wordCount >= 18
    }

    private fun isLikelyAnnouncementPage(html: String, baseUrl: String): Boolean {
        val normalizedUrl = baseUrl.lowercase()
        val normalizedHtml = html.lowercase()
        val doc = Jsoup.parse(html, baseUrl)
        val headingText = doc.select("h1, h2, h3, h4, .page-title, .panel-title, .breadcrumb")
            .joinToString(" ") { it.text() }
            .lowercase()

        val urlHint = normalizedUrl.contains("menuid=667") ||
            normalizedUrl.contains("announcement") ||
            normalizedUrl.contains("actiontype=5")

        val headingHint = listOf("announcement", "circular", "notice", "notices").any { hint ->
            headingText.contains(hint)
        }
        val bodySignalCount = announcementSignals.count { normalizedHtml.contains(it) }
        val hasAnnouncementContainers = doc.select(
            ".elem-info-wrapper, .announcement-item, .announcement, .media, .list-group-item"
        ).isNotEmpty()

        return urlHint || headingHint || (bodySignalCount >= 3 && hasAnnouncementContainers)
    }

    private fun isLikelyNavigationOrUtilityText(normalized: String): Boolean {
        val utilitySignals = listOf(
            "logout",
            "sign out",
            "change password",
            "welcome",
            "attendance",
            "profile",
            "dashboard",
            "my courses",
            "home",
            "settings"
        )
        return utilitySignals.count { normalized.contains(it) } >= 2
    }

    private fun looksLikeDate(value: String): Boolean {
        val normalized = value.trim()
        if (normalized.isBlank()) {
            return false
        }
        val datePatterns = listOf(
            Regex("""\b\d{1,2}[-/ ](?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*[-/ , ]+\d{2,4}\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*[-/ ]+\d{1,2}(?:st|nd|rd|th)?(?:,?[-/ ]+\d{2,4})?\b""", RegexOption.IGNORE_CASE),
            Regex("""\b\d{1,2}[-/]\d{1,2}[-/]\d{2,4}\b""")
        )
        return datePatterns.any { it.containsMatchIn(normalized) }
    }

    companion object {
        private val announcementSignals = listOf(
            "announcement",
            "timetable",
            "notice",
            "semester",
            "internship",
            "isa",
            "esa",
            "calendar",
            "circular",
            "exam",
            "retest",
            "quiz"
        )
    }
}
