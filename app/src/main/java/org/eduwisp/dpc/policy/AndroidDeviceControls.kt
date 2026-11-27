// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc.policy

import android.app.admin.DevicePolicyManager
import android.content.Context
import org.eduwisp.dpc.DpcReceiver

class AndroidDeviceControls(context: Context) : DeviceControls {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = DpcReceiver.component(context)
    private val packageName = context.packageName

    override val isDeviceOwner: Boolean get() = dpm.isDeviceOwnerApp(packageName)

    override fun setCameraDisabled(disabled: Boolean) = dpm.setCameraDisabled(admin, disabled)

    override fun setScreenCaptureDisabled(disabled: Boolean) = dpm.setScreenCaptureDisabled(admin, disabled)
}
