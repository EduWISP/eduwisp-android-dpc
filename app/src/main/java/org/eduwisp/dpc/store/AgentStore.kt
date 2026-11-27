// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc.store

import org.eduwisp.dpc.protocol.FamilyAccessChoice

/**
 * Stato dell'agente. Solo dati tecnici: nessun dato personale dello studente
 * (ADR-0008). La credenziale del dispositivo è conservata cifrata.
 */
interface AgentStore {
    var serverUrl: String?
    var enrollmentToken: String?
    var deviceId: String?
    var deviceToken: String?
    var pollIntervalSeconds: Int
    var policyVersionApplied: Int
    var nonCompliance: List<String>
    var lastSyncMillis: Long
    var lastError: String?
    var retired: Boolean

    /** La scuola chiede allo studente maggiorenne se lasciare l'accesso ai familiari (ADR-0018). */
    var familyAccessRequested: Boolean
    /** Scelta registrata dal server; null = non ancora scelto. */
    var familyAccessGranted: Boolean?
    /** Scelta confermata sul dispositivo ma non ancora ricevuta dal server. */
    var pendingFamilyChoice: FamilyAccessChoice?

    /** Ultima policy ricevuta, per riapplicarla senza rete (ADR-0019). */
    var lastPolicyJson: String?
    /** Prossima scadenza di uno sblocco temporaneo di un'app. */
    var nextAppExpiryMillis: Long?
    /** App obbligatorie non ancora installate (identificativi). */
    var missingRequiredApps: List<String>
}

class MemoryAgentStore : AgentStore {
    override var serverUrl: String? = null
    override var enrollmentToken: String? = null
    override var deviceId: String? = null
    override var deviceToken: String? = null
    override var pollIntervalSeconds: Int = DEFAULT_POLL_SECONDS
    override var policyVersionApplied: Int = 0
    override var nonCompliance: List<String> = emptyList()
    override var lastSyncMillis: Long = 0
    override var lastError: String? = null
    override var retired: Boolean = false
    override var familyAccessRequested: Boolean = false
    override var familyAccessGranted: Boolean? = null
    override var pendingFamilyChoice: FamilyAccessChoice? = null
    override var lastPolicyJson: String? = null
    override var nextAppExpiryMillis: Long? = null
    override var missingRequiredApps: List<String> = emptyList()
}

const val DEFAULT_POLL_SECONDS = 900
