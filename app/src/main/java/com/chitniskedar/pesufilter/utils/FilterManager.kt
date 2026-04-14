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

        val content = "$title $text"
        if (!matchesBranchAndSemester(content)) {
            return FilterResult(
                isPesuNotification = true,
                shouldShow = false,
                ignoreReason = "Branch or semester mismatch"
            )
        }

        val category = categorize(content)
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

    private fun matchesBranchAndSemester(content: String): Boolean {
        val normalizedContent = content.lowercase()
        val selectedBranch = preferencesManager.getSelectedBranch()
        val selectedSemester = preferencesManager.getSelectedSemester()

        val branchMatches = selectedBranch?.let {
            matchesBranch(normalizedContent, it)
        } ?: true

        val semesterMatches = selectedSemester?.let {
            matchesSemester(normalizedContent, it)
        } ?: true

        return branchMatches && semesterMatches
    }

    private fun matchesBranch(content: String, branch: String): Boolean {
        if (containsKeyword(content, GLOBAL_AUDIENCE_KEYWORDS)) {
            return true
        }

        val selectedBranchAliases = BRANCH_KEYWORDS[branch.uppercase()].orEmpty()
        if (selectedBranchAliases.any { alias -> content.contains(alias) }) {
            return true
        }

        val mentionsAnyKnownBranch = BRANCH_KEYWORDS.values.flatten().any { alias ->
            content.contains(alias)
        }

        return !mentionsAnyKnownBranch
    }

    private fun matchesSemester(content: String, semester: Int): Boolean {
        if (containsKeyword(content, GLOBAL_AUDIENCE_KEYWORDS)) {
            return true
        }

        val selectedSemesterAliases = semesterAliases(semester)
        if (selectedSemesterAliases.any { alias -> content.contains(alias) }) {
            return true
        }

        val mentionsAnySemester = (1..8).flatMap { semesterAliases(it) }.any { alias ->
            content.contains(alias)
        }

        return !mentionsAnySemester
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
        private val GLOBAL_AUDIENCE_KEYWORDS = listOf(
            "all branches",
            "all students",
            "all semesters",
            "everyone",
            "common to all",
            "for all"
        )
        private val BRANCH_KEYWORDS = mapOf(
            "CSE" to listOf("cse", "computer science"),
            "ECE" to listOf("ece", "electronics"),
            "EEE" to listOf("eee", "electrical"),
            "ME" to listOf(" me ", "mech", "mechanical"),
            "CIVIL" to listOf("civil"),
            "BBA" to listOf("bba"),
            "BCA" to listOf("bca"),
            "MCA" to listOf("mca")
        )

        private fun semesterAliases(semester: Int): List<String> {
            val ordinal = when (semester) {
                1 -> "1st"
                2 -> "2nd"
                3 -> "3rd"
                else -> "${semester}th"
            }

            val roman = when (semester) {
                1 -> "i"
                2 -> "ii"
                3 -> "iii"
                4 -> "iv"
                5 -> "v"
                6 -> "vi"
                7 -> "vii"
                else -> "viii"
            }

            return listOf(
                "sem $semester",
                "semester $semester",
                "$ordinal sem",
                "$roman sem"
            )
        }
    }
}
