package dev.codexpad

import dev.codexpad.model.workspaceNameError
import dev.codexpad.network.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class WorkspaceTest {
    @Test fun namesAreTrimmedAndTraversalAndControlCharactersRejected() {
        listOf("", " ", ".", "..", "../x", "/tmp/x", "a/b", "a\\b", "a..b", "a\nname",
            "a\u0000b", "a.", "_hidden", "a%2fb", "a;rm", "$(id)", "x".repeat(81)).forEach {
            assertNotNull("Expected invalid name: $it", workspaceNameError(it))
        }
        listOf("  Grüße 1  ", "projekt", "Test-Projekt_1.2", "日本語").forEach {
            assertNull("Expected valid name: $it", workspaceNameError(it))
        }
    }

    @Test fun workspaceMutationsUseExistingAuthenticatedTransportAndInspection() = runBlocking {
        MockWebServer().use { server ->
            val api = CodexPadApi(server.url("/").toString(), "private-token")
            server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
            api.createWorkspace("  Grüße 1  ")
            server.takeRequest().let {
                assertEquals("POST", it.method)
                assertEquals("/workspaces", it.path)
                assertEquals("Bearer private-token", it.getHeader("Authorization"))
                assertEquals("Grüße 1", JSONObject(it.body.readUtf8()).getString("name"))
            }
            server.enqueue(MockResponse().setBody("{}"))
            api.renameWorkspace("Grüße 1", "new")
            assertEquals("/workspaces/Gr%C3%BC%C3%9Fe%201/rename", server.takeRequest().path)
            server.enqueue(MockResponse().setBody("""{"files":2,"directories":1,"threads":0,"inspection":"snapshot"}"""))
            val inspection = api.inspectWorkspace("new")
            assertEquals("GET", server.takeRequest().method)
            assertEquals(2, inspection.files)
            server.enqueue(MockResponse().setBody("{}"))
            api.deleteWorkspace("new", inspection)
            server.takeRequest().let {
                assertEquals("/workspaces/new/delete", it.path)
                val payload = JSONObject(it.body.readUtf8())
                assertEquals("new", payload.getString("confirmation"))
                assertEquals("snapshot", payload.getString("inspection"))
            }
        }
    }

    @Test fun knownServerErrorsAreTranslatedWithoutEchoingArbitraryMessages() = runBlocking {
        MockWebServer().use { server ->
            val api = CodexPadApi(server.url("/").toString(), "private-token")
            for ((status, code, fragment) in listOf(
                Triple(400, "invalid_workspace_name", "Ungültiger"),
                Triple(409, "workspace_exists", "bereits vorhanden"),
                Triple(409, "workspace_has_threads", "gesperrt"),
                Triple(409, "workspace_changed", "geändert"),
                Triple(502, "unknown", "nicht verfügbar"))) {
                server.enqueue(MockResponse().setResponseCode(status)
                    .setBody(JSONObject().put("error", "private-token").put("code", code).toString()))
                val error = runCatching { api.renameWorkspace("old", "new") }.exceptionOrNull() as ApiException
                assertEquals(status, error.status)
                assertTrue(workspaceError(error).contains(fragment))
                assertFalse(workspaceError(error).contains("private-token"))
                server.takeRequest()
            }
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            assertTrue(runCatching { api.createWorkspace("lost-response") }.isFailure)
            server.takeRequest()
            assertNull(server.takeRequest(250, TimeUnit.MILLISECONDS))
        }
    }
}
