package com.chitniskedar.pesufilter.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.chitniskedar.pesufilter.R
import com.chitniskedar.pesufilter.auth.LoginActivity
import com.chitniskedar.pesufilter.databinding.ActivityMainBinding
import com.chitniskedar.pesufilter.model.Announcement
import com.chitniskedar.pesufilter.utils.FilterManager
import com.chitniskedar.pesufilter.utils.NotificationHelper
import com.chitniskedar.pesufilter.utils.PreferencesManager
import com.chitniskedar.pesufilter.worker.AnnouncementScheduler
import com.chitniskedar.pesufilter.worker.BackgroundSyncService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var filterManager: FilterManager
    private val autoRefreshHandler = Handler(Looper.getMainLooper())
    private val autoRefreshRunnable = object : Runnable {
        override fun run() {
            triggerRefresh(showToast = false)
            autoRefreshHandler.postDelayed(this, AUTO_REFRESH_INTERVAL_MS)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, R.string.notification_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferencesManager = PreferencesManager(this)
        notificationHelper = NotificationHelper(this)
        filterManager = FilterManager(preferencesManager)
        if (!routeIfNeeded()) {
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AnnouncementScheduler.schedulePeriodic(this)
        BackgroundSyncService.start(this)
        setupButtons()
        requestNotificationPermissionIfNeeded()
        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        if (!routeIfNeeded()) {
            finish()
            return
        }
        refreshUi()
        startAutoRefresh()
    }

    override fun onPause() {
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable)
        super.onPause()
    }

    private fun routeIfNeeded(): Boolean {
        return when {
            !preferencesManager.hasActiveSession() -> {
                BackgroundSyncService.stop(this)
                startActivity(
                    Intent(this, LoginActivity::class.java).apply {
                        putExtra(LoginActivity.EXTRA_FORCE_RELOGIN, preferencesManager.isSetupDone())
                    }
                )
                false
            }

            !preferencesManager.isSetupDone() -> {
                startActivity(Intent(this, OnboardingActivity::class.java))
                false
            }

            else -> true
        }
    }

    private fun setupButtons() {
        binding.buttonEditProfile.setOnClickListener {
            startActivity(
                Intent(this, OnboardingActivity::class.java).apply {
                    putExtra(OnboardingActivity.EXTRA_EDIT_MODE, true)
                }
            )
        }

        binding.buttonRefreshNow.setOnClickListener {
            if (!preferencesManager.hasActiveSession()) {
                startActivity(
                    Intent(this, LoginActivity::class.java).apply {
                        putExtra(LoginActivity.EXTRA_FORCE_RELOGIN, true)
                    }
                )
                return@setOnClickListener
            }
            triggerRefresh(showToast = true)
        }

        binding.buttonOpenLog.setOnClickListener {
            ensureSavedHistoryForTesting()
            startActivity(Intent(this, HiddenLogActivity::class.java))
        }

        binding.buttonRunFilterTest.setOnClickListener {
            runFilterTestMode()
        }

        binding.buttonReplaySavedNotifications.setOnClickListener {
            replaySavedNotifications()
        }

        binding.buttonLogout.setOnClickListener {
            logout()
        }
    }

    private fun refreshUi() {
        bindProfileSummary()
        bindSyncStatus()
        bindNotificationStatus()
        bindSavedAnnouncementCount()
        bindAutoRefreshStatus()
        bindActiveFilterChips()
    }

    private fun bindProfileSummary() {
        val branch = preferencesManager.getSelectedBranch().orEmpty()
        val semester = preferencesManager.getSelectedSemester()?.toString().orEmpty()
        binding.textProfileSummary.text = getString(R.string.profile_summary, branch, semester)
    }

    private fun bindActiveFilterChips() {
        val batch = preferencesManager.getSelectedBranch().orEmpty().ifBlank { "Not set" }
        val semester = preferencesManager.getSelectedSemester()?.toString().orEmpty().ifBlank { "Not set" }
        val enabledCategories = buildList {
            if (preferencesManager.isCategoryEnabled(PreferencesManager.CATEGORY_EXAM)) add("Exams")
            if (preferencesManager.isCategoryEnabled(PreferencesManager.CATEGORY_NOTICE)) add("Notices")
            if (preferencesManager.isCategoryEnabled(PreferencesManager.CATEGORY_INTERNSHIP)) add("Internships")
            if (preferencesManager.isCategoryEnabled(PreferencesManager.CATEGORY_GENERAL)) add("General")
        }.joinToString(", ").ifBlank { "None" }

        binding.textBatchChip.text = getString(R.string.profile_batch_chip, batch)
        binding.textSemesterChip.text = getString(R.string.profile_semester_chip, semester)
        binding.textCategoryChip.text = getString(R.string.profile_category_chip, enabledCategories)
    }

    private fun bindSyncStatus() {
        val lastSync = preferencesManager.getLastSyncTimestamp()
        val lastError = preferencesManager.getLastSyncError()

        binding.textSyncStatus.text = when {
            lastError != null -> getString(R.string.last_sync_error, lastError)
            lastSync != null -> getString(R.string.last_sync_success, DATE_FORMAT.format(Date(lastSync)))
            else -> getString(R.string.no_sync_yet)
        }
    }

    private fun bindNotificationStatus() {
        binding.textNotificationStatus.text = getString(
            if (notificationHelper.canPostNotifications()) {
                R.string.notification_status_ready_short
            } else {
                R.string.notification_status_blocked_short
            }
        )
    }

    private fun bindSavedAnnouncementCount() {
        binding.textSavedAnnouncementCount.text = getString(
            R.string.saved_announcements_count,
            preferencesManager.getSavedAnnouncements().size
        )
    }

    private fun bindAutoRefreshStatus() {
        binding.textAutoRefreshStatus.text = getString(R.string.auto_refresh_status)
    }

    private fun triggerRefresh(showToast: Boolean) {
        AnnouncementScheduler.enqueueImmediate(this)
        if (showToast) {
            Toast.makeText(this, R.string.refresh_started, Toast.LENGTH_SHORT).show()
        }
        refreshUi()
    }

    private fun startAutoRefresh() {
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable)
        if (!preferencesManager.hasActiveSession()) {
            return
        }
        autoRefreshHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_INTERVAL_MS)
    }

    private fun runFilterTestMode() {
        val results = SAMPLE_TEST_ANNOUNCEMENTS.joinToString("\n\n") { announcement ->
            val combinedText = announcement.title + "\n" + announcement.fullText
            val matches = filterManager.shouldShow(combinedText)
            val category = filterManager.categorize(combinedText)
            getString(
                R.string.filter_test_result_line,
                if (matches) getString(R.string.filter_test_match) else getString(R.string.filter_test_skip),
                announcement.title,
                category,
                announcement.fullText
            )
        }

        binding.textFilterTestHeader.visibility = View.VISIBLE
        binding.textFilterTestResults.visibility = View.VISIBLE
        binding.textFilterTestResults.text = results
    }

    private fun replaySavedNotifications() {
        if (!notificationHelper.canPostNotifications()) {
            Toast.makeText(this, R.string.replay_saved_blocked, Toast.LENGTH_SHORT).show()
            return
        }

        val savedAnnouncements = preferencesManager.getSavedAnnouncements()
        if (savedAnnouncements.isEmpty()) {
            Toast.makeText(this, R.string.replay_saved_none, Toast.LENGTH_SHORT).show()
            return
        }

        val relevantAnnouncements = savedAnnouncements.filter { announcement ->
            filterManager.shouldShow(announcement.title + "\n" + announcement.fullText)
        }

        if (relevantAnnouncements.isEmpty()) {
            Toast.makeText(this, R.string.replay_saved_no_matches, Toast.LENGTH_SHORT).show()
            return
        }

        relevantAnnouncements.forEach { announcement ->
            notificationHelper.showAnnouncementNotification(
                announcement = announcement,
                priority = filterManager.priorityFor(announcement.title + "\n" + announcement.fullText)
            )
        }

        Toast.makeText(
            this,
            getString(R.string.replay_saved_count, relevantAnnouncements.size),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun ensureSavedHistoryForTesting() {
        if (preferencesManager.getSavedAnnouncements().isNotEmpty()) {
            return
        }

        preferencesManager.saveAnnouncements(SAVED_HISTORY_TEST_ANNOUNCEMENTS)
        refreshUi()
        Toast.makeText(this, R.string.history_seeded, Toast.LENGTH_SHORT).show()
    }

    private fun logout() {
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable)
        preferencesManager.clearSession()
        BackgroundSyncService.stop(this)
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                putExtra(LoginActivity.EXTRA_FORCE_RELOGIN, true)
            }
        )
        finish()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        private const val AUTO_REFRESH_INTERVAL_MS = 3 * 60 * 1000L
        private val SAMPLE_TEST_ANNOUNCEMENTS = listOf(
            Announcement(
                title = "CSE 4th sem ISA timetable",
                date = "Test",
                fullText = "ISA timetable released for CSE 4th semester students."
            ),
            Announcement(
                title = "ECE internship drive",
                date = "Test",
                fullText = "Internship opportunity open for ECE 6th semester students."
            ),
            Announcement(
                title = "All students holiday notice",
                date = "Test",
                fullText = "General notice for all students across all branches and all semesters."
            ),
            Announcement(
                title = "Assignment submission reminder",
                date = "Test",
                fullText = "Assignment submission notice for AIML 2nd semester."
            )
        )
        private val SAVED_HISTORY_TEST_ANNOUNCEMENTS = listOf(
            Announcement(
                title = "Executive M.Tech VLSI - Jul Cohort 2025 Batch - Sem 2 ISA 2 Timetable",
                date = "24-April-2026",
                fullText = "Executive M.Tech VLSI - Jul Cohort 2025 Batch - Sem 2 ISA 2 Timetable"
            ),
            Announcement(
                title = "Minor in MedTech (Medical Technologies)",
                date = "24-April-2026",
                fullText = "Minor in MedTech (Medical Technologies)"
            ),
            Announcement(
                title = "B.Com 6th Sem - ISA 2 Timetable - EC Campus",
                date = "20-April-2026",
                fullText = "B.Com 6th Sem - ISA 2 Timetable - EC Campus"
            ),
            Announcement(
                title = "B.Pharm 8th Sem ISA 2 Theory Timetable",
                date = "20-April-2026",
                fullText = "B.Pharm 8th Sem ISA 2 Theory Timetable"
            ),
            Announcement(
                title = "Notification #204 ESA MAY 2026 Regular Registration",
                date = "20-April-2026",
                fullText = "Notification #204 ESA MAY 2026 Regular Registration"
            ),
            Announcement(
                title = "B.Pharm 6th Sem - ISA 2 Theory Timetable",
                date = "17-April-2026",
                fullText = "B.Pharm 6th Sem - ISA 2 Theory Timetable"
            ),
            Announcement(
                title = "B.Pharm 4th Sem - ISA 2 Practical Timetable",
                date = "17-April-2026",
                fullText = "B.Pharm 4th Sem - ISA 2 Practical Timetable"
            ),
            Announcement(
                title = "B.Pharm 6th Sem - ISA 2 Practical Timetable",
                date = "17-April-2026",
                fullText = "B.Pharm 6th Sem - ISA 2 Practical Timetable"
            ),
            Announcement(
                title = "B.Pharm 4th Sem - ISA 2 Theory Timetable",
                date = "17-April-2026",
                fullText = "B.Pharm 4th Sem - ISA 2 Theory Timetable"
            ),
            Announcement(
                title = "BBA & BBA-Analytics 4th sem - ISA 2 Timetable",
                date = "17-April-2026",
                fullText = "BBA & BBA-Analytics 4th sem - ISA 2 Timetable"
            ),
            Announcement(
                title = "BBA & BBA-Analytics 2nd sem - ISA 2 Timetable",
                date = "17-April-2026",
                fullText = "BBA & BBA-Analytics 2nd sem - ISA 2 Timetable"
            ),
            Announcement(
                title = "B.Tech ECE 8th Sem - ISA 2 Timetable - EC Campus",
                date = "16-April-2026",
                fullText = "B.Tech ECE 8th Sem - ISA 2 Timetable - EC Campus"
            ),
            Announcement(
                title = "M.Pharm 1st Sem - ISA 1 Practical Timetable",
                date = "16-April-2026",
                fullText = "M.Pharm 1st Sem - ISA 1 Practical Timetable"
            ),
            Announcement(
                title = "Nursing 8th Sem - ISA 2 Theory and Practical Timetable",
                date = "16-April-2026",
                fullText = "Nursing 8th Sem - ISA 2 Theory and Practical Timetable"
            ),
            Announcement(
                title = "Nursing 6th Sem - ISA 2 Theory and Practical Timetable",
                date = "16-April-2026",
                fullText = "Nursing 6th Sem - ISA 2 Theory and Practical Timetable"
            ),
            Announcement(
                title = "Nursing 4th Sem - ISA 2 Theory and Practical Timetable",
                date = "16-April-2026",
                fullText = "Nursing 4th Sem - ISA 2 Theory and Practical Timetable"
            )
        )
    }
}
