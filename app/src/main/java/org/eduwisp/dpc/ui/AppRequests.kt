// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc.ui

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONException
import org.json.JSONObject

/** Stato locale delle richieste di app fatte dallo studente (ADR-0021). */
class AppRequests(context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences("richieste_app", Context.MODE_PRIVATE)

    fun status(id: String): String? = prefs.getString(id, null)

    fun setStatus(id: String, status: String) = prefs.edit().putString(id, status).apply()

    companion object {
        const val SENDING = "sending"
        const val PENDING = "pending"
        const val APPROVED = "approved"
        const val REJECTED = "rejected"
        const val NOT_PERSONAL = "not_personal"
        const val ERROR = "error"
    }
}

/** Stato da mostrare: «in invio» finché il tablet non l'ha spedita, poi quello del server. */
fun requestStatus(local: String?, server: String?): String? =
    if (local == AppRequests.SENDING) local else server ?: local

/** Una riga dell'elenco «App bloccate»: [canAsk] se lo studente può chiederla. */
data class BlockedAppRow(val id: String, val label: String, val forbidden: Boolean, val status: String?) {
    val canAsk: Boolean
        get() = !forbidden && status !in setOf(AppRequests.SENDING, AppRequests.PENDING, AppRequests.NOT_PERSONAL)
}

/** Righe da mostrare: app sospese, con le vietate dalla piattaforma senza pulsante. */
fun blockedAppRows(suspended: Map<String, String>, forbidden: Set<String>, status: (String) -> String?) =
    suspended.entries
        .sortedBy { it.value.lowercase() }
        .map { BlockedAppRow(it.key, it.value, it.key in forbidden, status(it.key)) }

/** App vietate dalla piattaforma, lette dall'ultima policy ricevuta. */
fun forbiddenFromPolicy(policyJson: String?): Set<String> = try {
    val blocked = policyJson?.let { JSONObject(it).optJSONObject("apps")?.optJSONArray("blocked") }
    if (blocked == null) emptySet() else (0 until blocked.length()).map { blocked.getString(it) }.toSet()
} catch (e: JSONException) {
    emptySet()
}
