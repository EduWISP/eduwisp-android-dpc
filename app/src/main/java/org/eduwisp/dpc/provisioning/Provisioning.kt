// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc.provisioning

import android.content.Context
import android.os.BaseBundle
import org.eduwisp.dpc.store.PrefsAgentStore
import org.eduwisp.dpc.sync.Sync

/** Dati passati dal QR code (eduwisp_devices.platforms.android) nel bundle degli extra. */
object Provisioning {
    const val EXTRA_SERVER_URL = "org.eduwisp.SERVER_URL"
    const val EXTRA_ENROLLMENT_TOKEN = "org.eduwisp.ENROLLMENT_TOKEN"

    /** Salva server e token e avvia la registrazione. Restituisce false se i dati non sono validi. */
    fun configure(context: Context, serverUrl: String?, enrollmentToken: String?): Boolean {
        if (!isValidServer(serverUrl) || enrollmentToken.isNullOrBlank()) return false
        val store = PrefsAgentStore(context)
        if (store.deviceToken != null) return true // già registrato: un secondo invio non cambia nulla
        store.serverUrl = serverUrl!!.trimEnd('/')
        store.enrollmentToken = enrollmentToken.trim()
        store.lastError = null
        // Il lavoro periodico lo pianifica SyncWorker al termine del primo ciclo.
        Sync.runNow(context)
        return true
    }

    fun configure(context: Context, extras: BaseBundle?): Boolean =
        configure(context, extras?.getString(EXTRA_SERVER_URL), extras?.getString(EXTRA_ENROLLMENT_TOKEN))

    internal fun isValidServer(url: String?): Boolean {
        if (url == null) return false
        val allowed = if (org.eduwisp.dpc.BuildConfig.DEBUG) Regex("^https?://[^/\\s]+/?$") else Regex("^https://[^/\\s]+/?$")
        return allowed.matches(url)
    }
}
