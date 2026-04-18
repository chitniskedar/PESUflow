package com.chitniskedar.pesufilter.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chitniskedar.pesufilter.R
import com.chitniskedar.pesufilter.auth.LoginActivity
import com.chitniskedar.pesufilter.databinding.ActivityMainBinding
import com.chitniskedar.pesufilter.databinding.ItemHiddenNotificationBinding
import com.chitniskedar.pesufilter.model.Announcement
import com.chitniskedar.pesufilter.utils.FilterManager
import com.chitniskedar.pesufilter.utils.PreferencesManager
import com.chitniskedar.pesufilter.worker.AnnouncementScheduler
import com.google.android.material.materialswitch.MaterialSwitch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var filterManager: FilterManager
    private lateinit var announcementAdapter: AnnouncementAdapter

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
        if (!routeIfNeeded()) {
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        filterManager = FilterManager(preferencesManager)
        announcementAdapter = AnnouncementAdapter { announcement ->
            getString(
                R.string.filtered_with_priority,
                filterManager.priorityFor(announcement.fullText)
                    .name
                    .lowercase()
                    .replaceFirstChar { it.titlecase() }
            )
        }
        binding.recyclerAnnouncements.layoutManager = LinearLayoutManager(this)
        binding.recyclerAnnouncements.adapter = announcementAdapter

        AnnouncementScheduler.schedulePeriodic(this)
        setupCategoryToggles()
        setupTestModeToggle()
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
    }

    private fun routeIfNeeded(): Boolean {
        return when {
            !preferencesManager.hasActiveSession() -> {
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

    private fun setupCategoryToggles() {
        bindToggle(binding.switchExams, PreferencesManager.CATEGORY_EXAM)
        bindToggle(binding.switchAssignments, PreferencesManager.CATEGORY_NOTICE)
        bindToggle(binding.switchInternships, PreferencesManager.CATEGORY_INTERNSHIP)
        bindToggle(binding.switchGeneral, PreferencesManager.CATEGORY_GENERAL)
    }

    private fun setupTestModeToggle() {
        binding.switchTestMode.isChecked = preferencesManager.isTestModeEnabled()
        binding.switchTestMode.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.setTestModeEnabled(isChecked)
            if (isChecked) {
                Toast.makeText(this, R.string.test_mode_enabled, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindToggle(toggle: MaterialSwitch, category: String) {
        toggle.isChecked = preferencesManager.isCategoryEnabled(category)
        toggle.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.setCategoryEnabled(category, isChecked)
            refreshUi()
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
            val forceTestMode = preferencesManager.isTestModeEnabled()
            AnnouncementScheduler.enqueueImmediate(this, forceTestMode = forceTestMode)
            Toast.makeText(
                this,
                if (forceTestMode) R.string.test_refresh_started else R.string.refresh_started,
                Toast.LENGTH_SHORT
            ).show()
            refreshUi()
        }

        binding.buttonOpenLog.setOnClickListener {
            startActivity(Intent(this, HiddenLogActivity::class.java))
        }
    }

    private fun refreshUi() {
        bindProfileSummary()
        bindSyncStatus()

        val filteredAnnouncements = preferencesManager.getSavedAnnouncements().filter { announcement ->
            filterManager.shouldShow(announcement.fullText)
        }

        announcementAdapter.submitList(filteredAnnouncements)
        binding.textEmptyState.visibility = if (filteredAnnouncements.isEmpty()) View.VISIBLE else View.GONE
        binding.textEmptyState.text = getString(R.string.no_announcements_found)
    }

    private fun bindProfileSummary() {
        val branch = preferencesManager.getSelectedBranch().orEmpty()
        val semester = preferencesManager.getSelectedSemester()?.toString().orEmpty()
        binding.textProfileSummary.text = getString(R.string.profile_summary, branch, semester)
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

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private class AnnouncementAdapter(
        private val priorityLabelProvider: (Announcement) -> String
    ) : RecyclerView.Adapter<AnnouncementAdapter.AnnouncementViewHolder>() {
        private val items = mutableListOf<Announcement>()

        fun submitList(data: List<Announcement>) {
            items.clear()
            items.addAll(data)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnnouncementViewHolder {
            val binding = ItemHiddenNotificationBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return AnnouncementViewHolder(binding)
        }

        override fun onBindViewHolder(holder: AnnouncementViewHolder, position: Int) {
            holder.bind(items[position], priorityLabelProvider(items[position]))
        }

        override fun getItemCount(): Int = items.size

        class AnnouncementViewHolder(
            private val binding: ItemHiddenNotificationBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: Announcement, statusText: String) {
                binding.textMessage.text = item.title
                binding.textCategory.text = item.date
                binding.textTimestamp.text = item.fullText
                binding.textShownStatus.text = statusText
            }
        }
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    }
}
