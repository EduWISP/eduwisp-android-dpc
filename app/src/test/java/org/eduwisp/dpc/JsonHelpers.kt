// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc

import org.json.JSONArray
import org.json.JSONObject

/** Conversione in strutture Kotlin: android.jar espone solo l'API di base di org.json. */
fun JSONObject.asMap(): Map<String, Any?> = keys().asSequence().associateWith { plain(get(it)) }

fun JSONArray.asList(): List<Any?> = (0 until length()).map { plain(get(it)) }

private fun plain(value: Any?): Any? = when (value) {
    is JSONObject -> value.asMap()
    is JSONArray -> value.asList()
    JSONObject.NULL -> null
    else -> value
}
