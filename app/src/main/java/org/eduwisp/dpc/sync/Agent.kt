// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc.sync

import org.eduwisp.dpc.policy.PolicyApplier
import org.eduwisp.dpc.protocol.AgentInfo
import org.eduwisp.dpc.protocol.EnrollmentRequest
import org.eduwisp.dpc.protocol.ProtocolClient
import org.eduwisp.dpc.protocol.Result
import org.eduwisp.dpc.protocol.StatusReport
import org.eduwisp.dpc.store.AgentStore

enum class Outcome { DONE, RETRY, STOP }

/**
 * Un ciclo dell'agente: registrazione (una volta), lettura della policy,
 * applicazione e rapporto di stato. Funziona anche offline: la policy già
 * applicata resta in vigore finché il server non risponde (RNF offline).
 */
class Agent(
    private val store: AgentStore,
    private val applier: PolicyApplier,
    private val info: AgentInfo,
    private val clientFor: (String) -> ProtocolClient = ::ProtocolClient,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun sync(): Outcome {
        if (store.retired) return Outcome.STOP
        val server = store.serverUrl ?: return Outcome.STOP
        val client = clientFor(server)

        val token = store.deviceToken ?: when (val enrollment = enroll(client)) {
            is Enrollment.Enrolled -> enrollment.deviceToken
            Enrollment.Later -> return Outcome.RETRY
            Enrollment.Impossible -> return Outcome.STOP
        }

        val policy = when (val result = client.policy(token)) {
            is Result.Ok -> result.value
            is Result.Rejected -> return rejected(result)
            is Result.Unreachable -> return unreachable(result)
        }
        val issues = applier.apply(policy)
        store.policyVersionApplied = policy.policyVersion
        store.pollIntervalSeconds = policy.pollIntervalSeconds
        store.nonCompliance = issues.map { "${it.setting}:${it.reason.wire}" }

        val report = StatusReport(policy.policyVersion, issues, info.agentVersion, info.osVersion)
        return when (val result = client.status(token, report)) {
            is Result.Ok -> {
                store.lastSyncMillis = now()
                store.lastError = null
                Outcome.DONE
            }
            is Result.Rejected -> rejected(result)
            is Result.Unreachable -> unreachable(result)
        }
    }

    private sealed interface Enrollment {
        data class Enrolled(val deviceToken: String) : Enrollment
        data object Later : Enrollment
        data object Impossible : Enrollment
    }

    private fun enroll(client: ProtocolClient): Enrollment {
        val enrollmentToken = store.enrollmentToken ?: return Enrollment.Impossible
        val request = EnrollmentRequest(enrollmentToken, info, applier.capabilities)
        return when (val result = client.enroll(request)) {
            is Result.Ok -> {
                store.deviceId = result.value.deviceId
                store.deviceToken = result.value.deviceToken
                store.pollIntervalSeconds = result.value.pollIntervalSeconds
                store.enrollmentToken = null
                Enrollment.Enrolled(result.value.deviceToken)
            }
            is Result.Rejected -> {
                // Token non valido, scaduto o esaurito: serve un nuovo QR code.
                store.enrollmentToken = null
                store.lastError = result.error.code
                Enrollment.Impossible
            }
            is Result.Unreachable -> {
                store.lastError = result.reason
                Enrollment.Later
            }
        }
    }

    private fun rejected(result: Result.Rejected): Outcome {
        store.lastError = result.error.code
        return when (result.error.code) {
            "device_retired" -> {
                store.retired = true
                Outcome.STOP
            }
            // "unauthorized": la credenziale resta salvata, l'errore può essere temporaneo
            // lato server; se il dispositivo è stato eliminato serve un nuovo QR code.
            else -> Outcome.RETRY
        }
    }

    private fun unreachable(result: Result.Unreachable): Outcome {
        store.lastError = result.reason
        return Outcome.RETRY
    }
}
