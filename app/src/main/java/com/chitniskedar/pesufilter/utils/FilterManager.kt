package com.chitniskedar.pesufilter.utils

import android.util.Log

class FilterManager(
    private val preferencesManager: PreferencesManager
) {

    fun process(packageName: String, title: String, text: String): FilterResult {
        Log.d(TAG, "Notification captured from package=$packageName title=$title text=$text")

        val isPesuNotification = isPesuPackage(packageName)
        if (!isPesuNotification) {
            return FilterResult(
                isPesuNotification = false,
                ignoreReason = "Not a PESU notification"
            )
        }

        val category = categorize("$title $text")
        val isEnabled = preferencesManager.isCategoryEnabled(category)

        return FilterResult(
            isPesuNotification = true,
            category = category,
            shouldShow = isEnabled,
            ignoreReason = if (isEnabled) null else "Category disabled"
        )
    }

    private fun isPesuPackage(packageName: String): Boolean {
        return packageName.lowercase().contains("pesu")
    }

    private fun categorize(content: String): String {
        val normalizedContent = content.lowercase()

        return when {
            containsKeyword(normalizedContent, EXAM_KEYWORDS) -> PreferencesManager.CATEGORY_EXAM
            containsKeyword(normalizedContent, ASSIGNMENT_KEYWORDS) -> PreferencesManager.CATEGORY_ASSIGNMENT
            containsKeyword(normalizedContent, EVENT_KEYWORDS) -> PreferencesManager.CATEGORY_EVENT
            containsKeyword(normalizedContent, GENERAL_KEYWORDS) -> PreferencesManager.CATEGORY_GENERAL
            else -> PreferencesManager.CATEGORY_GENERAL
        }
    }

    private fun containsKeyword(content: String, keywords: List<String>): Boolean {
        return keywords.any { keyword -> content.contains(keyword) }
    }

    data class FilterResult(
        val isPesuNotification: Boolean = false,
        val category: String? = null,
        val shouldShow: Boolean = false,
        val ignoreReason: String? = null
    )

    companion object {
        private const val TAG = "FilterManager"

        private val EXAM_KEYWORDS = listOf("exam", "test", "internal")
        private val ASSIGNMENT_KEYWORDS = listOf("assignment", "submission")
        private val EVENT_KEYWORDS = listOf("workshop", "seminar")
        private val GENERAL_KEYWORDS = listOf("notice", "all students")
    }
}
