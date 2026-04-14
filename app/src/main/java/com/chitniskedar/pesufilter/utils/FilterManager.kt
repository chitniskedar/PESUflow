package com.chitniskedar.pesufilter.utils

class FilterManager(
    private val preferencesManager: PreferencesManager
) {

    fun shouldShow(text: String): Boolean {
        val normalizedText = text.lowercase()
        val category = categorize(normalizedText)

        if (!preferencesManager.isCategoryEnabled(category)) {
            return false
        }

        if (!matchesSemester(normalizedText)) {
            return false
        }

        if (containsKeyword(normalizedText, BLOCKED_BRANCH_KEYWORDS)) {
            val selectedBranchAliases = branchAliases(preferencesManager.getSelectedBranch())
            if (selectedBranchAliases.none { alias -> normalizedText.contains(alias) }) {
                return false
            }
        }

        val selectedBranchAliases = branchAliases(preferencesManager.getSelectedBranch())
        val branchMatch = selectedBranchAliases.any { alias -> normalizedText.contains(alias) }
        val globalMatch = containsKeyword(normalizedText, GLOBAL_KEYWORDS)
        val importantMatch = containsKeyword(normalizedText, ALWAYS_ALLOW_KEYWORDS)

        return branchMatch || globalMatch || importantMatch
    }

    fun categorize(text: String): String {
        return when {
            containsKeyword(text, EXAM_KEYWORDS) -> PreferencesManager.CATEGORY_EXAM
            containsKeyword(text, ASSIGNMENT_KEYWORDS) -> PreferencesManager.CATEGORY_ASSIGNMENT
            containsKeyword(text, INTERNSHIP_KEYWORDS) -> PreferencesManager.CATEGORY_INTERNSHIP
            else -> PreferencesManager.CATEGORY_GENERAL
        }
    }

    private fun matchesSemester(content: String): Boolean {
        val semester = preferencesManager.getSelectedSemester() ?: return true
        if (containsKeyword(content, GLOBAL_KEYWORDS)) {
            return true
        }

        val selectedAliases = semesterAliases(semester)
        if (selectedAliases.any { alias -> content.contains(alias) }) {
            return true
        }

        val mentionsSpecificSemester = (1..8).flatMap { semesterAliases(it) }.any { alias ->
            content.contains(alias)
        }

        return !mentionsSpecificSemester
    }

    private fun branchAliases(branch: String?): List<String> {
        return BRANCH_KEYWORDS[branch?.uppercase()].orEmpty()
    }

    private fun containsKeyword(content: String, keywords: List<String>): Boolean {
        return keywords.any { keyword -> content.contains(keyword) }
    }

    companion object {
        private val EXAM_KEYWORDS = listOf("exam", "isa", "esa", "quiz", "test", "internal")
        private val ASSIGNMENT_KEYWORDS = listOf("assignment", "submission", "lab record", "project review")
        private val INTERNSHIP_KEYWORDS = listOf("internship", "placement", "career")
        private val ALWAYS_ALLOW_KEYWORDS = listOf("exam", "isa", "assignment", "internship")
        private val GLOBAL_KEYWORDS = listOf("all", "all branches", "all students", "all semesters", "everyone")
        private val BLOCKED_BRANCH_KEYWORDS = listOf("mechanical", "pharm", "mba")
        private val BRANCH_KEYWORDS = mapOf(
            "CSE" to listOf("cse", "computer science"),
            "ECE" to listOf("ece", "electronics"),
            "EEE" to listOf("eee", "electrical"),
            "ME" to listOf(" me ", "mech", "mechanical"),
            "CIVIL" to listOf("civil"),
            "PHARM" to listOf("pharm", "pharmacy"),
            "MBA" to listOf("mba"),
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
