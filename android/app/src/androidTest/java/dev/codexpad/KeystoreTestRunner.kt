package dev.codexpad

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import dev.codexpad.settings.ConnectionSettings
import dev.codexpad.settings.SettingsStore
import dev.codexpad.settings.retainedLanHttpTrust
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
                val lan = "http://172.16.16.39:8876"
                check(runCatching { store.save(ConnectionSettings(lan, token)) }.isFailure)
                store.save(ConnectionSettings(lan, token, true))
                val loaded = SettingsStore(targetContext, name).load()
                check(loaded.serverUrl == lan && loaded.token == token && loaded.trustedLanHttp)
                check(prefs.all.values.none { it.toString().contains(token) })
                // Trust metadata cannot authorize a changed address by itself.
                check(prefs.edit().putString("url", "http://172.16.16.40:8876").commit())
                check(runCatching { store.load() }.isFailure)
                check(prefs.edit().putString("url", lan).commit())
                check(!retainedLanHttpTrust(lan, "https://pad.feichti.dev", loaded.trustedLanHttp))
                store.save(ConnectionSettings("https://pad.feichti.dev", token, true))
                check(!store.load().trustedLanHttp && store.load().token == token)
                // Leave a LAN configuration for the second instrumentation process.
                store.save(ConnectionSettings(lan, token, true))
            } else {
                val saved = store.load()
                check(saved.serverUrl == "http://172.16.16.39:8876" && saved.token.length == 64 && saved.trustedLanHttp)
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
