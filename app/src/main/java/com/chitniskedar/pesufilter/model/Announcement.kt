package com.chitniskedar.pesufilter.model

data class Announcement(
    val title: String,
    val date: String,
    val fullText: String
) {
    val stableId: String
        get() = buildString {
            append(title.normalizedForId())
            append("|")
            append(date.normalizedForId())
            append("|")
            append(fullText.normalizedForId().hashCode())
        }

    private fun String.normalizedForId(): String {
        return lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
