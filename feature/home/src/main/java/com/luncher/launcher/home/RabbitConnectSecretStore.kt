package com.luncher.launcher.home

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.luncher.domain.settings.RabbitConnectPreferences
import com.rabbit.connect.protocol.RabbitConnectKeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

internal class RabbitConnectSecretStore(context: Context) : RabbitConnectKeyStore {
    private val prefs = context.getSharedPreferences(RabbitConnectPreferences.FILE_NAME, Context.MODE_PRIVATE)
    private val alias = "com.rabbit.tv.rabbitconnect.pairing"
    override fun load(): ByteArray? {
        val packed = prefs.getString("encrypted_pairing_key", null) ?: run {
            if (prefs.getBoolean(RabbitConnectPreferences.KEY_PAIRED, false)) prefs.edit().putBoolean(RabbitConnectPreferences.KEY_PAIRED, false).apply()
            return null
        }
        return runCatching {
            val bytes = Base64.decode(packed, Base64.NO_WRAP)
            require(bytes.size in 29..128)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            cipher.doFinal(bytes.copyOfRange(12, bytes.size)).also { require(it.size == 32) }
        }.getOrElse { clear(); null }
    }
    override fun save(key: ByteArray) {
        require(key.size == 32)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        prefs.edit().putString("encrypted_pairing_key", Base64.encodeToString(cipher.iv + cipher.doFinal(key), Base64.NO_WRAP))
            .putBoolean(RabbitConnectPreferences.KEY_PAIRED, true).apply()
    }
    override fun clear() {
        prefs.edit().remove("encrypted_pairing_key").putBoolean(RabbitConnectPreferences.KEY_PAIRED, false).apply()
        runCatching { java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) } }
    }
    private fun key(): java.security.Key {
        val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        store.getKey(alias, null)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
            generateKey()
        }
    }
}
