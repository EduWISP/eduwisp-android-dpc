// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc.ui

import android.app.Activity
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.net.Uri
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.time.OffsetDateTime
import org.eduwisp.dpc.R
import org.eduwisp.dpc.protocol.FamilyAccessChoice
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
        findViewById<Button>(R.id.family_allow).setOnClickListener { askConfirmation(true) }
        findViewById<Button>(R.id.family_deny).setOnClickListener { askConfirmation(false) }
        savedInstanceState?.let { if (it.containsKey(KEY_CHOICE)) choiceInProgress = it.getBoolean(KEY_CHOICE) }
    }

    /** Scelta in attesa della conferma con il blocco schermo. */
    private var choiceInProgress: Boolean? = null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        choiceInProgress?.let { outState.putBoolean(KEY_CHOICE, it) }
    }

    /**
     * Studenti maggiorenni (ADR-0018): la scelta vale solo se confermata con il blocco schermo,
     * così non può farla un'altra persona che ha il tablet in mano.
     */
    @Suppress("DEPRECATION") // createConfirmDeviceCredentialIntent: semplice e disponibile da API 21
    private fun askConfirmation(granted: Boolean) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isDeviceSecure) {
            findViewById<TextView>(R.id.family_state).text = getString(R.string.family_need_lock)
            return
        }
        val description = getString(if (granted) R.string.family_confirm_allow else R.string.family_confirm_deny)
        val intent = keyguard.createConfirmDeviceCredentialIntent(getString(R.string.family_confirm_title), description)
            ?: return
        choiceInProgress = granted
        startActivityForResult(intent, REQUEST_CONFIRM)
    }

    @Deprecated("Activity di piattaforma senza AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CONFIRM) return
        val granted = choiceInProgress
        choiceInProgress = null
        if (resultCode != RESULT_OK || granted == null) return
        store.pendingFamilyChoice = FamilyAccessChoice(granted, OffsetDateTime.now().withNano(0).toString())
        Sync.runNow(this)
        render()
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
        renderFamily()
        renderRequired()
    }

    /** App obbligatorie non installate (ADR-0019): un pulsante per ciascuna apre lo store. */
    private fun renderRequired() {
        val missing = store.missingRequiredApps
        findViewById<View>(R.id.required_section).visibility = if (missing.isEmpty()) View.GONE else View.VISIBLE
        val list = findViewById<LinearLayout>(R.id.required_list)
        list.removeAllViews()
        for (app in missing) {
            list.addView(
                Button(this).apply {
                    text = getString(R.string.required_install, app.name ?: app.id)
                    isAllCaps = false
                    setOnClickListener { openStore(app.id) }
                },
            )
        }
    }

    private fun openStore(id: String) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id"))
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id"))
        startActivity(if (market.resolveActivity(packageManager) != null) market else web)
    }

    private fun renderFamily() {
        val section = findViewById<View>(R.id.family_section)
        section.visibility = if (store.familyAccessRequested) View.VISIBLE else View.GONE
        if (!store.familyAccessRequested) return
        val pending = store.pendingFamilyChoice
        findViewById<TextView>(R.id.family_state).text = getString(
            when {
                pending != null -> R.string.family_state_pending
                store.familyAccessGranted == true -> R.string.family_state_granted
                store.familyAccessGranted == false -> R.string.family_state_denied
                else -> R.string.family_state_none
            },
        )
    }

    private companion object {
        const val REQUEST_CONFIRM = 1
        const val KEY_CHOICE = "family_choice"
    }
}
