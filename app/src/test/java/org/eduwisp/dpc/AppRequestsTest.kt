// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc

import org.eduwisp.dpc.protocol.AppRequestMessage
import org.eduwisp.dpc.protocol.Policy
import org.eduwisp.dpc.protocol.ProtocolClient
import org.eduwisp.dpc.protocol.Result
import org.eduwisp.dpc.ui.AppRequests
import org.eduwisp.dpc.ui.blockedAppRows
import org.eduwisp.dpc.ui.forbiddenFromPolicy
import org.eduwisp.dpc.ui.requestStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Richieste di app (ADR-0021). */
class AppRequestsTest {
    private fun example(name: String) =
        JSONObject(javaClass.getResource("/protocol/v0/$name.json")!!.readText())

    @Test
    fun `la richiesta corrisponde all esempio del protocollo`() {
        val expected = example("app-request")
        val message = AppRequestMessage(
            "org.example.dizionario",
            "Dizionario di latino",
            "Mi serve per le versioni di latino a casa.",
        ).toJson()
        assertEquals(expected.asMap(), message.asMap())
        assertFalse(AppRequestMessage("x.y", null, "  ").toJson().has("note"))
    }

    @Test
    fun `il client invia la richiesta e legge lo stato`() {
        val server = FakeServer()
        val client = ProtocolClient("https://demo.eduwisp.org") { method, url, bearer, body ->
            server.requests += Triple(url.substringAfterLast('/'), bearer, body?.let(::JSONObject))
            201 to """{"protocol_version":"0","status":"pending"}"""
        }
        val r = client.appRequest("tok", AppRequestMessage("org.example.dizionario", "Dizionario", null))
        assertEquals(Result.Ok("pending"), r)
        assertEquals("app-requests", server.requests.single().first)
    }

    @Test
    fun `lo stato delle richieste arriva con la policy`() {
        val apps = Policy.parse(example("policy-apps")).apps!!
        assertEquals(mapOf("org.example.dizionario" to "pending"), apps.requests)
    }

    @Test
    fun `elenco delle app bloccate`() {
        val rows = blockedAppRows(
            mapOf("org.example.gioco" to "Gioco", "com.snapchat.android" to "Snapchat", "a.b" to "Atlante"),
            forbiddenFromPolicy(example("policy-apps").toString()),
        ) { if (it == "a.b") AppRequests.PENDING else null }
        assertEquals(listOf("Atlante", "Gioco", "Snapchat"), rows.map { it.label })
        assertFalse(rows[0].canAsk) // già in attesa
        assertTrue(rows[1].canAsk)
        assertTrue(rows[2].forbidden)
        assertFalse(rows[2].canAsk) // vietata: niente pulsante
    }

    @Test
    fun `lo stato del server prevale tranne durante l invio`() {
        assertEquals("rejected", requestStatus(AppRequests.PENDING, "rejected"))
        assertEquals(AppRequests.SENDING, requestStatus(AppRequests.SENDING, "rejected"))
        assertEquals(AppRequests.PENDING, requestStatus(AppRequests.PENDING, null))
    }
}
