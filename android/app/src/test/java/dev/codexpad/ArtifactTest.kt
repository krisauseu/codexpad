package dev.codexpad

import dev.codexpad.model.*
import dev.codexpad.data.Timeline
import dev.codexpad.network.CodexPadApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ArtifactTest {
    @Test fun historyRestoresArtifactsAndOldServersRemainCompatible() {
        val thread = Wire.thread(JSONObject("""{"id":"t","turns":[{"id":"u","status":"completed","items":[],"artifacts":[{"id":"a","name":"notes.md","mimeType":"text/markdown","size":7}]}]}"""))
        val timeline = Timeline().reconcile(thread).reconcile(thread, resetLive = true)
        assertEquals("notes.md", timeline.turns.single().artifacts.single().name)
        assertTrue(Wire.turn(JSONObject("""{"id":"old","status":"completed","items":[]}""")).artifacts.isEmpty())
    }
    @Test fun downloadUsesAuthAndRejectsRedirectsAndOversize() = runBlocking {
        MockWebServer().use { server ->
            val api = CodexPadApi(server.url("/").toString(), "test-token", allowLocalHttp = true)
            val artifact = Artifact("a", "notes.md", "text/markdown", 7)
            val file = File.createTempFile("artifact", ".md")
            try {
                server.enqueue(MockResponse().setHeader("Content-Type", "text/markdown").setBody("content"))
                api.downloadArtifact("t", artifact, file)
                assertEquals("content", file.readText())
                val request = server.takeRequest()
                assertEquals("/threads/t/artifacts/a", request.path)
                assertEquals("Bearer test-token", request.getHeader("Authorization"))
                server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/leak")))
                try { api.downloadArtifact("t", artifact, file); fail() } catch (_: java.io.IOException) { }
                assertFalse(file.exists())
                server.takeRequest()
                server.enqueue(MockResponse().setHeader("Content-Type", "text/markdown").setHeader("Content-Length", 65L * 1024 * 1024))
                try { api.downloadArtifact("t", artifact, file); fail() } catch (_: java.io.IOException) { }
                assertFalse(file.exists())
                assertEquals(3, server.requestCount)
            } finally { file.delete() }
        }
    }
}
