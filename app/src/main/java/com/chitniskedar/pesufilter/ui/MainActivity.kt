package com.chitniskedar.pesufilter.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chitniskedar.pesufilter.R
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

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val periodicRefresh = object : Runnable {
        override fun run() {
            AnnouncementScheduler.enqueueImmediate(this@MainActivity)
            refreshUi()
            refreshHandler.postDelayed(this, FOREGROUND_REFRESH_INTERVAL_MS)
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
        if (!preferencesManager.isSetupDone()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        filterManager = FilterManager(preferencesManager)
        announcementAdapter = AnnouncementAdapter()
        binding.recyclerAnnouncements.layoutManager = LinearLayoutManager(this)
        binding.recyclerAnnouncements.adapter = announcementAdapter

        AnnouncementScheduler.schedulePeriodic(this)
        setupCategoryToggles()
        setupButtons()
        requestNotificationPermissionIfNeeded()
        refreshUi()
    }

    override fun onStart() {
        super.onStart()
        refreshHandler.post(periodicRefresh)
    }

    override fun onStop() {
        refreshHandler.removeCallbacks(periodicRefresh)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun setupCategoryToggles() {
        bindToggle(binding.switchExams, PreferencesManager.CATEGORY_EXAM)
        bindToggle(binding.switchAssignments, PreferencesManager.CATEGORY_ASSIGNMENT)
        bindToggle(binding.switchInternships, PreferencesManager.CATEGORY_INTERNSHIP)
        bindToggle(binding.switchGeneral, PreferencesManager.CATEGORY_GENERAL)
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
            AnnouncementScheduler.enqueueImmediate(this)
            Toast.makeText(this, R.string.refresh_started, Toast.LENGTH_SHORT).show()
            refreshUi()
        }

        binding.buttonOpenLog.setOnClickListener {
            startActivity(Intent(this, HiddenLogActivity::class.java))
        }
    }

    private fun refreshUi() {
        bindProfileSummary()
        bindSyncStatus()

        val announcements = preferencesManager.getSavedAnnouncements()
        val filteredAnnouncements = announcements.filter { announcement ->
            filterManager.shouldShow("${announcement.title} ${announcement.date}")
        }

        announcementAdapter.submitList(filteredAnnouncements)
        binding.textEmptyState.text = getString(
            if (filteredAnnouncements.isEmpty()) R.string.no_announcements_found
            else R.string.recent_announcements
        )
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

    private class AnnouncementAdapter : RecyclerView.Adapter<AnnouncementAdapter.AnnouncementViewHolder>() {
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
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        class AnnouncementViewHolder(
            private val binding: ItemHiddenNotificationBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: Announcement) {
                binding.textMessage.text = item.title
                binding.textCategory.text = item.date
                binding.textTimestamp.text = item.link ?: itemView.context.getString(R.string.no_attachment_link)
                binding.textShownStatus.text = itemView.context.getString(R.string.filtered_for_you)
            }
        }
    }

    companion object {
        private const val FOREGROUND_REFRESH_INTERVAL_MS = 5 * 60 * 1000L
        private val DATE_FORMAT = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    }
}
