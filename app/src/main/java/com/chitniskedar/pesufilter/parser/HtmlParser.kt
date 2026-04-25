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
        val announcements = doc.select(".elem-info-wrapper")
        val announcements = doc.select(
            ".elem-info-wrapper, .announcement-item, .media, .list-group-item, .card, tr"
        ).filter { looksLikeAnnouncementCard(it) }

        return announcements.mapNotNull { element ->
            val rawTitle = element.select("h4.text-info").text().trim()
            val date = element.select(".text-date").text().trim()
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
        val loginMarkers = listOf(
            "sign in",
            "forgot your password",
            "username",
@@ -59,26 +62,94 @@ class HtmlParser {
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?.replace(Regex("\\s+"), " ")
            .orEmpty()
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
        if (normalized.length < 24) {
            return false
        }
        return looksLikeDate(normalized) ||
            normalized.contains("announcement") ||
            normalized.contains("timetable") ||
            normalized.contains("notice") ||
            normalized.contains("semester") ||
            normalized.contains("internship")
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