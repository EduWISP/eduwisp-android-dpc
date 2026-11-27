// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.eduwisp.dpc.protocol.AppRequestMessage
import org.eduwisp.dpc.protocol.ProtocolClient
import org.eduwisp.dpc.store.PrefsAgentStore
import org.eduwisp.dpc.ui.AppRequests
import java.util.concurrent.TimeUnit

/** Invia la richiesta di un'app (ADR-0021); senza rete riprova più tardi. */
class AppRequestWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val store = PrefsAgentStore(applicationContext)
        val server = store.serverUrl ?: return Result.failure()
        val token = store.deviceToken ?: return Result.retry()
        val message = AppRequestMessage(id, inputData.getString(KEY_NAME), inputData.getString(KEY_NOTE))
        val requests = AppRequests(applicationContext)
        return when (val r = ProtocolClient(server).appRequest(token, message)) {
            is org.eduwisp.dpc.protocol.Result.Ok -> {
                requests.setStatus(id, r.value)
                Result.success()
            }
            is org.eduwisp.dpc.protocol.Result.Rejected -> {
                requests.setStatus(id, if (r.error.code == "not_personal_device") AppRequests.NOT_PERSONAL else AppRequests.ERROR)
                Result.success()
            }
            is org.eduwisp.dpc.protocol.Result.Unreachable -> Result.retry()
        }
    }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_NOTE = "note"

        fun enqueue(context: Context, id: String, name: String?, note: String?) {
            val request = OneTimeWorkRequestBuilder<AppRequestWorker>()
                .setInputData(workDataOf(KEY_ID to id, KEY_NAME to name, KEY_NOTE to note))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("eduwisp-richiesta-$id", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
