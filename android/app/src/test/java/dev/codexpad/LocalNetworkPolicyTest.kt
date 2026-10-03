package dev.codexpad

import dev.codexpad.network.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.io.File

class LocalNetworkPolicyTest {
    @Test fun olderApiDoesNotReadOrRequireNewPermission() {
        val policy = LocalNetworkPolicy({ 36 }, { error("Must not query API 37 permission") })
        policy.checkHost("172.16.16.39")
        assertFalse(policy.missingPermission())
    }

    @Test fun grantedDeniedAndRevokedAreCheckedAgainIncludingResolvedHttpsTargets() {
        var granted = true
        var blocked = 0
        val policy = LocalNetworkPolicy({ 37 }, { granted }, { blocked++ })
        policy.checkHost("172.16.16.39")
        policy.checkAddress(InetAddress.getByName("192.168.1.1"))
        policy.checkResolvedHost("private.example", listOf(InetAddress.getByName("10.0.0.1")))
        granted = false
        for (host in listOf("172.16.16.39", "private.example", "host.local", "fd00::1", "fe80::1")) {
            assertTrue(runCatching { policy.checkHost(host) }.exceptionOrNull() is LocalNetworkPermissionException)
        }
        assertTrue(runCatching { policy.checkAddress(InetAddress.getByName("10.0.0.1")) }
            .exceptionOrNull() is LocalNetworkPermissionException)
        policy.checkHost("example.com")
        policy.checkAddress(InetAddress.getByName("8.8.8.8"))
        policy.checkAddress(InetAddress.getByName("127.0.0.1"))
        assertEquals(6, blocked)
        granted = true
        policy.checkHost("172.16.16.39")
    }

    @Test fun allTransportsFailBeforeConnectingWhenLanPermissionIsMissing() = runBlocking {
        val policy = LocalNetworkPolicy({ 37 }, { false })
        val api = CodexPadApi("http://172.16.16.39:8876", "test-token", trustedLanHttp = true, localNetwork = policy)
        val target = File.createTempFile("codexpad-permission", ".txt")
        try {
            val operations: List<suspend () -> Any?> = listOf(
                { api.health() }, { api.workspaces() }, { api.models() }, { api.rateLimits() },
                { api.history("t") }, { api.startTurn("t", "test") },
                { api.startTurn("t", "test", null, null, emptyList(), listOf(UploadText("test.txt", "text/plain", byteArrayOf(1)))) },
                { api.events("t").first() },
                { api.downloadArtifact("t", dev.codexpad.model.Artifact("a", "test.txt", "text/plain", 1), target) },
            )
            for (operation in operations) {
                val error = runCatching { operation() }.exceptionOrNull()
                assertTrue(error?.javaClass?.name, error is LocalNetworkPermissionException)
                assertEquals(LOCAL_NETWORK_PERMISSION_MESSAGE, connectionError(error as Exception))
            }
        } finally { target.delete() }
    }

    @Test fun apiCannotBypassCleartextPolicy() {
        for (url in listOf("http://example.com", "http://8.8.8.8", "http://172.16.16.39")) {
            assertTrue(runCatching { CodexPadApi(url, "test") }.isFailure)
        }
        assertTrue(runCatching { CodexPadApi("http://example.com", "test", trustedLanHttp = true) }.isFailure)
    }
}
