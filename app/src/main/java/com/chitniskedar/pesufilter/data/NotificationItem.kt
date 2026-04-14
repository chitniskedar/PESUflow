package com.chitniskedar.pesufilter.data

data class NotificationItem(
    val text: String,
    val category: String,
    val timestamp: Long,
    val isShown: Boolean
)
