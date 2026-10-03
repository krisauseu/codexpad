package dev.codexpad.settings

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

// Deliberately not a data class: generated toString/copy/component methods must not expose secrets.
class ConnectionSettings(val serverUrl: String, val token: String, val trustedLanHttp: Boolean = false)

fun normalizeServerUrl(value: String, allowLocalHttp: Boolean, trustedLanHttp: Boolean = false): String {
    val url = value.trim().toHttpUrlOrNull()
        ?: throw IllegalArgumentException("Bitte eine gültige Server-URL eingeben.")
    require(url.username.isEmpty() && url.password.isEmpty() && url.query == null &&
        url.fragment == null && url.encodedPath == "/") {
        "Nur die Serveradresse eingeben, ohne Zugangsdaten, Pfad, Query oder Fragment."
    }
    require(url.isHttps || (allowLocalHttp && url.host in setOf("127.0.0.1", "localhost", "::1", "10.0.2.2")) ||
        (trustedLanHttp && isPrivateLanIpv4(url.host))) {
        "HTTP erfordert die Freigabe für eine private IPv4-LAN-Adresse. Sonst HTTPS verwenden."
    }
    return url.toString().removeSuffix("/")
}

fun validateToken(value: String) {
    require(value.matches(Regex("[A-Za-z0-9_-]{43,512}"))) {
        "Ein Zugriffstoken mit 43–512 Zeichen (A–Z, a–z, 0–9, _ oder -) ist erforderlich."
    }
}

/** Literal RFC1918 IPv4 only; never resolve names to authorize cleartext. */
fun isPrivateLanIpv4(host: String): Boolean {
    val parts = host.split('.')
    if (parts.size != 4) return false
    val bytes = parts.map {
        if (it.isEmpty() || it.length > 3 || it.any { c -> c !in '0'..'9' } ||
            (it.length > 1 && it.startsWith('0'))) return false
        it.toIntOrNull()?.takeIf { n -> n in 0..255 } ?: return false
    }
    return bytes[0] == 10 || (bytes[0] == 172 && bytes[1] in 16..31) ||
        (bytes[0] == 192 && bytes[1] == 168)
}

/** Trust belongs to this exact normalized address, including scheme and port. */
fun retainedLanHttpTrust(previousUrl: String, candidateUrl: String, trusted: Boolean): Boolean =
    trusted && runCatching {
        normalizeServerUrl(candidateUrl, false, true) == previousUrl
    }.getOrDefault(false)
