package dev.codexpad.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.codexpad.BuildConfig
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Call on Dispatchers.IO. Only ciphertext + IV and the non-secret URL enter app-private preferences. */
class SettingsStore(context: Context, storageName: String = "connection") {
    private val prefs = context.getSharedPreferences(storageName, Context.MODE_PRIVATE)
    private val alias = "codexpad.$storageName.v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    fun load(): ConnectionSettings {
        val url = normalizeServerUrl(prefs.getString("url", BuildConfig.SERVER_URL)!!, BuildConfig.DEBUG)
        val encrypted = prefs.getString("token", null) ?: return ConnectionSettings(url, "")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128,
            Base64.decode(prefs.getString("iv", null) ?: error("Missing IV"), Base64.NO_WRAP)))
        cipher.updateAAD(url.toByteArray(Charsets.UTF_8))
        val token = String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
        validateToken(token)
        return ConnectionSettings(url, token)
    }

    fun save(settings: ConnectionSettings) {
        validateToken(settings.token)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key()) // Fresh randomized IV for every write.
        cipher.updateAAD(settings.serverUrl.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(settings.token.toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("url", settings.serverUrl)
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("token", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit())
    }
}
