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
}
