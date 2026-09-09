package com.skhealth.guardian.mobile

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class EmergencyContact(
    val name: String,
    val phoneNumber: String,
    val smsEnabled: Boolean = true,
    val callEnabled: Boolean = false
)

object ContactStore {
    private const val PREF = "emergency_contacts"
    private const val KEY_ENCRYPTED = "rows_encrypted"
    private const val KEY_LEGACY = "rows"

    fun contacts(context: Context): List<EmergencyContact> {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val encrypted = prefs.getString(KEY_ENCRYPTED, null)
        if (encrypted != null) return decode(SecureContactCrypto.decrypt(encrypted)).sortedBy { it.name }

        val legacy = prefs.getStringSet(KEY_LEGACY, emptySet()).orEmpty().mapNotNull(::decodeLegacy)
        if (legacy.isNotEmpty()) {
            save(context, legacy)
            prefs.edit().remove(KEY_LEGACY).apply()
        }
        return legacy.sortedBy { it.name }
    }

    fun save(context: Context, contacts: List<EmergencyContact>) {
        val plain = contacts.joinToString("\n") {
            listOf(it.name, it.phoneNumber, it.smsEnabled, it.callEnabled).joinToString("\u001F")
        }
        val encrypted = SecureContactCrypto.encrypt(plain)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_ENCRYPTED, encrypted)
            .remove(KEY_LEGACY)
            .apply()
    }

    private fun decode(plain: String): List<EmergencyContact> = plain.lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull(::decodeLegacy)
        .toList()

    private fun decodeLegacy(row: String): EmergencyContact? {
        val p = row.split('\u001F')
        return if (p.size < 4) null else EmergencyContact(p[0], p[1], p[2].toBoolean(), p[3].toBoolean())
    }
}

private object SecureContactCrypto {
    private const val KEY_ALIAS = "sk_health_guardian_contacts_v1"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String {
        return runCatching {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size > 12)
            val iv = packed.copyOfRange(0, 12)
            val encrypted = packed.copyOfRange(12, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
