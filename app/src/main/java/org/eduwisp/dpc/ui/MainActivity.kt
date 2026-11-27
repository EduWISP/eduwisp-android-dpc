// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc.ui

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.SharedPreferences
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.widget.Button
import android.widget.TextView
import org.eduwisp.dpc.R
import org.eduwisp.dpc.store.PrefsAgentStore
import org.eduwisp.dpc.sync.Sync

/** Schermata informativa: chi usa il dispositivo sa che è gestito e cosa vede la scuola. */
class MainActivity : Activity() {
    private lateinit var store: PrefsAgentStore
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = PrefsAgentStore(this)
        findViewById<Button>(R.id.sync).setOnClickListener { Sync.runNow(this) }
    }

    override fun onStart() {
        super.onStart()
        store.prefs.registerOnSharedPreferenceChangeListener(listener)
        render()
    }

    override fun onStop() {
        store.prefs.unregisterOnSharedPreferenceChangeListener(listener)
        super.onStop()
    }

    private fun render() {
        val owner = getSystemService(DevicePolicyManager::class.java).isDeviceOwnerApp(packageName)
        findViewById<View>(R.id.warning).visibility = if (owner) View.GONE else View.VISIBLE

        val state = when {
            store.retired -> getString(R.string.state_retired)
            store.serverUrl == null -> getString(R.string.state_not_configured)
            store.deviceId == null -> getString(R.string.state_waiting)
            store.lastSyncMillis == 0L -> getString(R.string.state_enrolled)
            store.nonCompliance.isEmpty() -> getString(R.string.state_compliant)
            else -> getString(R.string.state_non_compliant)
        }
        val lastSync = store.lastSyncMillis.takeIf { it > 0 }
            ?.let { DateUtils.getRelativeTimeSpanString(it).toString() } ?: getString(R.string.value_never)
        val lines = buildList {
            add(getString(R.string.label_state) + ": " + state)
            store.lastError?.let { add(getString(R.string.state_error, it)) }
            add(getString(R.string.label_server) + ": " + (store.serverUrl ?: getString(R.string.value_not_configured)))
            store.deviceId?.let { add(getString(R.string.label_device) + ": " + it) }
            add(getString(R.string.label_last_sync) + ": " + lastSync)
            add(getString(R.string.label_policy) + ": " + store.policyVersionApplied)
            add(
                getString(R.string.label_issues) + ": " +
                    store.nonCompliance.ifEmpty { listOf(getString(R.string.value_none)) }.joinToString(", "),
            )
        }
        findViewById<TextView>(R.id.details).text = lines.joinToString("\n")
    }
}
