// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc

import org.eduwisp.dpc.install.AppInstaller
import org.eduwisp.dpc.install.InstallOutcome
import org.eduwisp.dpc.install.sameServer
import org.eduwisp.dpc.policy.PolicyApplier
import org.eduwisp.dpc.protocol.AgentInfo
import org.eduwisp.dpc.protocol.AllowedApp
import org.eduwisp.dpc.protocol.ProtocolClient
import org.eduwisp.dpc.store.MemoryAgentStore
import org.eduwisp.dpc.sync.Agent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeInstaller(var outcome: InstallOutcome = InstallOutcome.Started) : AppInstaller {
    val installed = mutableListOf<String>()
    val icons = mutableListOf<String>()
    override fun install(app: AllowedApp, deviceToken: String): InstallOutcome {
        installed += app.id
        return outcome
    }
    override fun fetchIcon(app: AllowedApp) {
        if (app.icon != null) icons += app.id
    }
}

/** App obbligatorie installate in silenzio (ADR-0020). */
class InstallTest {
    private val store = MemoryAgentStore().apply {
        serverUrl = "https://demo.eduwisp.org"
        enrollmentToken = "token-di-registrazione-fittizio-0123"
    }
    private val server = FakeServer().apply {
        policy = 200 to """{"protocol_version":"0","policy_version":7,"poll_interval_seconds":900,"settings":{},
            "apps":{"mode":"allowlist","allowed":[
              {"id":"org.example.atlante","required":true,"name":"Atlante","icon":"https://demo.eduwisp.org/media/a.png",
               "install":{"url":"https://demo.eduwisp.org/api/protocol/v0/apps/12/apk",
                          "sha256":"9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08","size":5}},
              {"id":"com.microsoft.teams","required":true,"name":"Microsoft Teams"},
              {"id":"org.mozilla.firefox"}]}}"""
    }
    private val installer = FakeInstaller()
    private fun agent() = Agent(
        store,
        PolicyApplier(FakeControls()),
        AgentInfo("0.4.0", "15", "Emulatore"),
        clientFor = { ProtocolClient(it, server) },
        now = { 42L },
        installer = installer,
    )

    @Test
    fun `alla registrazione installa subito le obbligatorie con APK`() {
        agent().sync()
        assertEquals(listOf("enroll", "policy", "status"), server.requests.map { it.first })
        assertEquals(listOf("org.example.atlante"), installer.installed)
        assertEquals(listOf("org.example.atlante"), installer.icons)
        // Teams senza APK resta da installare con il pulsante
        assertEquals(
            listOf("org.example.atlante", "com.microsoft.teams"),
            store.missingRequiredApps.map { it.id },
        )
        val issues = server.requests.last().third!!.getJSONArray("non_compliance").asList()
        assertEquals(listOf(mapOf("setting" to "apps_required", "reason" to "pending")), issues)
    }

    @Test
    fun `installazione non riuscita segnalata alla scuola`() {
        installer.outcome = InstallOutcome.Failed("impronta dell'APK diversa")
        agent().sync()
        val issues = server.requests.last().third!!.getJSONArray("non_compliance").asList()
        assertTrue(mapOf("setting" to "apps_required", "reason" to "failed") in issues)
        assertTrue(store.lastError!!.contains("impronta"))
    }

    @Test
    fun `l APK si scarica solo dal server della scuola`() {
        assertTrue(sameServer("https://demo.eduwisp.org", "https://demo.eduwisp.org/api/protocol/v0/apps/1/apk"))
        assertFalse(sameServer("https://demo.eduwisp.org", "https://altro.example/app.apk"))
        assertFalse(sameServer("https://demo.eduwisp.org", "http://demo.eduwisp.org/app.apk"))
        assertFalse(sameServer("https://demo.eduwisp.org", "non è un indirizzo"))
    }
}
