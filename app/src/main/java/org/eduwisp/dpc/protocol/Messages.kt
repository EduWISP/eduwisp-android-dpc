// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc.protocol

import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

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

/**
 * Presente solo se il dispositivo è affidato a uno studente maggiorenne (ADR-0018).
 * [granted] è la scelta registrata dal server, null se lo studente non ha ancora scelto.
 */
data class FamilyAccess(val granted: Boolean?)

/** Scelta dello studente, confermata con il blocco schermo; [decidedAt] in ISO 8601. */
data class FamilyAccessChoice(val granted: Boolean, val decidedAt: String)

/**
 * App consentita (ADR-0019); [untilMillis] è la scadenza di uno sblocco temporaneo,
 * [required] indica un'app obbligatoria decisa dall'istituto.
 */
data class AllowedApp(
    val id: String,
    val untilMillis: Long? = null,
    val required: Boolean = false,
    /** Nome da mostrare allo studente; se manca si usa [id]. */
    val name: String? = null,
    /** Logo pubblico dell'app (ADR-0020). */
    val icon: String? = null,
    /** APK da installare in silenzio (ADR-0020). */
    val install: ApkInstall? = null,
)

/** APK sul server della scuola: si scarica con la credenziale del dispositivo e si verifica. */
data class ApkInstall(val url: String, val sha256: String, val size: Long?)

/**
 * App della policy: con `allowlist` si usano solo le app in [allowed], con `blocklist` si
 * bloccano solo quelle in [blocked]. Le app in [blocked] (vietate dalla piattaforma) sono
 * sempre bloccate.
 */
data class AppsPolicy(
    val mode: String,
    val allowed: List<AllowedApp>,
    val blocked: Set<String> = emptySet(),
    /** Stato delle richieste di app dello studente (ADR-0021): identificativo → stato. */
    val requests: Map<String, String> = emptyMap(),
)

data class Policy(
    val policyVersion: Int,
    val pollIntervalSeconds: Int,
    /** Impostazioni ricevute; quelle sconosciute restano qui e vengono segnalate come non supportate. */
    val settings: Map<String, Any>,
    val familyAccess: FamilyAccess? = null,
    val apps: AppsPolicy? = null,
    /** Messaggio originale, conservato per riapplicare la policy senza rete. */
    val source: String = "",
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
                familyAccess = json.optJSONObject("family_access")?.let {
                    FamilyAccess(if (it.isNull("granted")) null else it.getBoolean("granted"))
                },
                apps = json.optJSONObject("apps")?.let(::parseApps),
                source = json.toString(),
            )
        }

        private fun parseApps(json: JSONObject): AppsPolicy {
            val list = json.optJSONArray("allowed") ?: JSONArray()
            val allowed = (0 until list.length()).map { i ->
                val item = list.getJSONObject(i)
                val until = item.optString("until").takeIf { it.isNotEmpty() }?.let {
                    try {
                        OffsetDateTime.parse(it).toInstant().toEpochMilli()
                    } catch (e: DateTimeParseException) {
                        throw ProtocolException("until non valido: $it")
                    }
                }
                AllowedApp(
                    item.getString("id"),
                    until,
                    item.optBoolean("required", false),
                    item.optString("name").takeIf { it.isNotBlank() }?.take(100),
                    item.optString("icon").takeIf { it.isNotBlank() },
                    item.optJSONObject("install")?.let {
                        ApkInstall(
                            it.getString("url"),
                            it.getString("sha256").lowercase(),
                            if (it.has("size")) it.getLong("size") else null,
                        )
                    },
                )
            }
            val blockedJson = json.optJSONArray("blocked") ?: JSONArray()
            val blocked = (0 until blockedJson.length()).map { blockedJson.getString(it) }.toSet()
            val requestsJson = json.optJSONArray("requests") ?: JSONArray()
            val requests = (0 until requestsJson.length()).associate {
                val r = requestsJson.getJSONObject(it)
                r.getString("id") to r.getString("status")
            }
            return AppsPolicy(json.optString("mode"), allowed, blocked, requests)
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
    val familyAccessChoice: FamilyAccessChoice? = null,
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
        .apply {
            familyAccessChoice?.let {
                put(
                    "family_access_choice",
                    JSONObject().put("granted", it.granted).put("decided_at", it.decidedAt.take(40)),
                )
            }
        }
}

/** Richiesta di usare un'app sospesa, fatta dallo studente (ADR-0021). */
data class AppRequestMessage(val id: String, val name: String?, val note: String?) {
    fun toJson(): JSONObject = JSONObject()
        .put("protocol_version", PROTOCOL_VERSION)
        .put(
            "app",
            JSONObject().put("id", id).apply { name?.takeIf { it.isNotBlank() }?.let { put("name", it.take(100)) } },
        )
        .apply { note?.trim()?.takeIf { it.isNotEmpty() }?.let { put("note", it.take(300)) } }
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

internal fun checkVersion(json: JSONObject) {
    val version = json.optString("protocol_version")
    if (version != PROTOCOL_VERSION) throw ProtocolException("protocol_version non supportata: $version")
}
