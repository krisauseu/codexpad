package dev.codexpad

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import dev.codexpad.settings.ConnectionSettings
import dev.codexpad.settings.SettingsStore
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64

/** No test dependencies; real Android Keystore. Two invocations verify a process restart. */
class KeystoreTestRunner : Instrumentation() {
    private var phase = ""
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        phase = arguments?.getString("phase").orEmpty()
        start()
    }

    override fun onStart() {
        val result = Bundle()
        try {
            val name = "connection-instrumentation-test"
            val prefs = targetContext.getSharedPreferences(name, Context.MODE_PRIVATE)
            val store = SettingsStore(targetContext, name)
            if (phase == "write") {
                val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(48).also { SecureRandom().nextBytes(it) })
                store.save(ConnectionSettings("https://pad.feichti.dev", token))
                check(SettingsStore(targetContext, name).load().token == token)
                check(prefs.all.values.none { it.toString().contains(token) })
                val firstCipher = prefs.getString("token", null)
                store.save(ConnectionSettings("https://pad.feichti.dev", token))
                check(firstCipher != prefs.getString("token", null))
            } else {
                val saved = store.load()
                check(saved.serverUrl == "https://pad.feichti.dev" && saved.token.length == 64)
                // The URL is authenticated with the ciphertext: disk tampering cannot redirect a saved token.
                check(prefs.edit().putString("url", "https://different.example").commit())
                check(runCatching { store.load() }.isFailure)
                check(prefs.edit().clear().commit())
                KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("codexpad.$name.v1") }
            }
            result.putString("stream", "Keystore $phase PASS: encrypted persistence / restart / tamper protection\n")
            finish(Activity.RESULT_OK, result)
        } catch (_: Exception) {
            // Never print crypto/config values or arbitrary exceptions.
            result.putString("stream", "Keystore test FAILED\n")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
