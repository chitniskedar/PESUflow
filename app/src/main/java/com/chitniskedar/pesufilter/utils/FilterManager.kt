package com.chitniskedar.pesufilter.utils

class FilterManager(
    private val preferencesManager: PreferencesManager
) {

    fun shouldShow(title: String, fullText: String): Boolean {
        val normalizedTitle = normalize(title)
        val normalizedText = normalize("$title\n$fullText")

        // Hard gate from title first to avoid cross-item bleed from broad parsed body text.
        if (!matchesBranchStrictFromTitle(normalizedTitle)) {
            return false
        }

        // Stage 1: audience filter (branch + semester) first.
        if (!matchesBranch(normalizedText)) {
            return false
        }
        if (!matchesSemester(normalizedText)) {
            return false
        }

        // Stage 2: category subfilter (exam/internship/notice/general).
        val category = categorize(normalizedText)
        if (category == PreferencesManager.CATEGORY_INTERNSHIP) {
            val selectedSemester = preferencesManager.getSelectedSemester()
            if (selectedSemester != null && selectedSemester < 3) {
                return false
            }
        }
        return preferencesManager.isCategoryEnabled(category)
    }

    fun shouldShow(text: String): Boolean {
        return shouldShow(title = text, fullText = text)
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
        if (branchesMentioned.isNotEmpty()) {
            return selectedBranch in branchesMentioned
        }

        // If no explicit branch is present, gate by course-family mentions
        // so BTech users do not receive BPharm/medical-only notices and vice versa.
        val selectedFamily = branchFamilyFor(selectedBranch) ?: return true
        val mentionedFamilies = extractMentionedBranchFamilies(content)
        return mentionedFamilies.isEmpty() || selectedFamily in mentionedFamilies
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

    private fun matchesBranchStrictFromTitle(normalizedTitle: String): Boolean {
        val selectedBranch = preferencesManager.getSelectedBranch()?.uppercase() ?: return true
        val selectedFamily = branchFamilyFor(selectedBranch)

        val titleBranches = extractBranches(normalizedTitle)
        if (titleBranches.isNotEmpty() && selectedBranch !in titleBranches) {
            return false
        }

        if (selectedFamily != null) {
            val titleFamilies = extractMentionedBranchFamilies(normalizedTitle)
            if (titleFamilies.isNotEmpty() && selectedFamily !in titleFamilies) {
                return false
            }
        }
        return true
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

    private fun branchFamilyFor(branch: String): String? {
        return when (branch.uppercase()) {
            "CSE", "ECE", "EEE", "ME", "CIVIL", "AIML" -> BRANCH_FAMILY_BTECH
            "PHARM" -> BRANCH_FAMILY_PHARM
            "MBBS", "NURSING" -> BRANCH_FAMILY_MEDICAL
            "BBA", "MBA", "BCOM", "BCA", "MCA" -> BRANCH_FAMILY_MANAGEMENT
            else -> null
        }
    }

    private fun extractMentionedBranchFamilies(content: String): Set<String> {
        val mentioned = linkedSetOf<String>()
        BRANCH_FAMILY_KEYWORDS.forEach { (family, phrases) ->
            if (mentionsFamily(content, family, phrases)) {
                mentioned.add(family)
            }
        }
        return mentioned
    }

    private fun mentionsFamily(content: String, family: String, phrases: List<String>): Boolean {
        if (phrases.any { phrase -> containsPhrase(content, phrase) }) {
            return true
        }

        val tokens = content.split(Regex("\\s+")).filter { it.isNotBlank() }
        return when (family) {
            BRANCH_FAMILY_PHARM -> tokens.any { token -> token.startsWith("pharm") }
            BRANCH_FAMILY_PG_TECH -> tokens.any { token ->
                token == "mtech" || token == "m" || token.startsWith("mtech")
            }
            else -> false
        }
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
            "ME" to listOf("mech", "mechanical"),
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
        private const val BRANCH_FAMILY_BTECH = "BTECH"
        private const val BRANCH_FAMILY_PHARM = "PHARM"
        private const val BRANCH_FAMILY_MEDICAL = "MEDICAL"
        private const val BRANCH_FAMILY_MANAGEMENT = "MANAGEMENT"
        private const val BRANCH_FAMILY_PG_TECH = "PG_TECH"
        private val BRANCH_FAMILY_KEYWORDS = mapOf(
            BRANCH_FAMILY_BTECH to listOf(
                "btech", "b tech", "engineering", "all btech", "all b tech", "all engineering"
            ),
            BRANCH_FAMILY_PG_TECH to listOf(
                "mtech", "m tech", "m.e", "m e", "master of technology"
            ),
            BRANCH_FAMILY_PHARM to listOf(
                "bpharm", "b pharm", "pharm", "pharmacy", "pharma", "pharmd", "mpharm", "m pharm"
            ),
            BRANCH_FAMILY_MEDICAL to listOf(
                "medical", "mbbs", "nursing", "all medical", "health science"
            ),
            BRANCH_FAMILY_MANAGEMENT to listOf(
                "management", "mba", "bba", "bcom", "bca", "mca", "commerce"
            )
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
