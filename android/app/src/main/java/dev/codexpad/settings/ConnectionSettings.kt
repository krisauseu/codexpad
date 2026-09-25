package dev.codexpad.settings

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

// Deliberately not a data class: generated toString/copy/component methods must not expose secrets.
class ConnectionSettings(val serverUrl: String, val token: String)

fun normalizeServerUrl(value: String, allowLocalHttp: Boolean): String {
    val url = value.trim().toHttpUrlOrNull()
        ?: throw IllegalArgumentException("Bitte eine gültige HTTPS-Server-URL eingeben.")
    require(url.username.isEmpty() && url.password.isEmpty() && url.query == null &&
        url.fragment == null && url.encodedPath == "/") {
        "Nur die Serveradresse eingeben, ohne Zugangsdaten, Pfad, Query oder Fragment."
    }
    require(url.isHttps || (allowLocalHttp && url.host in setOf("127.0.0.1", "localhost", "::1", "10.0.2.2"))) {
        "HTTPS ist erforderlich. Debug erlaubt HTTP nur für Loopback oder den Android-Emulator."
    }
    return url.toString().removeSuffix("/")
}

fun validateToken(value: String) {
    require(value.matches(Regex("[A-Za-z0-9_-]{43,512}"))) {
        "Ein Zugriffstoken mit 43–512 Zeichen (A–Z, a–z, 0–9, _ oder -) ist erforderlich."
    }
}
