// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc.protocol

import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Esito di una chiamata: risposta valida, errore del protocollo o problema di rete. */
sealed interface Result<out T> {
    data class Ok<T>(val value: T) : Result<T>
    data class Rejected(val httpStatus: Int, val error: ProtocolError) : Result<Nothing>
    data class Unreachable(val reason: String) : Result<Nothing>
}

/** Trasporto HTTP sostituibile nei test. */
fun interface Transport {
    /** Restituisce codice HTTP e corpo (vuoto se assente). */
    @Throws(IOException::class)
    fun exchange(method: String, url: String, bearer: String?, body: String?): Pair<Int, String>
}

class ProtocolClient(
    serverUrl: String,
    private val transport: Transport = HttpTransport(),
) {
    private val base = serverUrl.trimEnd('/') + "/api/protocol/v0"

    fun enroll(request: EnrollmentRequest): Result<EnrollmentResponse> =
        call("POST", "$base/enroll", null, request.toJson().toString(), 201) { EnrollmentResponse.parse(it!!) }

    fun policy(deviceToken: String): Result<Policy> =
        call("GET", "$base/policy", deviceToken, null, 200) { Policy.parse(it!!) }

    fun status(deviceToken: String, report: StatusReport): Result<Unit> =
        call("POST", "$base/status", deviceToken, report.toJson().toString(), 204) { }

    /** Richiesta di un'app (ADR-0021): restituisce lo stato (pending, approved, rejected). */
    fun appRequest(deviceToken: String, request: AppRequestMessage): Result<String> =
        call("POST", "$base/app-requests", deviceToken, request.toJson().toString(), 201, 200) {
            checkVersion(it!!)
            it.getString("status")
        }

    private fun <T> call(
        method: String,
        url: String,
        bearer: String?,
        body: String?,
        expected: Int,
        alsoExpected: Int? = null,
        parse: (JSONObject?) -> T,
    ): Result<T> {
        val (code, text) = try {
            transport.exchange(method, url, bearer, body)
        } catch (e: IOException) {
            return Result.Unreachable(e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""))
        }
        return try {
            val json = text.takeIf { it.isNotBlank() }?.let(::JSONObject)
            when {
                code == expected || code == alsoExpected -> Result.Ok(parse(json))
                json != null && json.has("error") -> Result.Rejected(code, ProtocolError.parse(json))
                else -> Result.Unreachable("HTTP $code")
            }
        } catch (e: JSONException) {
            Result.Unreachable("risposta non valida (HTTP $code)")
        } catch (e: ProtocolException) {
            Result.Unreachable(e.message ?: "risposta non valida")
        }
    }
}

class HttpTransport(private val timeoutMs: Int = 20_000) : Transport {
    override fun exchange(method: String, url: String, bearer: String?, body: String?): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "EduWISP-DPC")
            bearer?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return code to text
        } finally {
            connection.disconnect()
        }
    }
}
