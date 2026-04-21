package com.chitniskedar.pesufilter.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.chitniskedar.pesufilter.R
import com.chitniskedar.pesufilter.auth.LoginActivity
import com.chitniskedar.pesufilter.databinding.ActivityMainBinding
import com.chitniskedar.pesufilter.utils.PreferencesManager
import com.chitniskedar.pesufilter.worker.AnnouncementScheduler
import com.google.android.material.materialswitch.MaterialSwitch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferencesManager: PreferencesManager

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

        AnnouncementScheduler.schedulePeriodic(this)
        setupCategoryToggles()
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

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    }
}
