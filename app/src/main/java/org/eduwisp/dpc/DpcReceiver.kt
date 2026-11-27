// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import org.eduwisp.dpc.provisioning.Provisioning

class DpcReceiver : DeviceAdminReceiver() {

    /** Percorso di provisioning precedente ad Android 10, ancora inviato da alcuni produttori. */
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        val extras = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, PersistableBundle::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)
        }
        Provisioning.configure(context, extras)
    }

    companion object {
        fun component(context: Context) = ComponentName(context, DpcReceiver::class.java)
    }
}
