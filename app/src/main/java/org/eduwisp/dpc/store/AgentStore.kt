// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc.store

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
}

const val DEFAULT_POLL_SECONDS = 900
