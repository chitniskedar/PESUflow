package com.chitniskedar.pesufilter.utils

import android.content.Context
import com.chitniskedar.pesufilter.model.Announcement
import org.json.JSONArray
import org.json.JSONObject

class PreferencesManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isCategoryEnabled(category: String): Boolean {
        return prefs.getBoolean(category, true)
    }

    fun isSetupDone(): Boolean {
        return prefs.getBoolean(KEY_SETUP_DONE, false)
    }

    fun saveUserProfile(branch: String, semester: Int) {
        prefs.edit()
            .putString(KEY_BRANCH, branch)
            .putInt(KEY_SEMESTER, semester)
            .putBoolean(KEY_SETUP_DONE, true)
            .apply()
    }

    fun getSelectedBranch(): String? {
        return prefs.getString(KEY_BRANCH, null)
    }

    fun getSelectedSemester(): Int? {
        return if (prefs.contains(KEY_SEMESTER)) prefs.getInt(KEY_SEMESTER, 1) else null
    }

    fun setCategoryEnabled(category: String, enabled: Boolean) {
        prefs.edit().putBoolean(category, enabled).apply()
    }

    fun saveAnnouncements(items: List<Announcement>) {
        prefs.edit().putString(ANNOUNCEMENTS_KEY, toJson(items)).apply()
    }

    fun getSavedAnnouncements(): List<Announcement> {
        val raw = prefs.getString(ANNOUNCEMENTS_KEY, null) ?: return emptyList()
        val jsonArray = JSONArray(raw)
        val announcements = mutableListOf<Announcement>()

        for (index in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(index)
            announcements.add(
                Announcement(
                    title = jsonObject.optString(KEY_TITLE),
                    date = jsonObject.optString(KEY_DATE),
                    link = jsonObject.optString(KEY_LINK).ifBlank { null }
                )
            )
        }

        return announcements
    }

    fun hasSeenAnnouncement(stableId: String): Boolean {
        return prefs.getStringSet(KEY_SEEN_ANNOUNCEMENTS, emptySet()).orEmpty().contains(stableId)
    }

    fun markAnnouncementSeen(stableId: String) {
        val updated = prefs.getStringSet(KEY_SEEN_ANNOUNCEMENTS, emptySet()).orEmpty().toMutableSet()
        updated.add(stableId)
        prefs.edit().putStringSet(KEY_SEEN_ANNOUNCEMENTS, updated).apply()
    }

    fun setLastSyncTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_TIMESTAMP, timestamp).apply()
    }

    fun getLastSyncTimestamp(): Long? {
        return if (prefs.contains(KEY_LAST_SYNC_TIMESTAMP)) {
            prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
        } else {
            null
        }
    }

    fun setLastSyncError(message: String?) {
        prefs.edit().putString(KEY_LAST_SYNC_ERROR, message).apply()
    }

    fun getLastSyncError(): String? {
        return prefs.getString(KEY_LAST_SYNC_ERROR, null)
    }

    fun getBackendUrl(): String {
        return prefs.getString(KEY_BACKEND_URL, DEFAULT_BACKEND_URL).orEmpty()
    }

    fun saveBackendUrl(url: String) {
        prefs.edit().putString(KEY_BACKEND_URL, url).apply()
    }

    fun getBackendCookie(): String? {
        return prefs.getString(KEY_BACKEND_COOKIE, null)
    }

    fun saveBackendCookie(cookie: String?) {
        prefs.edit().putString(KEY_BACKEND_COOKIE, cookie).apply()
    }

    private fun toJson(items: List<Announcement>): String {
        val jsonArray = JSONArray()
        items.forEach { item ->
            jsonArray.put(
                JSONObject().apply {
                    put(KEY_TITLE, item.title)
                    put(KEY_DATE, item.date)
                    put(KEY_LINK, item.link.orEmpty())
                }
            )
        }
        return jsonArray.toString()
    }

    companion object {
        const val CATEGORY_EXAM = "EXAM"
        const val CATEGORY_ASSIGNMENT = "ASSIGNMENT"
        const val CATEGORY_INTERNSHIP = "INTERNSHIP"
        const val CATEGORY_GENERAL = "GENERAL"

        private const val PREFS_NAME = "pesuflow_prefs"
        private const val ANNOUNCEMENTS_KEY = "announcements_key"
        private const val KEY_TITLE = "title"
        private const val KEY_DATE = "date"
        private const val KEY_LINK = "link"
        private const val KEY_BRANCH = "branch"
        private const val KEY_SEMESTER = "semester"
        private const val KEY_SETUP_DONE = "setup_done"
        private const val KEY_SEEN_ANNOUNCEMENTS = "seen_announcements"
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        private const val KEY_LAST_SYNC_ERROR = "last_sync_error"
        private const val KEY_BACKEND_URL = "backend_url"
        private const val KEY_BACKEND_COOKIE = "backend_cookie"
        private const val DEFAULT_BACKEND_URL = "https://your-pesu-backend-url-here"
    }
}
