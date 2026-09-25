package dev.codexpad

import dev.codexpad.network.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class ApiTest {
    private val token = java.util.UUID.randomUUID().toString().replace("-", "") + java.util.UUID.randomUUID().toString().replace("-", "")
    @Test fun endpointsAndBodiesMatchServerContract() = runBlocking {
        MockWebServer().use { server ->
            val api = CodexPadApi(server.url("/").toString(), token)
            val t = """{"thread":{"id":"t","status":{"type":"idle"},"turns":[]}}"""
            server.enqueue(MockResponse().setBody("""{"status":"ok","appServer":{}}"""))
            assertEquals("ok", api.health())
            server.enqueue(MockResponse().setBody("""{"workspaces":[{"id":"demo","name":"demo"}]}"""))
            assertEquals("demo", api.workspaces().single().id)
            server.enqueue(MockResponse().setBody("""{"threads":[{"id":"t","preview":"Hi"}]}"""))
            assertEquals("Hi", api.threads("demo").single().preview)
            server.enqueue(MockResponse().setResponseCode(201).setBody(t))
            api.createThread("demo")
            server.enqueue(MockResponse().setBody(t)); api.thread("t")
            server.enqueue(MockResponse().setBody(t)); api.history("t")
            server.enqueue(MockResponse().setResponseCode(202).setBody("""{"turn":{"id":"turn","status":"inProgress","items":[]}}"""))
            api.startTurn("t", "Grüße 👋")
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("event: snapshot\ndata: $t\n\n"))
            assertEquals("snapshot", api.events("t").first().event)
            val requests = (1..8).map { server.takeRequest(2, TimeUnit.SECONDS)!! }
            assertEquals(listOf("/health", "/workspaces", "/workspaces/demo/threads", "/workspaces/demo/threads",
                "/threads/t", "/threads/t/history", "/threads/t/turns", "/threads/t/events"), requests.map { it.path })
            requests.forEach { assertEquals("Bearer $token", it.getHeader("Authorization")) }
            assertEquals("{}", requests[3].body.readUtf8())
            assertEquals("POST", requests[6].method)
            assertEquals("Grüße 👋", JSONObject(requests[6].body.readUtf8()).getString("message"))
        }
    }

    @Test fun lostPostResponseNeverRetries() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            val result = runCatching { CodexPadApi(server.url("/").toString(), token).startTurn("t", "Once only") }
            assertTrue(result.isFailure)
            assertEquals("POST", server.takeRequest(2, TimeUnit.SECONDS)!!.method)
            assertNull(server.takeRequest(250, TimeUnit.MILLISECONDS))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun errorBodyIsReadableAndWorkspaceIdIsEncoded() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"Unknown workspace"}"""))
            val error = runCatching { CodexPadApi(server.url("/").toString(), token).threads("Grüße #1") }.exceptionOrNull()
            assertEquals(httpErrorMessage(404), error?.message)
            assertEquals(404, (error as ApiException).status)
            assertEquals("/workspaces/Gr%C3%BC%C3%9Fe%20%231/threads", server.takeRequest().path)
        }
    }

    @Test fun redirectsNeverForwardTokenAndErrorsNeverEchoIt() = runBlocking {
        MockWebServer().use { origin ->
            MockWebServer().use { other ->
                val api = CodexPadApi(origin.url("/").toString(), token)
                origin.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/workspaces")))
                assertEquals(302, (runCatching { api.workspaces() }.exceptionOrNull() as ApiException).status)
                assertNull(other.takeRequest(250, TimeUnit.MILLISECONDS))
                origin.enqueue(MockResponse().setResponseCode(401).setBody("{\"error\":\"$token\"}"))
                val failure = runCatching { api.workspaces() }.exceptionOrNull()!!
                assertEquals(httpErrorMessage(401), failure.message)
                assertFalse(failure.toString().contains(token))
                origin.enqueue(MockResponse().setResponseCode(401).setBody(token))
                val streamFailure = runCatching { api.events("t").first() }.exceptionOrNull()!!
                assertEquals(httpErrorMessage(401), streamFailure.message)
                assertFalse(streamFailure.toString().contains(token))
            }
        }
    }
}
