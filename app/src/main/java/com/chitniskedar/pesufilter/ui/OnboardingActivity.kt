package com.chitniskedar.pesufilter.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.chitniskedar.pesufilter.R
import com.chitniskedar.pesufilter.auth.LoginActivity
import com.chitniskedar.pesufilter.databinding.ActivityOnboardingBinding
import com.chitniskedar.pesufilter.utils.PreferencesManager
import com.chitniskedar.pesufilter.worker.BackgroundSyncService
import com.chitniskedar.pesufilter.worker.AnnouncementScheduler
import com.google.android.material.materialswitch.MaterialSwitch

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var preferencesManager: PreferencesManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { updatePermissionState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferencesManager = PreferencesManager(this)
        if (!preferencesManager.hasActiveSession()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        if (preferencesManager.isSetupDone() && !intent.getBooleanExtra(EXTRA_EDIT_MODE, false)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPickers()
        restoreSavedProfile()
        setupCategoryToggles()
        binding.buttonContinue.setOnClickListener { saveProfileAndContinue() }
        binding.buttonResetFilters.setOnClickListener { resetFilterDefaults() }
        requestNotificationPermissionIfNeeded()
        updatePermissionState()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionState()
    }

    private fun setupPickers() {
        val branchAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.branch_options,
            R.layout.item_spinner_selected
        ).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }

        val semesterAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.semester_options,
            R.layout.item_spinner_selected
        ).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }

        binding.spinnerBranch.adapter = branchAdapter
        binding.spinnerSemester.adapter = semesterAdapter
    }

    private fun restoreSavedProfile() {
        val branch = preferencesManager.getSelectedBranch()
        val semester = preferencesManager.getSelectedSemester()

        val branches = resources.getStringArray(R.array.branch_options)
        val semesters = resources.getStringArray(R.array.semester_options)

        val branchIndex = branches.indexOf(branch).takeIf { it >= 0 } ?: 0
        val semesterIndex = semester?.let { semesters.indexOf(it.toString()) }?.takeIf { it >= 0 } ?: 0

        binding.spinnerBranch.setSelection(branchIndex)
        binding.spinnerSemester.setSelection(semesterIndex)
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
        }
    }

    private fun resetFilterDefaults() {
        preferencesManager.setCategoryEnabled(PreferencesManager.CATEGORY_EXAM, true)
        preferencesManager.setCategoryEnabled(PreferencesManager.CATEGORY_NOTICE, true)
        preferencesManager.setCategoryEnabled(PreferencesManager.CATEGORY_INTERNSHIP, true)
        preferencesManager.setCategoryEnabled(PreferencesManager.CATEGORY_GENERAL, true)

        binding.switchExams.isChecked = true
        binding.switchAssignments.isChecked = true
        binding.switchInternships.isChecked = true
        binding.switchGeneral.isChecked = true
    }

    private fun saveProfileAndContinue() {
        val branch = binding.spinnerBranch.selectedItem?.toString().orEmpty()
        val semester = binding.spinnerSemester.selectedItem?.toString()?.toIntOrNull()

        if (branch.isBlank() || semester == null) {
            Toast.makeText(this, R.string.complete_profile_prompt, Toast.LENGTH_SHORT).show()
            return
        }

        preferencesManager.saveUserProfile(branch, semester)
        AnnouncementScheduler.schedulePeriodic(this)
        AnnouncementScheduler.enqueueImmediate(this)
        BackgroundSyncService.start(this)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun updatePermissionState() {
        val notificationsEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        binding.textPostPermissionStatus.text = getString(
            if (notificationsEnabled) R.string.post_notifications_granted else R.string.post_notifications_missing
        )
    }

    companion object {
        const val EXTRA_EDIT_MODE = "edit_mode"
    }
}
