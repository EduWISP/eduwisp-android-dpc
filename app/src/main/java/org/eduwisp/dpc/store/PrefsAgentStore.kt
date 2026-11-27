// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.dpc.store

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.eduwisp.dpc.protocol.FamilyAccessChoice

class PrefsAgentStore(context: Context) : AgentStore {
    val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override var serverUrl by string("server_url")
    override var enrollmentToken by string("enrollment_token")
    override var deviceId by string("device_id")
    override var deviceToken: String?
        get() = prefs.getString("device_token", null)?.let(TokenCipher::decrypt)
        set(value) = prefs.edit().putString("device_token", value?.let(TokenCipher::encrypt)).apply()
    override var pollIntervalSeconds: Int
        get() = prefs.getInt("poll_interval_seconds", DEFAULT_POLL_SECONDS)
        set(value) = prefs.edit().putInt("poll_interval_seconds", value).apply()
    override var policyVersionApplied: Int
        get() = prefs.getInt("policy_version_applied", 0)
        set(value) = prefs.edit().putInt("policy_version_applied", value).apply()
    override var nonCompliance: List<String>
        get() = prefs.getString("non_compliance", "")!!.split(',').filter { it.isNotEmpty() }
        set(value) = prefs.edit().putString("non_compliance", value.joinToString(",")).apply()
    override var lastSyncMillis: Long
        get() = prefs.getLong("last_sync", 0)
        set(value) = prefs.edit().putLong("last_sync", value).apply()
    override var lastError by string("last_error")
    override var retired: Boolean
        get() = prefs.getBoolean("retired", false)
        set(value) = prefs.edit().putBoolean("retired", value).apply()
    override var familyAccessRequested: Boolean
        get() = prefs.getBoolean("family_access_requested", false)
        set(value) = prefs.edit().putBoolean("family_access_requested", value).apply()
    override var familyAccessGranted: Boolean?
        get() = prefs.getString("family_access_granted", null)?.toBooleanStrictOrNull()
        set(value) = prefs.edit().putString("family_access_granted", value?.toString()).apply()
    override var pendingFamilyChoice: FamilyAccessChoice?
        get() {
            val granted = prefs.getString("family_choice_granted", null)?.toBooleanStrictOrNull()
            val at = prefs.getString("family_choice_at", null)
            return if (granted != null && at != null) FamilyAccessChoice(granted, at) else null
        }
        set(value) = prefs.edit()
            .putString("family_choice_granted", value?.granted?.toString())
            .putString("family_choice_at", value?.decidedAt)
            .apply()

    private fun string(key: String) = object : kotlin.properties.ReadWriteProperty<Any, String?> {
        override fun getValue(thisRef: Any, property: kotlin.reflect.KProperty<*>) = prefs.getString(key, null)
        override fun setValue(thisRef: Any, property: kotlin.reflect.KProperty<*>, value: String?) =
            prefs.edit().putString(key, value).apply()
    }

    companion object {
        const val FILE = "agent"
    }
}

/** AES-GCM con chiave non esportabile nell'Android Keystore. */
private object TokenCipher {
    private const val ALIAS = "eduwisp-device-token"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(ALIAS, null) as SecretKey?)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val data = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String? = try {
        val data = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, 12))
        String(cipher.doFinal(data, 12, data.size - 12), Charsets.UTF_8)
    } catch (e: java.security.GeneralSecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
