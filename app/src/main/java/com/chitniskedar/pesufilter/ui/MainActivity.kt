package com.chitniskedar.pesufilter.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch
import com.chitniskedar.pesufilter.R
import com.chitniskedar.pesufilter.data.NotificationItem
import com.chitniskedar.pesufilter.databinding.ActivityMainBinding
import com.chitniskedar.pesufilter.utils.FilterManager
import com.chitniskedar.pesufilter.utils.NotificationHelper
import com.chitniskedar.pesufilter.utils.PreferencesManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var filterManager: FilterManager
    private lateinit var notificationHelper: NotificationHelper

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
        if (!preferencesManager.isOnboardingComplete()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        filterManager = FilterManager(preferencesManager)
        notificationHelper = NotificationHelper(this)

        bindProfileSummary()
        setupCategoryToggles()
        setupButtons()
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        bindProfileSummary()
        updateAccessStatus()
    }

    private fun setupCategoryToggles() {
        bindToggle(binding.switchExams, PreferencesManager.CATEGORY_EXAM)
        bindToggle(binding.switchAssignments, PreferencesManager.CATEGORY_ASSIGNMENT)
        bindToggle(binding.switchEvents, PreferencesManager.CATEGORY_EVENT)
        bindToggle(binding.switchGeneral, PreferencesManager.CATEGORY_GENERAL)
    }

    private fun bindToggle(toggle: MaterialSwitch, category: String) {
        toggle.isChecked = preferencesManager.isCategoryEnabled(category)
        toggle.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.setCategoryEnabled(category, isChecked)
        }
    }

    private fun setupButtons() {
        binding.buttonEnableAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        binding.buttonEditProfile.setOnClickListener {
            startActivity(
                Intent(this, OnboardingActivity::class.java).apply {
                    putExtra(OnboardingActivity.EXTRA_EDIT_MODE, true)
                }
            )
        }

        binding.buttonOpenLog.setOnClickListener {
            startActivity(Intent(this, HiddenLogActivity::class.java))
        }

        binding.buttonSendExamTest.setOnClickListener {
            simulateNotification("PESU Alerts", "CSE Sem 1 Internal Exam Tomorrow")
        }

        binding.buttonSendEventTest.setOnClickListener {
            simulateNotification("PESU Events", "Workshop for all branches")
        }
    }

    private fun simulateNotification(title: String, text: String) {
        val timestamp = System.currentTimeMillis()
        val result = filterManager.process(
            packageName = FAKE_PESU_PACKAGE,
            title = title,
            text = text
        )

        val message = "$title - $text"
        val category = result.category ?: PreferencesManager.CATEGORY_GENERAL

        if (result.shouldShow) {
            notificationHelper.showImportantNotification(message, timestamp)
            preferencesManager.saveNotification(
                NotificationItem(
                    text = message,
                    category = category,
                    timestamp = timestamp,
                    isShown = true
                )
            )
        } else {
            preferencesManager.saveNotification(
                NotificationItem(
                    text = message,
                    category = category,
                    timestamp = timestamp,
                    isShown = false
                )
            )
            Toast.makeText(this, R.string.test_mode_filtered, Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindProfileSummary() {
        val branch = preferencesManager.getSelectedBranch().orEmpty()
        val semester = preferencesManager.getSelectedSemester()?.toString().orEmpty()
        binding.textProfileSummary.text = getString(R.string.profile_summary, branch, semester)
    }

    private fun updateAccessStatus() {
        val enabled = isNotificationServiceEnabled(this)
        binding.textAccessStatus.text = getString(
            if (enabled) R.string.notification_access_enabled else R.string.notification_access_disabled
        )
        binding.buttonEnableAccess.text = getString(
            if (enabled) R.string.manage_access else R.string.enable_access
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun isNotificationServiceEnabled(context: Context): Boolean {
        val componentName = ComponentName(context, com.chitniskedar.pesufilter.service.NotificationListener::class.java)
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()

        return enabledListeners.contains(componentName.flattenToString())
    }

    companion object {
        private const val FAKE_PESU_PACKAGE = "com.pesuacademy.app"
    }
}
