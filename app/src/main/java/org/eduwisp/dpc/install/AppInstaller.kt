// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc.install

import org.eduwisp.dpc.protocol.AllowedApp
import java.net.URL
import java.security.MessageDigest

/** Esito di un tentativo di installazione di un'app obbligatoria (ADR-0020). */
sealed interface InstallOutcome {
    /** Installazione avviata: Android la completa in silenzio. */
    data object Started : InstallOutcome

    /** Già avviata da poco: si aspetta il ciclo successivo. */
    data object Pending : InstallOutcome

    data class Failed(val reason: String) : InstallOutcome
}

/** Installazione silenziosa delle app obbligatorie: in produzione PackageInstaller, nei test un finto. */
interface AppInstaller {
    fun install(app: AllowedApp, deviceToken: String): InstallOutcome

    /** Scarica e conserva il logo dell'app (facoltativo). */
    fun fetchIcon(app: AllowedApp) {}
}

/** L'APK si scarica solo dallo stesso server della scuola, mai da indirizzi di terzi. */
fun sameServer(serverUrl: String, url: String): Boolean = try {
    val a = URL(serverUrl)
    val b = URL(url)
    a.protocol == b.protocol && a.host.equals(b.host, ignoreCase = true) && a.port == b.port
} catch (e: java.net.MalformedURLException) {
    false
}

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
