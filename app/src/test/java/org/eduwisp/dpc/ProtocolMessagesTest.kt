// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc

import org.eduwisp.dpc.protocol.AgentInfo
import org.eduwisp.dpc.protocol.EnrollmentRequest
import org.eduwisp.dpc.protocol.EnrollmentResponse
import org.eduwisp.dpc.protocol.NonCompliance
import org.eduwisp.dpc.protocol.NonComplianceReason
import org.eduwisp.dpc.protocol.Policy
import org.eduwisp.dpc.protocol.ProtocolError
import org.eduwisp.dpc.protocol.ProtocolException
import org.eduwisp.dpc.protocol.StatusReport
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/** Gli esempi sono copiati da EduWISP/eduwisp-protocol, examples/v0. */
class ProtocolMessagesTest {
    private fun example(name: String) =
        JSONObject(javaClass.getResource("/protocol/v0/$name.json")!!.readText())

    @Test
    fun `legge la risposta di registrazione di esempio`() {
        val response = EnrollmentResponse.parse(example("enrollment-response"))
        assertEquals("3f1e9a2c-5b7d-4c8e-9f01-23456789abcd", response.deviceId)
        assertEquals(900, response.pollIntervalSeconds)
    }

    @Test
    fun `rifiuta una credenziale che non corrisponde al dispositivo`() {
        val json = example("enrollment-response").put("device_token", "altro.segreto")
        assertThrows(ProtocolException::class.java) { EnrollmentResponse.parse(json) }
    }

    @Test
    fun `rifiuta una versione del protocollo diversa`() {
        val json = example("policy").put("protocol_version", "1")
        assertThrows(ProtocolException::class.java) { Policy.parse(json) }
    }

    @Test
    fun `legge la policy di esempio`() {
        val policy = Policy.parse(example("policy"))
        assertEquals(3, policy.policyVersion)
        assertEquals(mapOf("camera_disabled" to true, "screen_capture_disabled" to false), policy.settings)
    }

    @Test
    fun `la richiesta di registrazione ha gli stessi campi dell esempio`() {
        val expected = example("enrollment-request")
        val request = EnrollmentRequest(
            enrollmentToken = expected.getString("enrollment_token"),
            agent = AgentInfo("0.1.0", "15", "Tablet di prova"),
            capabilities = listOf("setting:camera_disabled", "setting:screen_capture_disabled"),
        ).toJson()
        assertEquals(expected.asMap().keys, request.asMap().keys)
        assertEquals(expected.getJSONObject("agent").asMap(), request.getJSONObject("agent").asMap())
        assertEquals(expected.getJSONArray("capabilities").asList(), request.getJSONArray("capabilities").asList())
    }

    @Test
    fun `il rapporto di stato corrisponde all esempio`() {
        val expected = example("status-report")
        val report = StatusReport(
            policyVersionApplied = 3,
            nonCompliance = listOf(NonCompliance("screen_capture_disabled", NonComplianceReason.UNSUPPORTED)),
            agentVersion = "0.1.0",
            osVersion = "15",
        ).toJson()
        assertEquals(expected.asMap(), report.asMap())
        assertFalse(report.getBoolean("compliant"))
    }

    @Test
    fun `legge l errore di esempio`() {
        assertEquals("token_expired", ProtocolError.parse(example("error")).code)
    }
}
