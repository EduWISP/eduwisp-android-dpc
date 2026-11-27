// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.eduwisp.dpc.provisioning.Provisioning

/**
 * Solo build di debug, solo da adb (permesso DUMP): simula gli extra del QR code.
 *
 *     adb shell am broadcast -n org.eduwisp.dpc/.debug.DebugConfigReceiver \
 *         --es org.eduwisp.SERVER_URL https://demo.eduwisp.org \
 *         --es org.eduwisp.ENROLLMENT_TOKEN <token>
 */
class DebugConfigReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ok = Provisioning.configure(context, intent.extras)
        Log.i("EduWISP", if (ok) "Configurazione di prova salvata" else "Configurazione di prova non valida")
        resultCode = if (ok) 0 else 1
    }
}
