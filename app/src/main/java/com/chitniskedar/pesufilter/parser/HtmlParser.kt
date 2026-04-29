package com.chitniskedar.pesufilter.parser

import com.chitniskedar.pesufilter.model.Announcement
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class HtmlParser {

    fun parseAnnouncements(html: String, baseUrl: String = ""): List<Announcement> {
        if (html.isBlank()) {
            return emptyList()
        }

        val doc = Jsoup.parse(html, baseUrl)
        val primaryCandidates = doc.select(
            ".elem-info-wrapper, .announcement-item, .media, .list-group-item, .card, tr, li"
        ).toList()
        val announcements = primaryCandidates
            .filter { element -> looksLikeAnnouncementCard(element) }
            .ifEmpty {
                doc.select("tr, li, .media, .card, .list-group-item, div")
                    .toList()
                    .filter { element -> element.text().length >= 12 }
                    .filter { element -> looksLikeAnnouncementCard(element) }
            }
            .ifEmpty {
                // Broad fallback: keep likely content containers so sync does not fail silently.
                doc.select("tr, li, .media, .card, .list-group-item, div, p")
                    .toList()
                    .filter { element ->
                        val text = element.text().trim()
                        val hasLink = element.select("a[href]").isNotEmpty()
                        text.length >= 18 && (hasLink || text.split(Regex("\\s+")).size >= 4)
                    }
            }

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

    private fun looksLikeAnnouncementCard(element: Element): Boolean {
        val normalized = element.text().lowercase()
        if (normalized.length < 12) {
            return false
        }
        if (element.select("a[href]").isNotEmpty()) {
            return true
        }
        return looksLikeDate(normalized) ||
            normalized.contains("announcement") ||
            normalized.contains("timetable") ||
            normalized.contains("notice") ||
            normalized.contains("semester") ||
            normalized.contains("internship") ||
            normalized.contains("isa") ||
            normalized.contains("esa") ||
            normalized.contains("calendar") ||
            normalized.contains("circular") ||
            normalized.contains("exam") ||
            normalized.contains("retest") ||
            normalized.contains("quiz")
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
}
