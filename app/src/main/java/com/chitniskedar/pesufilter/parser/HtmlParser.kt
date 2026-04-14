package com.chitniskedar.pesufilter.parser

import com.chitniskedar.pesufilter.model.Announcement
import org.jsoup.Jsoup

class HtmlParser {

    fun parseAnnouncements(html: String): List<Announcement> {
        if (html.isBlank()) {
            return emptyList()
        }

        val doc = Jsoup.parse(html)
        val announcements = doc.select(".elem-info-wrapper")

        return announcements.mapNotNull { element ->
            val title = element.select("h4.text-info").text().trim()
            val date = element.select(".text-date").text().trim()
            val link = element.select("a[href]").firstOrNull()?.absUrl("href")?.ifBlank { null }

            if (title.isBlank() && date.isBlank()) {
                null
            } else {
                Announcement(
                    title = title.ifBlank { "Untitled announcement" },
                    date = date.ifBlank { "Date unavailable" },
                    link = link
                )
            }
        }
    }
}
