// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc.policy

import org.eduwisp.dpc.protocol.AllowedApp
import org.eduwisp.dpc.protocol.AppsPolicy
import org.eduwisp.dpc.protocol.NonCompliance
import org.eduwisp.dpc.protocol.NonComplianceReason
import org.eduwisp.dpc.protocol.Policy

/** Operazioni sul dispositivo: in produzione DevicePolicyManager, nei test un finto. */
interface DeviceControls {
    val isDeviceOwner: Boolean
    fun setCameraDisabled(disabled: Boolean)
    fun setScreenCaptureDisabled(disabled: Boolean)

    /** Pacchetti con un'icona nella schermata delle app. */
    fun launchablePackages(): Set<String>

    /** Mai da sospendere: l'agente, la schermata Home, le tastiere attive (ADR-0019). */
    fun protectedPackages(): Set<String>

    /** Sospende o riattiva i pacchetti; restituisce quelli per cui non è riuscito. */
    fun setPackagesSuspended(packages: Set<String>, suspended: Boolean): Set<String>

    fun setUnknownSourcesBlocked(blocked: Boolean)
    fun setVpnConfigDisabled(disabled: Boolean)
    fun setPrivateDnsConfigDisabled(disabled: Boolean)
}

/**
 * Applica le impostazioni indipendenti dalla piattaforma (ADR-0011).
 * Un'impostazione assente dalla policy torna al valore predefinito di Android:
 * la scuola gestisce solo ciò che dichiara.
 */
class PolicyApplier(private val controls: DeviceControls) {

    private class Handler(val default: Boolean, val apply: (Boolean) -> Unit)

    private val handlers: Map<String, Handler> = mapOf(
        "camera_disabled" to Handler(false) { controls.setCameraDisabled(it) },
        "screen_capture_disabled" to Handler(false) { controls.setScreenCaptureDisabled(it) },
        "vpn_config_disabled" to Handler(false) { controls.setVpnConfigDisabled(it) },
        "private_dns_config_disabled" to Handler(false) { controls.setPrivateDnsConfigDisabled(it) },
    )

    /** Capacità dichiarate alla registrazione: `setting:<nome>` e `apps:allowlist`. */
    val capabilities: List<String> = handlers.keys.sorted().map { "setting:$it" } + APPS_CAPABILITY

    fun apply(policy: Policy, nowMillis: Long = System.currentTimeMillis()): List<NonCompliance> {
        val issues = applySettings(policy)
        applyApps(policy.apps, nowMillis)?.let { issues += it }
        if (controls.isDeviceOwner && missingRequired(policy.apps).isNotEmpty()) {
            issues += NonCompliance(APPS_REQUIRED, NonComplianceReason.PENDING)
        }
        return issues
    }

    /** App obbligatorie non ancora installate, da segnalare allo studente. */
    fun missingRequired(apps: AppsPolicy?): List<AllowedApp> {
        val required = apps?.allowed?.filter { it.required } ?: return emptyList()
        if (required.isEmpty()) return emptyList()
        val installed = controls.launchablePackages()
        return required.filterNot { it.id in installed }
    }

    /**
     * App consentite (ADR-0019): le app con icona non consentite vengono sospese, quelle
     * consentite riattivate. Senza elenco tutte tornano attive. Le app protette non si toccano.
     */
    private fun applyApps(apps: AppsPolicy?, nowMillis: Long): NonCompliance? {
        if (apps != null && apps.mode !in setOf(ALLOWLIST, BLOCKLIST)) {
            return NonCompliance(APPS, NonComplianceReason.UNSUPPORTED)
        }
        if (!controls.isDeviceOwner) {
            return if (apps != null) NonCompliance(APPS, NonComplianceReason.PENDING) else null
        }
        return try {
            val launchable = controls.launchablePackages()
            val suspend = when {
                apps == null -> emptySet()
                apps.mode == ALLOWLIST ->
                    (launchable - activeApps(apps, nowMillis) + (launchable intersect apps.blocked)) -
                        controls.protectedPackages()
                else -> (launchable intersect apps.blocked) - controls.protectedPackages()
            }
            val failed = controls.setPackagesSuspended(suspend, true) +
                controls.setPackagesSuspended(launchable - suspend, false)
            controls.setUnknownSourcesBlocked(apps != null)
            if (failed.isEmpty()) null else NonCompliance(APPS, NonComplianceReason.FAILED)
        } catch (e: SecurityException) {
            NonCompliance(APPS, NonComplianceReason.FAILED)
        }
    }

    private fun applySettings(policy: Policy): MutableList<NonCompliance> {
        val issues = mutableListOf<NonCompliance>()
        policy.settings.keys.filterNot(handlers::containsKey).sorted().forEach {
            issues += NonCompliance(it, NonComplianceReason.UNSUPPORTED)
        }
        for ((name, handler) in handlers.toSortedMap()) {
            val raw = policy.settings[name]
            val value = when (raw) {
                null -> handler.default
                is Boolean -> raw
                else -> {
                    issues += NonCompliance(name, NonComplianceReason.FAILED)
                    continue
                }
            }
            if (!controls.isDeviceOwner) {
                if (raw != null) issues += NonCompliance(name, NonComplianceReason.PENDING)
                continue
            }
            try {
                handler.apply(value)
            } catch (e: SecurityException) {
                issues += NonCompliance(name, NonComplianceReason.FAILED)
            } catch (e: IllegalStateException) {
                issues += NonCompliance(name, NonComplianceReason.FAILED)
            }
        }
        return issues
    }

    companion object {
        const val APPS = "apps"
        const val ALLOWLIST = "allowlist"
        const val BLOCKLIST = "blocklist"
        const val APPS_REQUIRED = "apps_required"
        const val APPS_CAPABILITY = "apps:allowlist"

        fun activeApps(apps: AppsPolicy, nowMillis: Long): Set<String> =
            apps.allowed.filter { it.untilMillis == null || it.untilMillis > nowMillis }.map { it.id }.toSet() -
                apps.blocked

        /** Prossima scadenza di uno sblocco, per richiudere l'app anche senza rete. */
        fun nextExpiry(apps: AppsPolicy?, nowMillis: Long): Long? =
            apps?.allowed?.mapNotNull { it.untilMillis }?.filter { it > nowMillis }?.minOrNull()
    }
}
