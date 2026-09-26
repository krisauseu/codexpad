package dev.codexpad.network

import dev.codexpad.model.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.coroutines.resumeWithException

class ApiException(val status: Int, message: String) : IOException(message)
data class UploadImage(val name: String, val mimeType: String, val bytes: ByteArray)
data class UploadText(val name: String, val mimeType: String, val bytes: ByteArray)

interface CodexPadService {
    suspend fun models(): List<CatalogModel>
    suspend fun health(): String
    suspend fun workspaces(): List<Workspace>
    suspend fun threads(workspaceId: String): List<CodexThread>
    suspend fun createThread(workspaceId: String): CodexThread
    suspend fun thread(threadId: String): CodexThread
    suspend fun history(threadId: String): CodexThread
    suspend fun startTurn(threadId: String, message: String, model: String? = null, effort: String? = null): Turn
    suspend fun compactThread(threadId: String)
    suspend fun interruptTurn(threadId: String, turnId: String)
    fun events(threadId: String): Flow<SseFrame>
}

class CodexPadApi(baseUrl: String, private val token: String) : CodexPadService {
    private val base = baseUrl.toHttpUrl()
    // Also disable transport retries and redirects: a POST may already have taken effect.
    private val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(150, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS).build()
    private val streaming = client.newBuilder().callTimeout(0, TimeUnit.SECONDS).build()

    private fun request(vararg path: String, body: JSONObject? = null): Request {
        val url = base.newBuilder().apply { path.forEach(::addPathSegment) }.build()
        return Request.Builder().url(url).apply {
            if (token.isNotEmpty()) header("Authorization", "Bearer $token")
            if (body != null) post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        }.build()
    }

    private suspend fun json(request: Request): JSONObject = withContext(Dispatchers.IO) { client.newCall(request).await().use {
        val raw = it.body?.string().orEmpty()
        if (!it.isSuccessful) throw ApiException(it.code,
            httpErrorMessage(it.code))
        JSONObject(raw)
    } }

    override suspend fun models() = json(request("models")).getJSONArray("models").objects().map(CatalogModel::parse)
    override suspend fun health() = json(request("health")).getString("status")
    override suspend fun workspaces() = json(request("workspaces")).getJSONArray("workspaces").objects()
        .map { Workspace(it.getString("id"), it.getString("name")) }
    override suspend fun threads(workspaceId: String) = json(request("workspaces", workspaceId, "threads"))
        .getJSONArray("threads").objects().map(Wire::thread)
    override suspend fun createThread(workspaceId: String) = Wire.threadEnvelope(
        json(request("workspaces", workspaceId, "threads", body = JSONObject())))
    override suspend fun thread(threadId: String) = Wire.threadEnvelope(json(request("threads", threadId)))
    override suspend fun history(threadId: String) = Wire.threadEnvelope(json(request("threads", threadId, "history")))
    override suspend fun startTurn(threadId: String, message: String, model: String?, effort: String?) =
        startTurn(threadId, message, model, effort, emptyList(), emptyList())

    suspend fun startTurn(threadId: String, message: String, model: String?, effort: String?,
        images: List<UploadImage>, files: List<UploadText> = emptyList()): Turn {
        val turnRequest = if (images.isEmpty() && files.isEmpty()) request("threads", threadId, "turns",
            body = JSONObject().put("message", message).apply {
                model?.let { put("model", it) }; effort?.let { put("effort", it) }
            }) else {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("message", message).apply {
                    model?.let { addFormDataPart("model", it) }
                    effort?.let { addFormDataPart("effort", it) }
                    images.forEach { image -> addFormDataPart("image", image.name,
                        image.bytes.toRequestBody(image.mimeType.toMediaType())) }
                    files.forEach { file -> addFormDataPart("file", file.name,
                        file.bytes.toRequestBody(file.mimeType.toMediaType())) }
                }.build()
            request("threads", threadId, "turns").newBuilder().post(body).build()
        }
        return Wire.turn(json(turnRequest).getJSONObject("turn"))
    }

    suspend fun downloadArtifact(threadId: String, artifact: Artifact, target: java.io.File) = withContext(Dispatchers.IO) {
        try {
            client.newCall(request("threads", threadId, "artifacts", artifact.id)).await().use { response ->
                if (!response.isSuccessful) throw ApiException(response.code, httpErrorMessage(response.code))
                val body = response.body ?: throw IOException("Leere Datei")
                val limit = 64L * 1024 * 1024
                if (body.contentLength() !in 1..limit || response.header("Content-Type") != artifact.mimeType)
                    throw IOException("Ungültige Datei")
                body.byteStream().use { input -> target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > limit) throw IOException("Datei zu groß")
                        output.write(buffer, 0, count)
                    }
                    if (total != body.contentLength()) throw IOException("Unvollständige Datei")
                } }
            }
        } catch (error: Exception) { target.delete(); throw error }
    }

    override suspend fun compactThread(threadId: String) {
        json(request("threads", threadId, "compact", body = JSONObject()))
    }

    override suspend fun interruptTurn(threadId: String, turnId: String) {
        json(request("threads", threadId, "turns", turnId, "interrupt", body = JSONObject()))
    }

    override fun events(threadId: String): Flow<SseFrame> = callbackFlow {
        val call = streaming.newCall(request("threads", threadId, "events").newBuilder()
            .header("Accept", "text/event-stream").build())
        val reader = launch(Dispatchers.IO) {
            try {
                call.await().use { response ->
                    if (!response.isSuccessful) throw ApiException(response.code, httpErrorMessage(response.code))
                    if (response.header("Content-Type")?.startsWith("text/event-stream") != true)
                        throw IOException("Server lieferte keinen SSE-Stream")
                    val source = response.body?.source() ?: throw IOException("Leerer SSE-Stream")
                    // Setup can take up to two server RPC timeouts. Once connected, expect 15s heartbeats.
                    source.timeout().timeout(45, TimeUnit.SECONDS)
                    val parser = SseParser()
                    while (true) {
                        val line = source.readUtf8Line() ?: throw IOException("SSE-Verbindung geschlossen")
                        parser.line(line)?.let { send(it) }
                    }
                }
            } catch (error: Exception) { close(error) }
        }
        awaitClose { call.cancel(); reader.cancel() }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, value, _ -> value.close() }
        }
    })
}

// Do not surface arbitrary response/exception text: a remote endpoint could echo credentials.
fun httpErrorMessage(status: Int): String = when (status) {
    401, 403 -> "Zugriff abgelehnt. Zugriffstoken in den Einstellungen prüfen."
    404 -> "Serverroute oder Eintrag nicht gefunden. Server-URL prüfen."
    502, 503, 504 -> "Codex-Server derzeit nicht verfügbar. Später erneut versuchen."
    else -> "Server antwortet mit HTTP $status."
}

fun connectionError(error: Exception): String = when (error) {
    is ApiException -> httpErrorMessage(error.status)
    is javax.net.ssl.SSLException -> "TLS-Verbindung fehlgeschlagen. Zertifikat und Server-URL prüfen."
    is java.net.UnknownHostException -> "Servername nicht gefunden. URL, DNS und Internetverbindung prüfen."
    is java.net.ConnectException -> "Server nicht erreichbar. Adresse und Serverbetrieb prüfen."
    is java.net.SocketTimeoutException, is kotlinx.coroutines.TimeoutCancellationException -> "Zeitüberschreitung. Internetverbindung und Server prüfen."
    else -> "Verbindung fehlgeschlagen. Server-URL und Internetverbindung prüfen."
}
