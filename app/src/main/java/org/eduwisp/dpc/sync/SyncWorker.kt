// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc.sync

import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.eduwisp.dpc.BuildConfig
import org.eduwisp.dpc.policy.AndroidDeviceControls
import org.eduwisp.dpc.policy.LocalPolicy
import org.eduwisp.dpc.policy.PolicyApplier
import org.eduwisp.dpc.protocol.AgentInfo
import org.eduwisp.dpc.store.PrefsAgentStore
import java.util.concurrent.TimeUnit

class AppExpiryWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        LocalPolicy.reapply(applicationContext)
        return Result.success()
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val store = PrefsAgentStore(applicationContext)
        val agent = Agent(store, PolicyApplier(AndroidDeviceControls(applicationContext)), agentInfo())
        // Un ciclo alla volta: due registrazioni parallele creerebbero due dispositivi sul server.
        val outcome = synchronized(LOCK) { agent.sync() }
        Sync.schedule(applicationContext, store.pollIntervalSeconds)
        Sync.scheduleAppExpiry(applicationContext, store.nextAppExpiryMillis, System.currentTimeMillis())
        return when (outcome) {
            Outcome.DONE, Outcome.STOP -> Result.success()
            Outcome.RETRY -> Result.retry()
        }
    }

    companion object {
        val LOCK = Any()

        fun agentInfo() = AgentInfo(
            agentVersion = BuildConfig.VERSION_NAME,
            osVersion = Build.VERSION.RELEASE,
            model = Build.MODEL,
        )
    }
}

object Sync {
    private const val PERIODIC = "eduwisp-sync"
    private const val NOW = "eduwisp-sync-now"

    /** WorkManager non scende sotto i 15 minuti per il lavoro periodico. */
    private const val MIN_PERIOD_SECONDS = 15 * 60L

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(context: Context, intervalSeconds: Int) {
        val period = maxOf(intervalSeconds.toLong(), MIN_PERIOD_SECONDS)
        val request = PeriodicWorkRequestBuilder<SyncWorker>(period, TimeUnit.SECONDS)
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .addTag("period:$period")
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private const val APP_EXPIRY = "eduwisp-app-expiry"

    /** Alla scadenza di uno sblocco l'app si richiude, anche senza rete (ADR-0019). */
    fun scheduleAppExpiry(context: Context, expiryMillis: Long?, nowMillis: Long) {
        // Nessuna scadenza: un lavoro rimasto in coda riapplicherebbe solo la stessa policy.
        // Non lo si cancella, perché può essere proprio il lavoro in corso.
        val manager = WorkManager.getInstance(context)
        if (expiryMillis == null) return
        val request = OneTimeWorkRequestBuilder<AppExpiryWorker>()
            .setInitialDelay(maxOf(expiryMillis - nowMillis, 0) + 1000, TimeUnit.MILLISECONDS)
            .build()
        manager.enqueueUniqueWork(APP_EXPIRY, ExistingWorkPolicy.REPLACE, request)
    }

    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }
}
