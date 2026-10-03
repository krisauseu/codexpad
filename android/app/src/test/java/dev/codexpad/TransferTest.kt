package dev.codexpad

import dev.codexpad.model.TransferPolicy
import dev.codexpad.model.Artifact
import dev.codexpad.network.CodexPadApi
import dev.codexpad.network.UploadText
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TransferTest {
    @Test fun longPromptsStayUnchangedAndOversizeNeverPosts() = runBlocking {
        MockWebServer().use { server ->
            val api = CodexPadApi(server.url("/").toString(), "token", allowLocalHttp = true)
            for (length in listOf(4000, 8000, 12000, 12001)) {
                val prefix = " \nGrüße äöü ß e\u0301 👋\n# Prompt\n```kotlin\nprintln(\"Hallo\")\n```\n"
                val message = prefix + "😀".repeat(length - TransferPolicy.messageLength(prefix) - 2) + "\n "
                assertEquals(length, TransferPolicy.messageLength(message))
                for (multipart in listOf(false, true)) {
                    val files = if (multipart) listOf(UploadText("seite.htm", "text/html", "<p>Grüße</p>".toByteArray())) else emptyList()
                    if (length > 12000) {
                        val before = server.requestCount
                        try { api.startTurn("t", message, null, null, emptyList(), files); fail("Expected rejection") }
                        catch (error: IllegalArgumentException) { assertEquals(TransferPolicy.MESSAGE_LIMIT_ERROR, error.message) }
                        assertEquals(before, server.requestCount)
                    } else {
                        server.enqueue(MockResponse().setResponseCode(202).setBody("""{"turn":{"id":"u","status":"inProgress","items":[]}}"""))
                        api.startTurn("t", message, null, null, emptyList(), files)
                        val request = server.takeRequest()
                        val raw = request.body.readUtf8()
                        if (multipart) {
                            val boundary = request.getHeader("Content-Type")!!.substringAfter("boundary=")
                            val value = raw.substringAfter("name=\"message\"").substringAfter("\r\n\r\n").substringBefore("\r\n--$boundary")
                            assertEquals(message, value)
                            assertTrue(raw.contains("filename=\"seite.htm\""))
                            assertTrue(raw.contains("Content-Type: text/html"))
                            assertTrue(raw.contains("<p>Grüße</p>"))
                        } else assertEquals(message, JSONObject(raw).getString("message"))
                    }
                }
            }
        }
    }

    @Test fun pickerAndValidationKeepExplicitTextAllowlist() {
        assertTrue("text/html" in TransferPolicy.pickerMimeTypes)
        for ((suffix, mime) in TransferPolicy.textTypes) {
            assertTrue(TransferPolicy.acceptsText("Grüße$suffix", mime))
            assertTrue(TransferPolicy.acceptsText("PAGE${suffix.uppercase()}", "text/plain"))
        }
        for (name in listOf("app.js", "run.exe", "page.html.exe", "page")) {
            assertFalse(TransferPolicy.acceptsText(name, "text/html"))
        }
        assertFalse(TransferPolicy.acceptsText("page.html", "application/octet-stream"))
        assertFalse(TransferPolicy.acceptsText("page.htm", null))
        assertEquals("text/plain", TransferPolicy.openMimeType("text/html"))
        assertEquals("text/plain", TransferPolicy.openMimeType("text/markdown"))
        assertEquals("application/pdf", TransferPolicy.openMimeType("application/pdf"))
    }

    @Test fun htmlUploadAndDownloadPreserveNamesAndBytes() = runBlocking {
        MockWebServer().use { server ->
            val api = CodexPadApi(server.url("/").toString(), "token", allowLocalHttp = true)
            val content = "<!doctype html>\r\n<p>Grüße 👋</p>\n<script>alert(1)</script>\n"
            for (name in listOf("seite.html", "seite.htm")) {
                server.enqueue(MockResponse().setResponseCode(202).setBody("""{"turn":{"id":"u","status":"inProgress","items":[]}}"""))
                api.startTurn("t", "", null, null, emptyList(), listOf(UploadText(name, "text/html", content.toByteArray())))
                val upload = server.takeRequest().body.readUtf8()
                assertTrue(upload.contains("filename=\"$name\""))
                assertTrue(upload.contains(content))
                val artifact = Artifact("a", name, "text/html", content.toByteArray().size.toLong())
                val directory = java.nio.file.Files.createTempDirectory("html-download").toFile()
                val target = File(directory, artifact.name)
                try {
                    server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody(content))
                    api.downloadArtifact("t", artifact, target)
                    assertEquals(name, target.name)
                    assertArrayEquals(content.toByteArray(), target.readBytes())
                    assertEquals("Bearer token", server.takeRequest().getHeader("Authorization"))
                } finally { directory.deleteRecursively() }
            }
        }
    }
}
