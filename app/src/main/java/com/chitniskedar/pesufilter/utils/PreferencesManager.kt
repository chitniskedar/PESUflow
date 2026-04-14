package com.chitniskedar.pesufilter.utils

import android.content.Context
import com.chitniskedar.pesufilter.data.NotificationItem
import org.json.JSONArray
import org.json.JSONObject

class PreferencesManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isCategoryEnabled(category: String): Boolean {
        return prefs.getBoolean(category, true)
    }

    fun isOnboardingComplete(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
    }

    fun saveUserProfile(branch: String, semester: Int) {
        prefs.edit()
            .putString(KEY_BRANCH, branch)
            .putInt(KEY_SEMESTER, semester)
            .putBoolean(KEY_ONBOARDING_COMPLETE, true)
            .apply()
    }

    fun getSelectedBranch(): String? {
        return prefs.getString(KEY_BRANCH, null)
    }

    fun getSelectedSemester(): Int? {
        return if (prefs.contains(KEY_SEMESTER)) {
            prefs.getInt(KEY_SEMESTER, 1)
        } else {
            null
        }
    }

    fun setCategoryEnabled(category: String, enabled: Boolean) {
        prefs.edit().putBoolean(category, enabled).apply()
    }

    fun saveNotification(item: NotificationItem) {
        val currentItems = getSavedNotifications().toMutableList()
        currentItems.add(0, item)
        prefs.edit().putString(HIDDEN_LOG_KEY, toJson(currentItems)).apply()
    }

    fun getSavedNotifications(): List<NotificationItem> {
        val raw = prefs.getString(HIDDEN_LOG_KEY, null) ?: return emptyList()
        val jsonArray = JSONArray(raw)
        val notifications = mutableListOf<NotificationItem>()

        for (index in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(index)
            notifications.add(
                NotificationItem(
                    text = jsonObject.optString(KEY_TEXT),
                    category = jsonObject.optString(KEY_CATEGORY),
                    timestamp = jsonObject.optLong(KEY_TIMESTAMP),
                    isShown = jsonObject.optBoolean(KEY_IS_SHOWN)
                )
            )
        }

        return notifications
    }

    private fun toJson(items: List<NotificationItem>): String {
        val jsonArray = JSONArray()
        items.forEach { item ->
            jsonArray.put(
                JSONObject().apply {
                    put(KEY_TEXT, item.text)
                    put(KEY_CATEGORY, item.category)
                    put(KEY_TIMESTAMP, item.timestamp)
                    put(KEY_IS_SHOWN, item.isShown)
                }
            )
        }
        return jsonArray.toString()
    }

    companion object {
        const val CATEGORY_EXAM = "EXAM"
        const val CATEGORY_ASSIGNMENT = "ASSIGNMENT"
        const val CATEGORY_EVENT = "EVENT"
        const val CATEGORY_GENERAL = "GENERAL"

        private const val PREFS_NAME = "smart_pesu_filter_prefs"
        private const val HIDDEN_LOG_KEY = "hidden_log_key"
        private const val KEY_TEXT = "text"
        private const val KEY_CATEGORY = "category"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_IS_SHOWN = "is_shown"
        private const val KEY_BRANCH = "branch"
        private const val KEY_SEMESTER = "semester"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    }
}
