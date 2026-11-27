// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc.policy

import org.eduwisp.dpc.protocol.NonCompliance
import org.eduwisp.dpc.protocol.NonComplianceReason
import org.eduwisp.dpc.protocol.Policy

/** Operazioni sul dispositivo: in produzione DevicePolicyManager, nei test un finto. */
interface DeviceControls {
    val isDeviceOwner: Boolean
    fun setCameraDisabled(disabled: Boolean)
    fun setScreenCaptureDisabled(disabled: Boolean)
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
    )

    /** Capacità dichiarate alla registrazione, nel formato `setting:<nome>`. */
    val capabilities: List<String> = handlers.keys.sorted().map { "setting:$it" }

    fun apply(policy: Policy): List<NonCompliance> {
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
}
