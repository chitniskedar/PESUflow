package com.chitniskedar.pesufilter.utils

class FilterManager(
    private val preferencesManager: PreferencesManager
) {

    fun shouldShow(text: String): Boolean {
        val normalizedText = normalize(text)
        val category = categorize(normalizedText)

        if (!preferencesManager.isCategoryEnabled(category)) {
            return false
        }

        if (!matchesSemester(normalizedText)) {
            return false
        }

        val branchAliases = branchAliases(preferencesManager.getSelectedBranch())
        val branchMatch = branchAliases.any { alias -> containsPhrase(normalizedText, alias) }
        val globalMatch = containsAnyPhrase(normalizedText, GLOBAL_PHRASES)
        val importantMatch = containsAnyPhrase(normalizedText, ALWAYS_ALLOW_PHRASES)
        val blockedMatch = containsAnyPhrase(normalizedText, BLOCKED_BRANCH_PHRASES)

        if (blockedMatch && !branchMatch && !globalMatch) {
            return false
        }

        return branchMatch || globalMatch || importantMatch
    }

    fun categorize(text: String): String {
        val normalizedText = normalize(text)
        return when {
            containsAnyPhrase(normalizedText, EXAM_PHRASES) -> PreferencesManager.CATEGORY_EXAM
            containsAnyPhrase(normalizedText, INTERNSHIP_PHRASES) -> PreferencesManager.CATEGORY_INTERNSHIP
            containsAnyPhrase(normalizedText, NOTICE_PHRASES) -> PreferencesManager.CATEGORY_NOTICE
            else -> PreferencesManager.CATEGORY_GENERAL
        }
    }

    fun priorityFor(text: String): AnnouncementPriority {
        val normalizedText = normalize(text)
        return when {
            containsAnyPhrase(normalizedText, EXAM_PRIORITY_PHRASES) -> AnnouncementPriority.HIGH
            containsAnyPhrase(normalizedText, INTERNSHIP_PHRASES) -> AnnouncementPriority.MEDIUM
            else -> AnnouncementPriority.LOW
        }
    }

    private fun matchesSemester(content: String): Boolean {
        val selectedSemester = preferencesManager.getSelectedSemester() ?: return true
        if (containsAnyPhrase(content, GLOBAL_PHRASES)) {
            return true
        }

        val semestersMentioned = extractSemesters(content)
        return semestersMentioned.isEmpty() || selectedSemester in semestersMentioned
    }

    private fun extractSemesters(content: String): Set<Int> {
        val semesters = linkedSetOf<Int>()

        SEMESTER_REGEXES.forEach { regex ->
            regex.findAll(content).forEach { match ->
                match.groups.drop(1)
                    .mapNotNull { it?.value?.trim() }
                    .forEach { rawValue ->
                        semesterFromToken(rawValue)?.let { semesters.add(it) }
                    }
            }
        }

        return semesters
    }

    private fun branchAliases(branch: String?): List<String> {
        return BRANCH_KEYWORDS[branch?.uppercase()].orEmpty()
    }

    private fun containsAnyPhrase(content: String, phrases: List<String>): Boolean {
        return phrases.any { phrase -> containsPhrase(content, phrase) }
    }

    private fun containsPhrase(content: String, phrase: String): Boolean {
        return Regex("""(?<![a-z0-9])${Regex.escape(normalize(phrase))}(?![a-z0-9])""")
            .containsMatchIn(content)
    }

    private fun semesterFromToken(rawValue: String): Int? {
        val normalized = rawValue.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
        return when (normalized) {
            "1", "1st", "first", "i" -> 1
            "2", "2nd", "second", "ii" -> 2
            "3", "3rd", "third", "iii" -> 3
            "4", "4th", "fourth", "iv" -> 4
            "5", "5th", "fifth", "v" -> 5
            "6", "6th", "sixth", "vi" -> 6
            "7", "7th", "seventh", "vii" -> 7
            "8", "8th", "eighth", "viii" -> 8
            else -> null
        }
    }

    private fun normalize(text: String): String {
        return text.lowercase().replace(Regex("\\s+"), " ").trim()
    }

    companion object {
        private val EXAM_PHRASES = listOf(
            "exam",
            "isa",
            "esa",
            "timetable",
            "time table",
            "quiz",
            "test",
            "internal"
        )
        private val EXAM_PRIORITY_PHRASES = listOf("exam", "isa", "esa", "timetable", "time table")
        private val INTERNSHIP_PHRASES = listOf(
            "internship",
            "opportunity",
            "opportunities",
            "placement",
            "career",
            "workshop"
        )
        private val NOTICE_PHRASES = listOf(
            "assignment",
            "notice",
            "notification",
            "calendar of events",
            "backlog",
            "enrollment"
        )
        private val ALWAYS_ALLOW_PHRASES = EXAM_PHRASES + INTERNSHIP_PHRASES + NOTICE_PHRASES
        private val GLOBAL_PHRASES = listOf(
            "all",
            "all branches",
            "all students",
            "all semesters",
            "everyone",
            "open to all"
        )
        private val BLOCKED_BRANCH_PHRASES = listOf("pharm", "nursing", "mbbs", "bba", "bcom")
        private val BRANCH_KEYWORDS = mapOf(
            "CSE" to listOf("cse", "computer science", "computer science and engineering"),
            "ECE" to listOf("ece", "electronics", "electronics and communication"),
            "EEE" to listOf("eee", "electrical", "electrical and electronics"),
            "ME" to listOf("me", "mech", "mechanical"),
            "CIVIL" to listOf("civil"),
            "PHARM" to listOf("pharm", "pharmacy"),
            "MBA" to listOf("mba"),
            "BBA" to listOf("bba"),
            "BCA" to listOf("bca"),
            "MCA" to listOf("mca"),
            "AIML" to listOf("aiml", "ai ml", "artificial intelligence", "machine learning")
        )
        private val SEMESTER_REGEXES = listOf(
            Regex("""\b([1-8](?:st|nd|rd|th)?)\s*(?:sem|semester)\b"""),
            Regex("""\b(?:sem|semester)\s*([1-8])\b"""),
            Regex("""\b([ivx]{1,4})\s*(?:sem|semester)\b"""),
            Regex("""\b([1-8](?:st|nd|rd|th)?)\s*(?:,|&|and)\s*([1-8](?:st|nd|rd|th)?)\s*(?:sem|semester)\b"""),
            Regex("""\b([1-8](?:st|nd|rd|th)?)\s*,\s*([1-8](?:st|nd|rd|th)?)\s*(?:and|&)\s*([1-8](?:st|nd|rd|th)?)\s*(?:sem|semester)\b""")
        )
    }

    enum class AnnouncementPriority {
        HIGH,
        MEDIUM,
        LOW
    }
}
