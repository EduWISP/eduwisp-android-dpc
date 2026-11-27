// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc.policy

import android.content.Context
import org.eduwisp.dpc.protocol.Policy
import org.eduwisp.dpc.protocol.ProtocolException
import org.eduwisp.dpc.store.PrefsAgentStore
import org.eduwisp.dpc.sync.Sync
import org.eduwisp.dpc.sync.SyncWorker
import org.json.JSONException
import org.json.JSONObject

/**
 * Riapplica l'ultima policy ricevuta senza contattare il server (ADR-0019), alla scadenza
 * di uno sblocco. Funziona anche offline.
 */
object LocalPolicy {
    fun reapply(context: Context) {
        val store = PrefsAgentStore(context)
        val policy = store.lastPolicyJson?.let {
            try {
                Policy.parse(JSONObject(it))
            } catch (e: JSONException) {
                null
            } catch (e: ProtocolException) {
                null
            }
        } ?: return
        val now = System.currentTimeMillis()
        synchronized(SyncWorker.LOCK) {
            PolicyApplier(AndroidDeviceControls(context)).apply(policy, now)
        }
        Sync.scheduleAppExpiry(context, PolicyApplier.nextExpiry(policy.apps, now), now)
    }
}
