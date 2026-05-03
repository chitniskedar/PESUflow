package com.chitniskedar.pesufilter.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AnnouncementScheduler {

    fun schedulePeriodic(context: Context) {
        val periodicRequest = PeriodicWorkRequestBuilder<AnnouncementSyncWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(defaultConstraints())
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )
    }

    fun enqueueImmediate(context: Context) {
        val oneTimeRequest = OneTimeWorkRequestBuilder<AnnouncementSyncWorker>()
            .setConstraints(defaultConstraints())
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            oneTimeRequest
        )
    }

    private fun defaultConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }

    private const val PERIODIC_WORK_NAME = "pesuflow_periodic_sync"
    private const val IMMEDIATE_WORK_NAME = "pesuflow_immediate_sync"
}
