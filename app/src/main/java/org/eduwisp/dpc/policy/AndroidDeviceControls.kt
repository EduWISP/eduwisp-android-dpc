// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc.policy

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.UserManager
import android.view.inputmethod.InputMethodManager
import org.eduwisp.dpc.EduWispAdminReceiver
import org.eduwisp.dpc.R

class AndroidDeviceControls(private val context: Context) : DeviceControls {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = EduWispAdminReceiver.component(context)
    private val packageName = context.packageName
    private val pm = context.packageManager

    override val isDeviceOwner: Boolean get() = dpm.isDeviceOwnerApp(packageName)

    override fun setCameraDisabled(disabled: Boolean) = dpm.setCameraDisabled(admin, disabled)

    override fun setScreenCaptureDisabled(disabled: Boolean) = dpm.setScreenCaptureDisabled(admin, disabled)

    private fun packagesFor(category: String): Set<String> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .toSet()

    override fun launchablePackages(): Set<String> = packagesFor(Intent.CATEGORY_LAUNCHER)

    override fun protectedPackages(): Set<String> {
        val keyboards = context.getSystemService(InputMethodManager::class.java)
            .enabledInputMethodList.map { it.packageName }
        return setOf(packageName) + packagesFor(Intent.CATEGORY_HOME) + keyboards
    }

    override fun setPackagesSuspended(packages: Set<String>, suspended: Boolean): Set<String> {
        if (packages.isEmpty()) return emptySet()
        if (suspended) dpm.setShortSupportMessage(admin, context.getString(R.string.app_blocked_message))
        val failed = dpm.setPackagesSuspended(admin, packages.toTypedArray(), suspended).toSet()
        // Android non lascia mai sospendere alcune app di sistema essenziali (per esempio il
        // telefono predefinito): non è un errore della policy.
        return failed.filterNot(::isSystemApp).toSet()
    }

    private fun isSystemApp(name: String): Boolean = try {
        pm.getApplicationInfo(name, 0).flags and ApplicationInfo.FLAG_SYSTEM != 0
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    override fun setVpnConfigDisabled(disabled: Boolean) = restriction(UserManager.DISALLOW_CONFIG_VPN, disabled)

    override fun setPrivateDnsConfigDisabled(disabled: Boolean) =
        restriction(UserManager.DISALLOW_CONFIG_PRIVATE_DNS, disabled)

    private fun restriction(name: String, on: Boolean) {
        if (on) dpm.addUserRestriction(admin, name) else dpm.clearUserRestriction(admin, name)
    }

    override fun setUnknownSourcesBlocked(blocked: Boolean) {
        for (restriction in listOf(
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY,
        )) {
            if (blocked) dpm.addUserRestriction(admin, restriction) else dpm.clearUserRestriction(admin, restriction)
        }
    }
}
