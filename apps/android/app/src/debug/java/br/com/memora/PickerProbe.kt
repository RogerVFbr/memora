package br.com.memora

import android.content.Context
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant
import javax.net.ssl.HttpsURLConnection

/** Sanitized message for UI; optional redirect fields are private evidence only. */
class ProbeHttpException(
    val status: Int,
    // Diagnostic data may contain a signed media URL. Persist only in ProbeStore; never print.
    val privateRedirectLocation: String? = null,
    val redirectHost: String? = null,
    val privateRedirectTrail: String? = null
) : Exception("Probe HTTP $status")

enum class ProbeMediaFailure { UNEXPECTED_MIME, EMPTY_RESPONSE, IMAGE_DECODE_FAILED }
class ProbeMediaException(val reason: ProbeMediaFailure) : Exception("Probe media validation failed: ${reason.name}")

/** Diagnostic limits, not product cache/FPS targets. No media bytes are persisted. */
internal object ProbePolicy {
    const val PHOTO_LIMIT = 8 * 1024 * 1024
    const val VIDEO_PREFIX = 64 * 1024
    const val JSON_LIMIT = 2 * 1024 * 1024
    const val MAX_PAGES = 32
    const val MAX_REDIRECTS = 3

    fun sessionId(value: String): String {
        require(value.matches(Regex("[A-Za-z0-9_-]{1,1024}"))) { "Invalid Picker session identifier" }
        return value
    }

    fun redirectUrl(current: String, location: String): String {
        require(location.isNotBlank() && location.length <= 16_384) { "Invalid media redirect" }
        val next = URI(current).resolve(location)
        val host = next.host.orEmpty().lowercase()
        require(next.scheme == "https" && next.rawUserInfo == null && next.port == -1 &&
            next.rawFragment == null &&
            (host == "googleusercontent.com" || host.endsWith(".googleusercontent.com"))) {
            "Media redirect origin rejected"
        }
        // Signed query parameters are allowed, but remain private in the encrypted snapshot.
        return next.toASCIIString()
    }

    fun mediaUrl(base: String, video: Boolean): String {
        val uri = URI(base)
        val host = uri.host.orEmpty().lowercase()
        require(uri.scheme == "https" && uri.rawUserInfo == null && uri.port == -1 &&
            uri.rawQuery == null && uri.rawFragment == null &&
            (host == "googleusercontent.com" || host.endsWith(".googleusercontent.com"))) {
            "Media origin rejected"
        }
        return base + if (video) "=dv" else "=w1280-h720"
    }
}

internal class ProbeRedirectGuard(original: String) {
    private val visited = mutableSetOf(original)
    var hops = 0
        private set
    fun follow(next: String) {
        check(hops < ProbePolicy.MAX_REDIRECTS && visited.add(next)) { "Media redirect loop or limit" }
        hops++
    }
}

internal class ProbePagingGuard {
    private val tokens = mutableSetOf<String>()
    private val ids = mutableSetOf<String>()
    var pages = 0
        private set
    var received = 0
        private set
    var duplicates = 0
        private set
    fun page(tokenUsed: String) {
        check(pages < ProbePolicy.MAX_PAGES && tokens.add(tokenUsed)) { "Pagination did not terminate" }
        pages++
    }
    fun item(id: String): Boolean {
        require(id.isNotBlank()) { "Missing media identifier" }
        received++
        return ids.add(id).also { if (!it) duplicates++ }
    }
}

/** Bounded complete reads fail instead of silently claiming success for truncated JSON/photos. */
internal fun readProbeBytes(input: InputStream, limit: Int): ByteArray {
    require(limit > 0)
    val output = ByteArrayOutputStream(minOf(limit, 32 * 1024))
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        check(!Thread.currentThread().isInterrupted) { "Probe cancelled" }
        val count = input.read(buffer, 0, minOf(buffer.size, limit - total + 1))
        if (count == -1) return output.toByteArray()
        total += count
        require(total <= limit) { "Response exceeds diagnostic limit" }
        output.write(buffer, 0, count)
    }
}

/** Native-only Picker probe. All identifiers/URLs/tokens stay in the encrypted bench snapshot. */
class PickerProbe(context: Context) {
    private val store = ProbeStore(context)
    private val api = "https://photospicker.googleapis.com/v1"

    private fun now() = Instant.now().toString()
    private fun checkActive() { check(!Thread.currentThread().isInterrupted) { "Probe cancelled" } }
    private fun encoded(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun open(url: String, token: String?): HttpsURLConnection {
        checkActive()
        return (URI(url).toURL().openConnection() as HttpsURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 15_000
            useCaches = false
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Cache-Control", "no-cache, no-store")
            setRequestProperty("Pragma", "no-cache")
        }
    }

    private fun json(url: String, token: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        // API requests are constructed internally; never accept a caller-provided endpoint.
        require(url.startsWith("$api/") && URI(url).host == "photospicker.googleapis.com")
        val connection = open(url, token)
        try {
            connection.requestMethod = method
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status !in 200..299) throw ProbeHttpException(status)
            val bytes = connection.inputStream.use { readProbeBytes(it, ProbePolicy.JSON_LIMIT) }
            val response = if (bytes.isEmpty()) JSONObject() else JSONObject(String(bytes, Charsets.UTF_8))
            return response.put("_probeHttp", httpEvidence(connection, status))
        } finally { connection.disconnect() }
    }

    fun create(token: String, expiresInSeconds: Long): JSONObject {
        require(token.isNotBlank() && expiresInSeconds > 0)
        // Never overwrite the only reference to an existing session awaiting G4/cleanup.
        check(store.read() == null) { "Existing probe must be resumed or cleaned first" }
        val issuedAt = Instant.now()
        val session = json("$api/sessions", token, "POST",
            JSONObject().put("pickingConfig", JSONObject().put("maxItemCount", "12")))
        val id = ProbePolicy.sessionId(session.getString("id"))
        val snapshot = JSONObject().put("version", 1).put("token", token)
            .put("tokenIssuedAt", issuedAt.toString())
            .put("tokenExpiresAt", issuedAt.plusSeconds(expiresInSeconds).toString())
            .put("sessionId", id).put("session", session).put("sessionCreatedAt", now())
            .put("initialSession", JSONObject(session.toString()))
            .put("initialExpireTimePresent", session.has("expireTime"))
            .put("initialExpireTime", if (session.has("expireTime")) session.get("expireTime") else JSONObject.NULL)
            .put("polls", JSONArray()).put("catalog", JSONArray()).put("initialCatalog", JSONArray())
            .put("listPages", JSONArray()).put("mediaChecks", JSONArray())
        try { store.save(snapshot) }
        catch (e: Exception) {
            // A created session must not become untracked just because private persistence failed.
            try { json("$api/sessions/$id", token, "DELETE") } catch (_: Exception) { /* caller sees failure */ }
            throw e
        }
        return session
    }

    fun poll(token: String, sessionId: String): JSONObject {
        val id = ProbePolicy.sessionId(sessionId)
        val session = json("$api/sessions/$id", token)
        val observedAt = now()
        store.update {
            check(it.getString("sessionId") == id) { "Probe session mismatch" }
            it.put("session", session).put("lastPollAt", observedAt)
            val polls = it.getJSONArray("polls")
            check(polls.length() < 512) { "Polling history limit reached" }
            polls.put(JSONObject().put("at", observedAt)
                .put("mediaItemsSet", session.optBoolean("mediaItemsSet"))
                .put("expireTimePresent", session.has("expireTime"))
                .put("expireTime", if (session.has("expireTime")) session.get("expireTime") else JSONObject.NULL)
                .put("pollingConfig", session.optJSONObject("pollingConfig") ?: JSONObject())
                .put("http", session.getJSONObject("_probeHttp")))
        }
        return session
    }

    fun inspect(token: String, sessionId: String, onProgress: (String) -> Unit): String {
        val id = ProbePolicy.sessionId(sessionId)
        val snapshot = store.read() ?: error("No private probe snapshot")
        check(snapshot.getString("sessionId") == id && snapshot.getJSONObject("session").optBoolean("mediaItemsSet")) {
            "Picker selection is not ready"
        }
        val guard = ProbePagingGuard()
        val catalog = JSONArray()
        val initial = snapshot.optJSONArray("initialCatalog") ?: JSONArray()
        val initialById = linkedMapOf<String, JSONObject>()
        for (index in 0 until initial.length()) {
            val old = initial.getJSONObject(index)
            initialById[old.getString("id")] = old
        }
        val pages = JSONArray()
        var pageToken = ""
        do {
            checkActive()
            guard.page(pageToken)
            val listedAt = now()
            val suffix = if (pageToken.isEmpty()) "" else "&pageToken=${encoded(pageToken)}"
            val page = json("$api/mediaItems?sessionId=${encoded(id)}&pageSize=5$suffix", token)
            val items = page.optJSONArray("mediaItems") ?: JSONArray()
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                if (guard.item(item.optString("id"))) {
                    check(catalog.length() < 12) { "Selection exceeds diagnostic catalog limit" }
                    // Preserve the first URL independently from current URLs after future relists.
                    val itemId = item.getString("id")
                    val first = initialById[itemId] ?: JSONObject(item.toString())
                        .put("firstReceivedAt", listedAt)
                        .put("firstBaseUrl", item.optJSONObject("mediaFile")?.optString("baseUrl").orEmpty())
                        .also { initialById[itemId] = it; initial.put(it) }
                    catalog.put(JSONObject(item.toString()).put("listObtainedAt", listedAt)
                        .put("firstReceivedAt", first.getString("firstReceivedAt"))
                        .put("firstBaseUrl", first.getString("firstBaseUrl")))
                }
            }
            pages.put(JSONObject().put("at", listedAt).put("count", items.length())
                .put("hasNextPage", page.optString("nextPageToken").isNotEmpty())
                .put("http", page.getJSONObject("_probeHttp")))
            store.update {
                it.put("catalog", catalog).put("initialCatalog", initial)
                    .put("listPages", pages).put("listedAt", now())
                    .put("receivedCount", guard.received).put("duplicateCount", guard.duplicates)
            }
            onProgress("Listagem: ${guard.pages} páginas, ${catalog.length()} itens únicos.")
            pageToken = page.optString("nextPageToken")
        } while (pageToken.isNotEmpty())
        store.update {
            it.put("catalog", catalog).put("initialCatalog", initial)
                    .put("listPages", pages).put("listedAt", now())
                .put("receivedCount", guard.received).put("duplicateCount", guard.duplicates)
                .put("mediaChecks", JSONArray())
        }

        var photos = 0; var videos = 0; var photosOk = 0; var videosPartial = 0
        var videoNotReady = 0; var unknown = 0; var missingMetadata = 0; var failures = 0
        var totalBytes = 0L
        val failureStatuses = linkedMapOf<Int, Int>()
        val failureCategories = linkedMapOf<String, Int>()
        val blockedRedirectHosts = linkedSetOf<String>()
        for (index in 0 until catalog.length()) {
            checkActive()
            val item = catalog.getJSONObject(index)
            val type = item.optString("type")
            if (type == "PHOTO") photos++ else if (type == "VIDEO") videos++ else unknown++
            val file = item.optJSONObject("mediaFile")
            val metadata = file?.optJSONObject("mediaFileMetadata")
            val mime = file?.optString("mimeType").orEmpty()
            val missing = metadata == null || !metadata.has("width") || !metadata.has("height") ||
                item.optString("createTime").isEmpty() || mime.isEmpty()
            if (missing) missingMetadata++
            val result = JSONObject().put("id", item.getString("id")).put("type", type)
                .put("startedAt", now()).put("missingMetadata", missing)
            try {
                if (type != "PHOTO" && type != "VIDEO") {
                    result.put("outcome", "UNSUPPORTED_TYPE")
                } else if (type == "VIDEO" && metadata?.optJSONObject("videoMetadata")?.optString("processingStatus") != "READY") {
                    videoNotReady++
                    result.put("outcome", "VIDEO_NOT_READY")
                        .put("processingStatus", metadata?.optJSONObject("videoMetadata")?.optString("processingStatus") ?: "MISSING")
                } else {
                    val video = type == "VIDEO"
                    val base = file?.optString("baseUrl").orEmpty()
                    val checked = media(ProbePolicy.mediaUrl(base, video), token, video)
                    val bytes = checked.getLong("bytes")
                    totalBytes += bytes
                    if (video) videosPartial++ else photosOk++
                    result.put("outcome", if (video) "VIDEO_PREFIX_ONLY" else "PHOTO_COMPLETE")
                        .put("retrieval", checked)
                }
            } catch (e: Exception) {
                if (Thread.currentThread().isInterrupted) throw e
                failures++
                // Exception messages from URL/HTTP layers can include private identifiers.
                result.put("outcome", "FETCH_FAILED").put("category", e.javaClass.simpleName)
                val category = if (e is ProbeMediaException) {
                    result.put("reason", e.reason.name)
                    "${e.javaClass.simpleName}/${e.reason.name}"
                } else e.javaClass.simpleName
                failureCategories[category] = (failureCategories[category] ?: 0) + 1
                if (e is ProbeHttpException) {
                    result.put("httpStatus", e.status)
                    failureStatuses[e.status] = (failureStatuses[e.status] ?: 0) + 1
                    if (e.privateRedirectLocation != null) result.put("redirectLocation", e.privateRedirectLocation)
                    if (e.redirectHost != null) {
                        result.put("redirectHost", e.redirectHost)
                        blockedRedirectHosts.add(e.redirectHost)
                    }
                    if (e.privateRedirectTrail != null) result.put("redirects", JSONArray(e.privateRedirectTrail))
                }
            }
            result.put("finishedAt", now())
            store.update { it.getJSONArray("mediaChecks").put(result) }
            onProgress("Verificação ${index + 1}/${catalog.length()}: fotos $photosOk, vídeos parciais $videosPartial, falhas $failures.")
        }
        val accepted = photos == 10 && videos == 2 && unknown == 0 && photosOk == 10 &&
            videosPartial == 2 && failures == 0 && videoNotReady == 0 && guard.duplicates == 0
        val summary = "${if (accepted) "AMOSTRA G3 VERIFICADA" else "AMOSTRA G3 INCOMPLETA"}\n" +
            "$photos fotos ($photosOk recuperadas), $videos vídeos ($videosPartial prefixos de até 64 KiB).\n" +
            "${guard.pages} páginas; ${guard.duplicates} duplicatas; $missingMetadata itens com metadados ausentes.\n" +
            "$totalBytes bytes lidos; $failures falhas; $videoNotReady vídeos não prontos.\n" +
            "Falhas HTTP: ${failureStatuses.entries.joinToString { "${it.key} (${it.value})" }.ifEmpty { "nenhuma" }}.\n" +
            "Categorias de falha: ${failureCategories.entries.joinToString { "${it.key} (${it.value})" }.ifEmpty { "nenhuma" }}.\n" +
            "Destinos de redirect bloqueados: ${blockedRedirectHosts.joinToString().ifEmpty { "nenhum" }}.\n" +
            "Vídeos parciais: não comprova arquivo completo, reprodução ou codec. Nenhum byte de mídia salvo."
        store.update {
            it.put("summary", summary).put("inspectionFinishedAt", now()).put("sampleVerified", accepted)
            if (!it.has("firstMediaChecks")) it.put("firstMediaChecks", JSONArray(it.getJSONArray("mediaChecks").toString()))
        }
        return summary
    }

    private fun media(url: String, token: String, video: Boolean): JSONObject {
        var currentUrl = url
        val guard = ProbeRedirectGuard(url)
        val redirects = JSONArray()
        while (true) {
            checkActive()
            // A redirected URL is a separate capability; never forward the OAuth bearer.
            val connection = open(currentUrl, if (guard.hops == 0) token else null)
            if (video) connection.setRequestProperty("Range", "bytes=0-${ProbePolicy.VIDEO_PREFIX - 1}")
            try {
                val status = connection.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location").orEmpty()
                    val host = try { URI(currentUrl).resolve(location).host?.lowercase() } catch (_: Exception) { null }
                    val next = try {
                        ProbePolicy.redirectUrl(currentUrl, location).also { guard.follow(it) }
                    } catch (_: Exception) {
                        throw ProbeHttpException(status, location.takeIf { it.isNotBlank() }, host, redirects.toString())
                    }
                    redirects.put(JSONObject().put("fromHost", URI(currentUrl).host)
                        .put("location", location).put("toHost", URI(next).host)
                        .put("authorizationForwarded", false).put("http", httpEvidence(connection, status)))
                    currentUrl = next
                    continue
                }
                if (status !in 200..299) throw ProbeHttpException(status, privateRedirectTrail = redirects.toString())
                return readMedia(connection, status, video).put("redirects", redirects)
                    .put("finalHost", URI(currentUrl).host)
                    .put("redirectCount", guard.hops)
            } finally { connection.disconnect() }
        }
    }

    private fun readMedia(connection: HttpsURLConnection, status: Int, video: Boolean): JSONObject {
        val responseMime = connection.contentType.orEmpty().substringBefore(';').lowercase()
        val acceptedMime = if (video) responseMime.startsWith("video/") || responseMime == "application/octet-stream"
            else responseMime.startsWith("image/")
        if (!acceptedMime) throw ProbeMediaException(ProbeMediaFailure.UNEXPECTED_MIME)
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
        var reachedEof = false
        var responseWidth: Int? = null
        var responseHeight: Int? = null
        if (video) {
            connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                while (count < ProbePolicy.VIDEO_PREFIX) {
                    checkActive()
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), ProbePolicy.VIDEO_PREFIX - count).toInt())
                    if (read == -1) { reachedEof = true; break }
                    digest.update(buffer, 0, read); count += read
                }
            }
        } else {
            val bytes = connection.inputStream.use { readProbeBytes(it, ProbePolicy.PHOTO_LIMIT) }
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
                    throw ProbeMediaException(ProbeMediaFailure.IMAGE_DECODE_FAILED)
                responseWidth = bounds.outWidth; responseHeight = bounds.outHeight
                count = bytes.size.toLong(); digest.update(bytes); reachedEof = true
            } finally { bytes.fill(0) }
        }
        if (count <= 0) throw ProbeMediaException(ProbeMediaFailure.EMPTY_RESPONSE)
        return JSONObject().put("at", now()).put("status", status).put("bytes", count)
            .put("sha256", digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) })
            .put("responseMimeType", responseMime)
            .put("responseWidth", responseWidth ?: JSONObject.NULL)
            .put("responseHeight", responseHeight ?: JSONObject.NULL)
            .put("contentLength", connection.contentLengthLong)
            .put("rangeRequested", video).put("reachedEof", reachedEof)
            .put("completeFileProven", !video).put("fromNetwork", true)
            .put("http", httpEvidence(connection, status))
    }


    private fun httpEvidence(connection: HttpsURLConnection, status: Int) = JSONObject()
        .put("observedAt", now()).put("status", status)
        .put("date", connection.getHeaderField("Date") ?: JSONObject.NULL)
        .put("age", connection.getHeaderField("Age") ?: JSONObject.NULL)
        .put("cacheControl", connection.getHeaderField("Cache-Control") ?: JSONObject.NULL)
        .put("contentRange", connection.getHeaderField("Content-Range") ?: JSONObject.NULL)

    /** Explicit cleanup after G4, cancellation, or discarded sample; success snapshots stay until then. */
    fun cleanup(token: String, sessionId: String): Boolean {
        val id = ProbePolicy.sessionId(sessionId)
        return try {
            val saved = store.read()
            if (saved != null && saved.getString("sessionId") != id) return false
            json("$api/sessions/$id", token, "DELETE")
            store.clear()
            true
        } catch (_: Exception) {
            // HTTP 404 can also mean "not owned by this account"; without account-binding
            // evidence it does not prove this saved session was removed. Keep the snapshot.
            false
        }
    }
}
