package dev.codexpad.network

import dev.codexpad.settings.isPrivateLanIpv4
import java.io.IOException
import java.net.InetAddress

const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
const val LOCAL_NETWORK_PERMISSION_MESSAGE =
    "Lokaler Netzwerkzugriff fehlt. Bitte die Berechtigung für Geräte in der Nähe erlauben; bei Ablehnung in den Android-App-Einstellungen freigeben."

class LocalNetworkPermissionException : IOException(LOCAL_NETWORK_PERMISSION_MESSAGE)

/** Re-evaluated per request, DNS result and pooled connection; grants are never cached. */
class LocalNetworkPolicy(
    private val apiLevel: () -> Int,
    private val granted: () -> Boolean,
    private val onBlocked: () -> Unit = {},
) {
    private val resolvedLocalHosts = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun checkResolvedHost(host: String, addresses: List<InetAddress>) {
        if (addresses.any(::isLocalAddress)) resolvedLocalHosts.add(host)
        addresses.forEach(::checkAddress)
    }

    fun missingPermission(): Boolean = apiLevel() >= 37 && !granted()

    fun checkHost(host: String) {
        if (host in resolvedLocalHosts || isPrivateLanIpv4(host) || host.endsWith(".local", ignoreCase = true) || host.contains(':')) {
            // Parsing is restricted to IPv6 literals here; no blocking DNS for ordinary names.
            if (!host.contains(':') || isLocalAddress(InetAddress.getByName(host))) checkLocal()
        }
    }

    fun checkAddress(address: InetAddress) { if (isLocalAddress(address)) checkLocal() }

    private fun checkLocal() {
        if (missingPermission()) {
            onBlocked()
            throw LocalNetworkPermissionException()
        }
    }
}

fun isLocalAddress(address: InetAddress): Boolean = !address.isLoopbackAddress &&
    (address.isSiteLocalAddress || address.isLinkLocalAddress ||
        (address.address.size == 16 && (address.address[0].toInt() and 0xfe) == 0xfc))
