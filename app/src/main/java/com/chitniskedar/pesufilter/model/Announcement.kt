package com.chitniskedar.pesufilter.model

data class Announcement(
    val title: String,
    val date: String,
    val link: String? = null
) {
    val stableId: String
        get() = "$title|$date|${link.orEmpty()}".lowercase().trim()
}
