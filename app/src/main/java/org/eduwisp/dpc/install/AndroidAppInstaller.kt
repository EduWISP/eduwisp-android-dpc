// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import org.eduwisp.dpc.protocol.AllowedApp
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest

private const val TAG = "EduWISP"
private const val MAX_APK_BYTES = 300L * 1024 * 1024
private const val RETRY_AFTER_MS = 10 * 60 * 1000L

/**
 * Scarica l'APK dal server della scuola con la credenziale del dispositivo, ne verifica
 * l'impronta SHA-256 e il nome del pacchetto, e lo installa in silenzio come proprietario
 * del dispositivo (ADR-0020).
 */
class AndroidAppInstaller(private val context: Context, private val serverUrl: String) : AppInstaller {
    private val prefs = context.getSharedPreferences("installazioni", Context.MODE_PRIVATE)

    override fun install(app: AllowedApp, deviceToken: String): InstallOutcome {
        val apk = app.install ?: return InstallOutcome.Failed("nessun APK")
        if (!sameServer(serverUrl, apk.url)) return InstallOutcome.Failed("APK fuori dal server della scuola")
        val ultimo = prefs.getLong(app.id, 0)
        if (System.currentTimeMillis() - ultimo < RETRY_AFTER_MS) return InstallOutcome.Pending
        val dir = File(context.cacheDir, "apk").apply { mkdirs() }
        val file = File(dir, "${app.id}.apk")
        try {
            val digest = download(apk.url, deviceToken, file, apk.size)
            if (digest != apk.sha256) return InstallOutcome.Failed("impronta dell'APK diversa")
            val info = context.packageManager.getPackageArchiveInfo(file.path, 0)
            if (info?.packageName != app.id) return InstallOutcome.Failed("APK di un'altra app")
            commit(app.id, file)
            prefs.edit().putLong(app.id, System.currentTimeMillis()).apply()
            return InstallOutcome.Started
        } catch (e: IOException) {
            return InstallOutcome.Failed("download: ${e.message}")
        } catch (e: SecurityException) {
            return InstallOutcome.Failed("installazione non permessa")
        } finally {
            file.delete()
        }
    }

    private fun download(url: String, token: String, file: File, expected: Long?): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Authorization", "Bearer $token")
        try {
            if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            DigestInputStream(connection.inputStream, digest).use { input ->
                file.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_APK_BYTES || (expected != null && total > expected)) {
                            throw IOException("APK più grande del previsto")
                        }
                        out.write(buffer, 0, n)
                    }
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        } finally {
            connection.disconnect()
        }
    }

    private fun commit(packageName: String, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("app.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName)
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
    }

    override fun fetchIcon(app: AllowedApp) {
        val url = app.icon ?: return
        val file = iconFile(context, app.id)
        if (file.exists()) return
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            try {
                if (connection.responseCode != 200) return
                val limite = 512 * 1024
                val out = java.io.ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    while (out.size() <= limite) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                    }
                }
                if (out.size() <= limite) file.writeBytes(out.toByteArray())
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            Log.i(TAG, "Logo non scaricato: ${app.id}")
        }
    }

    companion object {
        fun iconFile(context: Context, id: String) =
            File(File(context.filesDir, "icone").apply { mkdirs() }, "$id.img")
    }
}

/** Esito dell'installazione: Android lo comunica dopo il commit della sessione. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val name = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.i(TAG, "Installazione di $name: stato $status ${message.orEmpty()}")
    }
}
