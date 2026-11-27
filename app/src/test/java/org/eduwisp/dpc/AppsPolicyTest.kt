// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc

import org.eduwisp.dpc.policy.PolicyApplier
import org.eduwisp.dpc.protocol.AllowedApp
import org.eduwisp.dpc.protocol.AppsPolicy
import org.eduwisp.dpc.protocol.NonCompliance
import org.eduwisp.dpc.protocol.NonComplianceReason
import org.eduwisp.dpc.protocol.Policy
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

/** App consentite (ADR-0019). */
class AppsPolicyTest {
    private val controls = FakeControls()
    private val applier = PolicyApplier(controls)
    private val now = 1_000_000L

    private fun policy(apps: AppsPolicy?) = Policy(1, 900, emptyMap(), apps = apps)

    @Test
    fun `legge l elenco di esempio con la scadenza dello sblocco`() {
        val json = JSONObject(javaClass.getResource("/protocol/v0/policy-apps.json")!!.readText())
        val apps = Policy.parse(json).apps!!
        assertEquals("allowlist", apps.mode)
        assertEquals(5, apps.allowed.size)
        assertTrue(apps.allowed.first { it.id == "com.microsoft.teams" }.required)
        assertEquals("Microsoft Teams", apps.allowed.first { it.id == "com.microsoft.teams" }.name)
        assertTrue("com.zhiliaoapp.musically" in apps.blocked)
        val wiki = apps.allowed.first { it.id == "org.wikipedia" }
        assertEquals(OffsetDateTime.parse("2027-03-02T11:00:00+01:00").toInstant().toEpochMilli(), wiki.untilMillis)
        assertNull(apps.allowed.first { it.id == "org.mozilla.firefox" }.untilMillis)
    }

    @Test
    fun `sospende le app non consentite ma mai quelle protette`() {
        val issues = applier.apply(policy(AppsPolicy("allowlist", listOf(AllowedApp("org.mozilla.firefox")))), now)
        assertTrue(issues.isEmpty())
        assertEquals(setOf("com.example.gioco"), controls.suspended)
        assertEquals(true, controls.unknownSourcesBlocked)
    }

    @Test
    fun `lo sblocco scaduto richiude l app`() {
        val apps = AppsPolicy("allowlist", listOf(AllowedApp("com.example.gioco", untilMillis = now + 60_000)))
        applier.apply(policy(apps), now)
        assertTrue("com.example.gioco" !in controls.suspended)
        assertEquals(now + 60_000, PolicyApplier.nextExpiry(apps, now))
        applier.apply(policy(apps), now + 60_001)
        assertTrue("com.example.gioco" in controls.suspended)
        assertNull(PolicyApplier.nextExpiry(apps, now + 60_001))
    }

    @Test
    fun `senza elenco tutte le app tornano attive`() {
        applier.apply(policy(AppsPolicy("allowlist", emptyList())), now)
        assertTrue(controls.suspended.isNotEmpty())
        applier.apply(policy(null), now)
        assertTrue(controls.suspended.isEmpty())
        assertEquals(false, controls.unknownSourcesBlocked)
    }

    @Test
    fun `sospensione non riuscita segnalata`() {
        controls.failing = setOf("com.example.gioco")
        val issues = applier.apply(policy(AppsPolicy("allowlist", emptyList())), now)
        assertEquals(listOf(NonCompliance("apps", NonComplianceReason.FAILED)), issues)
    }

    @Test
    fun `modalita sconosciuta non supportata`() {
        val issues = applier.apply(policy(AppsPolicy("orari", emptyList())), now)
        assertEquals(listOf(NonCompliance("apps", NonComplianceReason.UNSUPPORTED)), issues)
        assertTrue(controls.suspended.isEmpty())
    }

    @Test
    fun `senza device owner l elenco resta in sospeso`() {
        controls.isDeviceOwner = false
        val issues = applier.apply(policy(AppsPolicy("allowlist", emptyList())), now)
        assertEquals(listOf(NonCompliance("apps", NonComplianceReason.PENDING)), issues)
        assertTrue(controls.suspended.isEmpty())
    }

    @Test
    fun `le app vietate restano bloccate anche se consentite per errore`() {
        val apps = AppsPolicy(
            "allowlist",
            listOf(AllowedApp("com.example.gioco"), AllowedApp("org.mozilla.firefox")),
            blocked = setOf("com.example.gioco"),
        )
        applier.apply(policy(apps), now)
        assertTrue("com.example.gioco" in controls.suspended)
        assertTrue("org.mozilla.firefox" !in controls.suspended)
    }

    @Test
    fun `senza elenco di consentite si bloccano solo le vietate`() {
        applier.apply(policy(AppsPolicy("blocklist", emptyList(), blocked = setOf("com.example.gioco"))), now)
        assertEquals(setOf("com.example.gioco"), controls.suspended)
        assertEquals(true, controls.unknownSourcesBlocked)
    }

    @Test
    fun `le app obbligatorie mancanti sono segnalate`() {
        val apps = AppsPolicy(
            "allowlist",
            listOf(AllowedApp("org.mozilla.firefox", required = true), AllowedApp("com.microsoft.teams", required = true)),
        )
        assertEquals(listOf("com.microsoft.teams"), applier.missingRequired(apps).map { it.id })
        val issues = applier.apply(policy(apps), now)
        assertEquals(listOf(NonCompliance("apps_required", NonComplianceReason.PENDING)), issues)
    }

    @Test
    fun `vpn e dns privato applicati come impostazioni`() {
        applier.apply(Policy(1, 900, mapOf("vpn_config_disabled" to true, "private_dns_config_disabled" to true)), now)
        assertEquals(true, controls.vpn)
        assertEquals(true, controls.privateDns)
    }

    @Test
    fun `un dato salvato in un formato vecchio non fa chiudere l app`() {
        assertEquals(emptyList<AllowedApp>(), org.eduwisp.dpc.store.parseMissingRequired("com.microsoft.teams,it.x"))
        val apps = org.eduwisp.dpc.store.parseMissingRequired("""[{"id":"com.microsoft.teams","name":"Microsoft Teams"}]""")
        assertEquals("Microsoft Teams", apps.single().name)
    }
}
