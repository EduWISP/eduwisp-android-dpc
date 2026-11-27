// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc

import org.eduwisp.dpc.policy.DeviceControls
import org.eduwisp.dpc.policy.PolicyApplier
import org.eduwisp.dpc.protocol.AgentInfo
import org.eduwisp.dpc.protocol.ProtocolClient
import org.eduwisp.dpc.protocol.Transport
import org.eduwisp.dpc.store.MemoryAgentStore
import org.eduwisp.dpc.sync.Agent
import org.eduwisp.dpc.sync.Outcome
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private const val DEVICE = "3f1e9a2c-5b7d-4c8e-9f01-23456789abcd"
private const val TOKEN = "$DEVICE.credenziale-fittizia"

class FakeControls(override var isDeviceOwner: Boolean = true) : DeviceControls {
    var camera: Boolean? = null
    var capture: Boolean? = null
    override fun setCameraDisabled(disabled: Boolean) { camera = disabled }
    override fun setScreenCaptureDisabled(disabled: Boolean) { capture = disabled }
}

/** Server finto: risponde secondo la rotta e registra le richieste ricevute. */
class FakeServer : Transport {
    val requests = mutableListOf<Triple<String, String?, JSONObject?>>()
    var offline = false
    var enroll: Pair<Int, String> = 201 to
        """{"protocol_version":"0","device_id":"$DEVICE","device_token":"$TOKEN","poll_interval_seconds":600}"""
    var policy: Pair<Int, String> = 200 to
        """{"protocol_version":"0","policy_version":2,"poll_interval_seconds":1200,"settings":{"camera_disabled":true}}"""
    var status: Pair<Int, String> = 204 to ""

    override fun exchange(method: String, url: String, bearer: String?, body: String?): Pair<Int, String> {
        if (offline) throw IOException("rete assente")
        val route = url.substringAfterLast('/')
        requests += Triple(route, bearer, body?.let(::JSONObject))
        return when (route) {
            "enroll" -> enroll
            "policy" -> policy
            "status" -> status
            else -> 404 to """{"error":{"code":"not_found","message":""}}"""
        }
    }
}

class AgentTest {
    private val store = MemoryAgentStore().apply {
        serverUrl = "https://demo.eduwisp.org"
        enrollmentToken = "token-di-registrazione-fittizio-0123"
    }
    private val controls = FakeControls()
    private val server = FakeServer()
    private val agent = Agent(
        store,
        PolicyApplier(controls),
        AgentInfo("0.1.0", "15", "Emulatore"),
        clientFor = { ProtocolClient(it, server) },
        now = { 42L },
    )

    @Test
    fun `registra il dispositivo applica la policy e invia lo stato`() {
        assertEquals(Outcome.DONE, agent.sync())
        assertEquals(listOf("enroll", "policy", "status"), server.requests.map { it.first })
        assertEquals(TOKEN, store.deviceToken)
        assertNull("il token di registrazione va scartato", store.enrollmentToken)
        assertEquals(true, controls.camera)
        assertEquals(false, controls.capture)
        assertEquals(2, store.policyVersionApplied)
        assertEquals(1200, store.pollIntervalSeconds)
        assertEquals(42L, store.lastSyncMillis)
        val (_, bearer, report) = server.requests.last()
        assertEquals(TOKEN, bearer)
        assertTrue(report!!.getBoolean("compliant"))
    }

    @Test
    fun `la registrazione non invia la credenziale e dichiara le capacità`() {
        agent.sync()
        val (_, bearer, body) = server.requests.first()
        assertNull(bearer)
        assertEquals(
            listOf("setting:camera_disabled", "setting:screen_capture_disabled"),
            body!!.getJSONArray("capabilities").asList(),
        )
    }

    @Test
    fun `una seconda sincronizzazione non si registra di nuovo`() {
        agent.sync()
        server.requests.clear()
        agent.sync()
        assertEquals(listOf("policy", "status"), server.requests.map { it.first })
    }

    @Test
    fun `offline riprova più tardi e conserva il token`() {
        server.offline = true
        assertEquals(Outcome.RETRY, agent.sync())
        assertEquals("token-di-registrazione-fittizio-0123", store.enrollmentToken)
        assertTrue(store.lastError!!.contains("rete assente"))
    }

    @Test
    fun `token scaduto si ferma e chiede un nuovo QR code`() {
        server.enroll = 403 to """{"error":{"code":"token_expired","message":"Scaduto."}}"""
        assertEquals(Outcome.STOP, agent.sync())
        assertNull(store.enrollmentToken)
        assertNull(store.deviceToken)
        assertEquals("token_expired", store.lastError)
    }

    @Test
    fun `dispositivo dismesso smette di sincronizzare`() {
        agent.sync()
        server.policy = 403 to """{"error":{"code":"device_retired","message":"Dismesso."}}"""
        assertEquals(Outcome.STOP, agent.sync())
        assertTrue(store.retired)
        server.requests.clear()
        assertEquals(Outcome.STOP, agent.sync())
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun `impostazioni sconosciute o non applicabili sono segnalate`() {
        server.policy = 200 to """{"protocol_version":"0","policy_version":5,"poll_interval_seconds":900,
            "settings":{"camera_disabled":"sì","wifi_disabled":true}}"""
        agent.sync()
        val report = server.requests.last().third!!
        assertFalse(report.getBoolean("compliant"))
        assertEquals(
            listOf(
                mapOf("setting" to "wifi_disabled", "reason" to "unsupported"),
                mapOf("setting" to "camera_disabled", "reason" to "failed"),
            ),
            report.getJSONArray("non_compliance").asList(),
        )
    }

    @Test
    fun `senza device owner le impostazioni restano in sospeso`() {
        controls.isDeviceOwner = false
        agent.sync()
        assertNull(controls.camera)
        assertEquals(listOf("camera_disabled:pending"), store.nonCompliance)
    }

    @Test
    fun `senza server configurato non fa nulla`() {
        store.serverUrl = null
        assertEquals(Outcome.STOP, agent.sync())
        assertTrue(server.requests.isEmpty())
    }
}
