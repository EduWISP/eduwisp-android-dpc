// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc.protocol

import org.json.JSONArray
import org.json.JSONObject

/** Messaggi del protocollo EduWISP v0 (https://eduwisp.org/protocol/v0/). */
const val PROTOCOL_VERSION = "0"
const val PLATFORM = "android"

class ProtocolException(message: String) : Exception(message)

data class AgentInfo(
    val agentVersion: String,
    val osVersion: String,
    val model: String?,
)

data class EnrollmentRequest(
    val enrollmentToken: String,
    val agent: AgentInfo,
    val capabilities: List<String>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("protocol_version", PROTOCOL_VERSION)
        .put("enrollment_token", enrollmentToken)
        .put(
            "agent",
            JSONObject()
                .put("platform", PLATFORM)
                .put("agent_version", agentVersion(agent.agentVersion))
                .put("os_version", agent.osVersion.take(32))
                .apply { agent.model?.takeIf { it.isNotBlank() }?.let { put("model", it.take(64)) } },
        )
        .put("capabilities", JSONArray(capabilities.distinct()))
}

data class EnrollmentResponse(
    val deviceId: String,
    val deviceToken: String,
    val pollIntervalSeconds: Int,
) {
    companion object {
        fun parse(json: JSONObject): EnrollmentResponse {
            checkVersion(json)
            val response = EnrollmentResponse(
                deviceId = json.getString("device_id"),
                deviceToken = json.getString("device_token"),
                pollIntervalSeconds = json.getInt("poll_interval_seconds"),
            )
            if (!response.deviceToken.startsWith(response.deviceId + ".")) {
                throw ProtocolException("device_token non corrisponde a device_id")
            }
            return response
        }
    }
}

data class Policy(
    val policyVersion: Int,
    val pollIntervalSeconds: Int,
    /** Impostazioni ricevute; quelle sconosciute restano qui e vengono segnalate come non supportate. */
    val settings: Map<String, Any>,
) {
    companion object {
        fun parse(json: JSONObject): Policy {
            checkVersion(json)
            val raw = json.getJSONObject("settings")
            val settings = raw.keys().asSequence().associateWith { raw.get(it) }
            return Policy(
                policyVersion = json.getInt("policy_version"),
                pollIntervalSeconds = json.getInt("poll_interval_seconds"),
                settings = settings,
            )
        }
    }
}

enum class NonComplianceReason(val wire: String) { UNSUPPORTED("unsupported"), FAILED("failed"), PENDING("pending") }

data class NonCompliance(val setting: String, val reason: NonComplianceReason)

data class StatusReport(
    val policyVersionApplied: Int,
    val nonCompliance: List<NonCompliance>,
    val agentVersion: String,
    val osVersion: String,
) {
    val compliant: Boolean get() = nonCompliance.isEmpty()

    fun toJson(): JSONObject = JSONObject()
        .put("protocol_version", PROTOCOL_VERSION)
        .put("policy_version_applied", policyVersionApplied)
        .put("compliant", compliant)
        .put(
            "non_compliance",
            JSONArray(
                nonCompliance.take(100).map {
                    JSONObject().put("setting", it.setting).put("reason", it.reason.wire)
                },
            ),
        )
        .put("agent_version", agentVersion(agentVersion))
        .put("os_version", osVersion.take(32))
}

data class ProtocolError(val code: String, val message: String) {
    companion object {
        fun parse(json: JSONObject): ProtocolError {
            val error = json.getJSONObject("error")
            return ProtocolError(error.getString("code"), error.optString("message"))
        }
    }
}

private fun agentVersion(value: String) = value.take(32)

private fun checkVersion(json: JSONObject) {
    val version = json.optString("protocol_version")
    if (version != PROTOCOL_VERSION) throw ProtocolException("protocol_version non supportata: $version")
}
