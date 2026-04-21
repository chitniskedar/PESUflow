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

        return matchesBranch(normalizedText)
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
        if (containsAnyPhrase(content, GLOBAL_SEMESTER_PHRASES)) {
            return true
        }

        val semestersMentioned = extractSemesters(content)
        return semestersMentioned.isEmpty() || selectedSemester in semestersMentioned
    }

    private fun extractSemesters(content: String): Set<Int> {
        val semesters = linkedSetOf<Int>()
        val normalizedContent = normalize(content)

        SEMESTER_RANGE_REGEXES.forEach { regex ->
            regex.findAll(normalizedContent).forEach { match ->
                val start = semesterFromToken(match.groupValues.getOrNull(1).orEmpty())
                val end = semesterFromToken(match.groupValues.getOrNull(2).orEmpty())
                if (start != null && end != null) {
                    val range = if (start <= end) start..end else end..start
                    semesters.addAll(range)
                }
            }
        }

        SEMESTER_REGEXES.forEach { regex ->
            regex.findAll(normalizedContent).forEach { match ->
                match.groups.drop(1)
                    .mapNotNull { it?.value?.trim() }
                    .forEach { rawValue ->
                        semesterFromToken(rawValue)?.let { semesters.add(it) }
                    }
            }
        }

        YEAR_REGEXES.forEach { regex ->
            regex.findAll(normalizedContent).forEach { match ->
                match.groups.drop(1)
                    .mapNotNull { it?.value?.trim() }
                    .forEach { rawValue ->
                        yearFromToken(rawValue)?.let { year ->
                            semesters.addAll(yearToSemesters(year))
                        }
                    }
            }
        }

        return semesters
    }

    private fun matchesBranch(content: String): Boolean {
        val selectedBranch = preferencesManager.getSelectedBranch()?.uppercase() ?: return true
        if (containsAnyPhrase(content, GLOBAL_BRANCH_PHRASES)) {
            return true
        }

        val branchesMentioned = extractBranches(content)
        return branchesMentioned.isEmpty() || selectedBranch in branchesMentioned
    }

    private fun extractBranches(content: String): Set<String> {
        return BRANCH_KEYWORDS.entries
            .filter { (_, aliases) -> aliases.any { alias -> containsPhrase(content, alias) } }
            .mapTo(linkedSetOf()) { it.key }
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

    private fun yearFromToken(rawValue: String): Int? {
        val normalized = rawValue.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
        return when (normalized) {
            "1", "1st", "first", "i" -> 1
            "2", "2nd", "second", "ii" -> 2
            "3", "3rd", "third", "iii" -> 3
            "4", "4th", "fourth", "iv" -> 4
            else -> null
        }
    }

    private fun yearToSemesters(year: Int): Set<Int> {
        val startSemester = (year * 2) - 1
        return setOf(startSemester, startSemester + 1)
    }

    private fun normalize(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    companion object {
        private const val SEMESTER_TOKEN =
            """(?:[1-8](?:st|nd|rd|th)?|first|second|third|fourth|fifth|sixth|seventh|eighth|i|ii|iii|iv|v|vi|vii|viii)"""

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
        private val GLOBAL_BRANCH_PHRASES = listOf(
            "all branches",
            "all students",
            "everyone",
            "open to all"
        )
        private val GLOBAL_SEMESTER_PHRASES = listOf(
            "all semesters",
            "all sem",
            "all years"
        )
        private val BRANCH_KEYWORDS = mapOf(
            "CSE" to listOf("cse", "computer science", "computer science and engineering"),
            "ECE" to listOf("ece", "electronics", "electronics and communication"),
            "EEE" to listOf("eee", "electrical", "electrical and electronics"),
            "ME" to listOf("me", "mech", "mechanical"),
            "CIVIL" to listOf("civil"),
            "PHARM" to listOf("pharm", "pharmacy"),
            "MBA" to listOf("mba"),
            "BBA" to listOf("bba"),
            "BCOM" to listOf("bcom", "b com"),
            "BCA" to listOf("bca"),
            "MCA" to listOf("mca"),
            "MBBS" to listOf("mbbs"),
            "NURSING" to listOf("nursing"),
            "AIML" to listOf("aiml", "ai ml", "artificial intelligence", "machine learning")
        )
        private val SEMESTER_RANGE_REGEXES = listOf(
            Regex("""\b($SEMESTER_TOKEN)\s+(?:sem|semester)\s+(?:to|through)\s+($SEMESTER_TOKEN)\s+(?:sem|semester)\b"""),
            Regex("""\b($SEMESTER_TOKEN)\s+(?:to|through)\s+($SEMESTER_TOKEN)\s+(?:sem|semester)\b""")
        )
        private val SEMESTER_REGEXES = listOf(
            Regex("""\b($SEMESTER_TOKEN)\s+(?:sem|semester)\b"""),
            Regex("""\b(?:sem|semester)\s+($SEMESTER_TOKEN)\b"""),
            Regex("""\b($SEMESTER_TOKEN)\s+($SEMESTER_TOKEN)\s+($SEMESTER_TOKEN)\s+(?:sem|semester)\b"""),
            Regex("""\b($SEMESTER_TOKEN)\s+($SEMESTER_TOKEN)\s+(?:sem|semester)\b"""),
            Regex("""\b($SEMESTER_TOKEN)\s+($SEMESTER_TOKEN)\s+($SEMESTER_TOKEN)\s+(?:isa|esa|exam|timetable|time table|retest)\b"""),
            Regex("""\b($SEMESTER_TOKEN)\s+($SEMESTER_TOKEN)\s+(?:isa|esa|exam|timetable|time table|retest)\b""")
        )
        private val YEAR_REGEXES = listOf(
            Regex("""\b([1-4](?:st|nd|rd|th)?|first|second|third|fourth|i|ii|iii|iv)\s+(?:year|yr)\b"""),
            Regex("""\b([1-4](?:st|nd|rd|th)?|first|second|third|fourth|i|ii|iii|iv)\s+and\s+([1-4](?:st|nd|rd|th)?|first|second|third|fourth|i|ii|iii|iv)\s+(?:year|yr)\b"""),
            Regex("""\b([1-4](?:st|nd|rd|th)?|first|second|third|fourth|i|ii|iii|iv)\s*,\s*([1-4](?:st|nd|rd|th)?|first|second|third|fourth|i|ii|iii|iv)\s*(?:and|&)\s*([1-4](?:st|nd|rd|th)?|first|second|third|fourth|i|ii|iii|iv)\s+(?:year|yr)\b""")
        )
    }

    enum class AnnouncementPriority {
        HIGH,
        MEDIUM,
        LOW
    }
}
