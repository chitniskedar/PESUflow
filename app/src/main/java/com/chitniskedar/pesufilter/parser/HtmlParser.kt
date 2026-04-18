package com.chitniskedar.pesufilter.parser

import com.chitniskedar.pesufilter.model.Announcement
import org.jsoup.Jsoup

class HtmlParser {

    fun parseAnnouncements(html: String, baseUrl: String = ""): List<Announcement> {
        if (html.isBlank()) {
            return emptyList()
        }

        val doc = Jsoup.parse(html, baseUrl)
        val announcements = doc.select(".elem-info-wrapper")

        return announcements.mapNotNull { element ->
            val rawTitle = element.select("h4.text-info").text().trim()
            val date = element.select(".text-date").text().trim()
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
            "password",
            "captcha",
            "verify and sign in"
        )

        return loginMarkers.count { normalized.contains(it) } >= 2
    }

    private fun cleanTitle(rawTitle: String): String {
        if (rawTitle.isBlank()) {
            return rawTitle
        }

        return rawTitle
            .lines()
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
}
