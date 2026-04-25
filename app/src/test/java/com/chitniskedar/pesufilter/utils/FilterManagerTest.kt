package com.chitniskedar.pesufilter.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FilterManagerTest {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var filterManager: FilterManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("pesuflow_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        preferencesManager = PreferencesManager(context)
        preferencesManager.saveUserProfile("CSE", 4)
        preferencesManager.setCategoryEnabled(PreferencesManager.CATEGORY_EXAM, true)
        preferencesManager.setCategoryEnabled(PreferencesManager.CATEGORY_NOTICE, true)
        preferencesManager.setCategoryEnabled(PreferencesManager.CATEGORY_INTERNSHIP, true)
        preferencesManager.setCategoryEnabled(PreferencesManager.CATEGORY_GENERAL, true)
        filterManager = FilterManager(preferencesManager)
    }

    @Test
    fun shouldShow_usesTitleKeywordsWithMatchingBranchAndSemester() {
        val combined = "ISA timetable released\nFor CSE 4th semester students."

        assertTrue(filterManager.shouldShow(combined))
        assertEquals(PreferencesManager.CATEGORY_EXAM, filterManager.categorize(combined))
    }

    @Test
    fun shouldHide_whenSemesterDoesNotMatch() {
        val combined = "ISA timetable released\nFor CSE 6th semester students."

        assertFalse(filterManager.shouldShow(combined))
    }

    @Test
    fun shouldHide_whenCategoryDisabled() {
        preferencesManager.setCategoryEnabled(PreferencesManager.CATEGORY_INTERNSHIP, false)

        val combined = "Internship drive open\nInternship opportunity for CSE 4th semester students."

        assertFalse(filterManager.shouldShow(combined))
        assertEquals(PreferencesManager.CATEGORY_INTERNSHIP, filterManager.categorize(combined))
    }

    @Test
    fun shouldShow_globalAnnouncements() {
        val combined = "Holiday notice\nGeneral notice for all students across all branches and all semesters."

        assertTrue(filterManager.shouldShow(combined))
        assertEquals(PreferencesManager.CATEGORY_NOTICE, filterManager.categorize(combined))
    }
}
