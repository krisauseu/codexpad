package dev.codexpad

import dev.codexpad.settings.*
import org.junit.Assert.*
import org.junit.Test

class ConnectionSettingsTest {
    @Test fun httpsDefaultAndLocalDebugRemainAvailable() {
        assertEquals("https://pad.feichti.dev", normalizeServerUrl(" https://pad.feichti.dev/ ", false))
        assertEquals("https://my.example:8443", normalizeServerUrl("https://my.example:8443", false))
        assertEquals("http://127.0.0.1:8765", normalizeServerUrl("http://127.0.0.1:8765", true))
        assertEquals("http://10.0.2.2:8765", normalizeServerUrl("http://10.0.2.2:8765", true))
    }

    @Test fun insecureOrCredentialBearingUrlsAreRejectedWithoutEcho() {
        for (url in listOf("http://pad.feichti.dev", "https://user:secret@pad.feichti.dev",
            "https://pad.feichti.dev?token=secret", "https://pad.feichti.dev/#secret",
            "https://pad.feichti.dev/secret", "invalid-secret")) {
            val error = runCatching { normalizeServerUrl(url, true) }.exceptionOrNull()
            assertNotNull(error)
            assertFalse(error!!.message.orEmpty().contains("secret"))
        }
        assertTrue(runCatching { normalizeServerUrl("http://127.0.0.1:8765", false) }.isFailure)
    }

    @Test fun tokensAreValidatedWithoutSecretDiagnostics() {
        val token = java.util.UUID.randomUUID().toString().replace("-", "").repeat(2)
        validateToken(token)
        assertFalse(ConnectionSettings("https://pad.feichti.dev", token).toString().contains(token))
        for (invalid in listOf("", "short", "ü".repeat(64), token + "\n")) {
            assertTrue(runCatching { validateToken(invalid) }.isFailure)
        }
    }

    @Test fun trustedLanHttpIsExplicitAndOnlyRfc1918() {
        for (url in listOf("https://example.com", "https://172.16.16.39:8876")) {
            assertEquals(url, normalizeServerUrl(url, false))
            assertEquals(url, normalizeServerUrl(url, false, true))
        }
        for (host in listOf("10.0.0.1", "172.16.16.39:8876", "172.31.255.255", "192.168.1.20")) {
            val url = "http://$host"
            assertEquals(url, normalizeServerUrl(url, false, true))
            assertTrue(runCatching { normalizeServerUrl(url, false) }.isFailure)
        }
        for (host in listOf("example.com", "8.8.8.8", "1.2.3.4", "172.15.255.255", "172.32.0.0",
            "192.169.1.20", "11.0.0.1", "[fd00::1]", "[fe80::1]", "[::1]", "10.1", "192.168.example.com")) {
            assertTrue(host, runCatching { normalizeServerUrl("http://$host", false, true) }.isFailure)
        }
        for (host in listOf("10.0.0.256", "10.0.-1.1", "010.0.0.1", "10.0.1", "192.168", "::ffff:192.168.1.1")) {
            assertFalse(host, isPrivateLanIpv4(host))
        }
    }

    @Test fun trustIsResetForSchemeHostOrPortChanges() {
        val original = "http://172.16.16.39:8876"
        assertTrue(retainedLanHttpTrust(original, " $original/ ", true))
        assertFalse(retainedLanHttpTrust(original, original, false))
        for (other in listOf("https://172.16.16.39:8876", "http://172.16.16.40:8876",
            "http://172.16.16.39:8877", "http://example.com", "invalid")) {
            assertFalse(retainedLanHttpTrust(original, other, true))
        }
    }
}
