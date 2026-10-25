package br.com.memora

import android.content.Context
import android.graphics.BitmapFactory
import android.os.SystemClock
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
    val privateRedirectTrail: String? = null,
    val privateHttpEvidence: String? = null
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
    @Volatile private var expiryCancellation: ExpiryProbeCancellation? = null
    private fun isCancelled() = Thread.currentThread().isInterrupted || expiryCancellation?.isCancelled() == true
    private fun checkActive() { check(!isCancelled()) { "Probe cancelled" } }

    /** UI signals this before cancelling its Future; native I/O may clear interruption. */
    fun cancelExpiryCheck() { expiryCancellation?.cancel() }
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

    private var expiryRun: Int? = null
    private var expiryResponseHttp: JSONObject? = null

    private fun json(url: String, token: String, method: String = "GET", body: JSONObject? = null): JSONObject =
        observedRequest(if (url.contains("/mediaItems?")) "LIST" else "SESSION_$method") {
            jsonRequest(url, token, method, body)
        }

    private fun jsonRequest(url: String, token: String, method: String, body: JSONObject?): JSONObject {
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
            if (expiryRun != null) {
                    expiryResponseHttp = httpEvidence(connection, status)
                }
            if (status !in 200..299) throw ProbeHttpException(status, privateHttpEvidence = httpEvidence(connection, status).toString())
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
                if (isCancelled()) throw e
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

    private fun media(url: String, token: String, video: Boolean): JSONObject =
        observedRequest(if (video) "VIDEO_PREFIX" else "PHOTO") { mediaRequest(url, token, video) }

    private fun mediaRequest(url: String, token: String, video: Boolean): JSONObject {
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
                if (expiryRun != null) {
                    expiryResponseHttp = httpEvidence(connection, status)
                }
                if (status in setOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location").orEmpty()
                    val host = try { URI(currentUrl).resolve(location).host?.lowercase() } catch (_: Exception) { null }
                    val next = try {
                        ProbePolicy.redirectUrl(currentUrl, location).also { guard.follow(it) }
                    } catch (_: Exception) {
                        throw ProbeHttpException(status, location.takeIf { it.isNotBlank() }, host, redirects.toString(), httpEvidence(connection, status).toString())
                    }
                    redirects.put(JSONObject().put("fromHost", URI(currentUrl).host)
                        .put("location", location).put("toHost", URI(next).host)
                        .put("authorizationForwarded", false).put("http", httpEvidence(connection, status)))
                    currentUrl = next
                    continue
                }
                if (status !in 200..299) throw ProbeHttpException(status, privateRedirectTrail = redirects.toString(), privateHttpEvidence = httpEvidence(connection, status).toString())
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
                checkActive()
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

    /** Credential replacement is explicit and never creates/deletes a Picker session. */
    fun replaceAuthorization(response: JSONObject, receivedAt: Instant): String =
        storeAuthorization(response, receivedAt, "authorization_code")

    private fun storeAuthorization(response: JSONObject, receivedAt: Instant, origin: String,
        refreshRun: Int? = null, expectedVersion: Int? = null): String {
        checkActive()
        val token = response.getString("access_token")
        val seconds = response.getLong("expires_in")
        require(token.isNotBlank() && seconds > 0 && seconds <= 31_536_000)
        if (response.has("scope")) require(response.getString("scope").split(' ').contains(
            "https://www.googleapis.com/auth/photospicker.mediaitems.readonly")) { "Picker scope not granted" }
        val type = response.optString("token_type")
        require(type.equals("Bearer", ignoreCase = true)) { "Unexpected OAuth token type" }
        store.update { saved ->
            preserveInitialAuthorization(saved)
            if (expectedVersion != null) check(saved.optInt("credentialVersion", 0) == expectedVersion) { "Credential changed during refresh" }
            val history = saved.optJSONArray("authorizationHistory") ?: JSONArray()
            val version = ExpiryProbePolicy.nextIndex(history.length(), ExpiryProbePolicy.MAX_AUTHORIZATIONS) + 1
            val credential = JSONObject().put("version", version).put("token", token).put("origin", origin)
                .put("receivedAt", receivedAt.toString())
                .put("expiresAt", receivedAt.plusSeconds(seconds).toString())
                .put("tokenType", type).put("association", "UNVERIFIED")
                .put("grantedScopesPresent", response.has("scope"))
                .put("refreshTokenPresent", response.optString("refresh_token").isNotBlank())
            if (response.has("scope")) credential.put("grantedScopes", response.getString("scope"))
            if (response.optString("refresh_token").isNotBlank()) credential.put("refreshToken", response.getString("refresh_token"))
            if (response.has("refreshExpiresAt")) credential.put("refreshExpiresAt", response.getString("refreshExpiresAt"))
            else if (response.has("refresh_token_expires_in")) credential.put("refreshExpiresAt",
                receivedAt.plusSeconds(response.getLong("refresh_token_expires_in")).toString())
            if (expectedVersion != null) credential.put("sourceCredentialVersion", expectedVersion)
            history.put(credential)
            saved.put("authorizationHistory", history).put("credentialVersion", version)
                .put("token", token).put("tokenReceivedAt", receivedAt.toString())
                .put("tokenIssuedAt", receivedAt.toString()).put("tokenExpiresAt", receivedAt.plusSeconds(seconds).toString())
                .put("tokenType", type).put("tokenTypePresent", true)
                .put("grantedScopesPresent", response.has("scope"))
                .put("refreshTokenPresent", credential.getBoolean("refreshTokenPresent"))
                .put("credentialAssociation", "UNVERIFIED")
            saved.remove("grantedScopes"); saved.remove("refreshToken"); saved.remove("refreshExpiresAt")
            if (credential.has("grantedScopes")) saved.put("grantedScopes", credential.get("grantedScopes"))
            if (credential.has("refreshToken")) saved.put("refreshToken", credential.get("refreshToken"))
            if (credential.has("refreshExpiresAt")) saved.put("refreshExpiresAt", credential.get("refreshExpiresAt"))
            val summary = if (origin == "refresh_token")
                "Refresh sem navegador concluído; credencial $version gravada. Sessão e URLs iniciais preservadas; consulte a mesma sessão para comprovar acesso."
                else "OAuth renovado; sessão e URLs iniciais preservadas. Associação à sessão ainda não comprovada."
            if (refreshRun != null) saved.getJSONArray("refreshChecks").getJSONObject(refreshRun)
                .put("outcome", "SUCCESS").put("finishedAt", now()).put("summary", summary).put("credentialVersion", version)
                .remove("candidateCredential")
            saved.put("lastExpirySummary", summary)
            checkActive()
        }
        return "OAuth renovado; nenhuma sessão criada ou removida. Consulte a mesma sessão para verificar acesso."
    }

    /** Explicit, bounded refresh of the current grant. No browser, scope expansion, or session creation. */
    fun refreshAuthorization(clientId: String, isCancelled: () -> Boolean = { false }): String {
        require(clientId.matches(Regex("[A-Za-z0-9_-]+\\.apps\\.googleusercontent\\.com"))) { "Invalid OAuth client identifier" }
        val signal = ExpiryProbeCancellation(isCancelled)
        check(!signal.isCancelled() && !Thread.currentThread().isInterrupted) { "Refresh cancelled before start" }
        check(expiryRun == null && expiryCancellation == null) { "Another probe action is active" }
        val saved = store.update { preserveInitialAuthorization(it) }
        val checks = saved.optJSONArray("refreshChecks") ?: JSONArray()
        val run = ExpiryProbePolicy.nextIndex(checks.length(), RefreshProbePolicy.MAX_CHECKS)
        val sourceVersion = saved.optInt("credentialVersion", 0)
        store.update { checks.put(JSONObject().put("startedAt", now()).put("outcome", "STARTED")
            .put("sourceCredentialVersion", sourceVersion).put("origin", "refresh_token"))
            it.put("refreshChecks", checks) }
        expiryCancellation = signal
        val started = SystemClock.elapsedRealtime()
        val deadline = started + 60_000L
        var connection: HttpsURLConnection? = null
        var http: JSONObject? = null
        var oauthError: String? = null
        var committed = false
        fun active() { checkActive(); check(SystemClock.elapsedRealtime() < deadline) { "Refresh time budget reached" } }
        try {
            active()
            ExpiryProbePolicy.nextIndex(saved.optJSONArray("authorizationHistory")?.length() ?: 0, ExpiryProbePolicy.MAX_AUTHORIZATIONS)
            val refresh = saved.optString("refreshToken")
            if (refresh.isBlank()) {
                val summary = "Refresh indisponível: a credencial atual não contém refresh token. Nenhuma rede ou autorização automática foi iniciada."
                persistWithoutInterrupt { store.update { it.getJSONArray("refreshChecks").getJSONObject(run)
                    .put("outcome", "NO_REFRESH_TOKEN").put("finishedAt", now()).put("durationMs", SystemClock.elapsedRealtime() - started)
                    .put("summary", summary); it.put("lastExpirySummary", summary) } }
                return summary
            }
            // Intent is already durable. The refresh token is sent only in the native HTTPS body.
            connection = open("https://oauth2.googleapis.com/token", null).apply {
                requestMethod = "POST"; doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            val body = OAuthTransaction.form(mapOf("client_id" to clientId, "refresh_token" to refresh,
                "grant_type" to "refresh_token")).toByteArray(Charsets.UTF_8)
            try { connection.outputStream.use { it.write(body) } } finally { body.fill(0) }
            val status = connection.responseCode
            http = httpEvidence(connection, status)
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { readRefreshBody(it, ::active) } ?: byteArrayOf()
            val receivedAt = Instant.now()
            val response = try { if (bytes.isEmpty()) JSONObject() else JSONObject(String(bytes, Charsets.UTF_8)) }
                finally { bytes.fill(0) }
            if (status !in 200..299) {
                oauthError = RefreshProbePolicy.safeError(response.optString("error"))
                throw ProbeHttpException(status, privateHttpEvidence = http.toString())
            }
            // A returned rotated credential must remain recoverable even if cancellation arrives
            // after the response. Retain it privately without activating it until the atomic commit.
            persistWithoutInterrupt { store.update { snapshot ->
                val record = snapshot.getJSONArray("refreshChecks").getJSONObject(run)
                val candidate = JSONObject().put("receivedAt", receivedAt.toString())
                listOf("access_token", "expires_in", "token_type", "scope", "refresh_token", "refresh_token_expires_in").forEach {
                    if (response.has(it)) candidate.put(it, response.get(it))
                }
                record.put("candidateCredential", candidate).put("http", http)
                    .put("durationMs", SystemClock.elapsedRealtime() - started)
                    .put("refreshTokenReturned", response.has("refresh_token")).put("scopeReturned", response.has("scope"))
            } }
            active()
            fun fields(value: JSONObject, mapping: Map<String, String>): Map<String, String> = mapping.mapNotNull { (from, to) ->
                if (value.has(from)) to to value.get(from).toString() else null
            }.toMap()
            val previous = fields(saved, mapOf("refreshToken" to "refresh_token", "grantedScopes" to "scope", "refreshExpiresAt" to "refreshExpiresAt"))
            val returned = fields(response, listOf("access_token", "expires_in", "token_type", "scope", "refresh_token", "refresh_token_expires_in").associateWith { it })
            val merged = RefreshProbePolicy.merge(previous, returned, receivedAt)
            store.update { it.getJSONArray("refreshChecks").getJSONObject(run).put("http", http)
                .put("durationMs", SystemClock.elapsedRealtime() - started)
                .put("refreshTokenReturned", response.has("refresh_token")).put("scopeReturned", response.has("scope")) }
            // Credentials and SUCCESS are committed together, preventing a false failure after a successful save.
            storeAuthorization(JSONObject(merged as Map<*, *>), receivedAt, "refresh_token", run, sourceVersion)
            committed = true
            return store.read()!!.getString("lastExpirySummary")
        } catch (error: Exception) {
            if (committed) throw error
            val cancelled = this.isCancelled()
            val status = if (http != null) " HTTP ${http.getInt("status")}." else ""
            val detail = oauthError?.let { " OAuth $it." }.orEmpty()
            val summary = "Refresh ${if (cancelled) "cancelado" else "não concluído"}.$status$detail Credencial anterior e sessão preservadas; sem nova seleção."
            persistWithoutInterrupt { store.update { it.getJSONArray("refreshChecks").getJSONObject(run)
                .put("outcome", if (cancelled) "CANCELLED" else "FAILED").put("finishedAt", now())
                .put("durationMs", SystemClock.elapsedRealtime() - started).put("http", http ?: JSONObject.NULL)
                .put("oauthError", oauthError ?: JSONObject.NULL).put("category", error.javaClass.simpleName).put("summary", summary)
                it.put("lastExpirySummary", summary) } }
            if (cancelled) throw error
            return summary
        } finally { connection?.disconnect(); expiryCancellation = null }
    }

    private fun preserveInitialAuthorization(saved: JSONObject) {
        if (saved.has("initialAuthorization")) return
        val initial = JSONObject().put("version", saved.optInt("credentialVersion", 0))
        mapOf("token" to "token", "tokenReceivedAt" to "receivedAt", "tokenIssuedAt" to "issuedAt",
            "tokenExpiresAt" to "expiresAt", "tokenType" to "tokenType", "grantedScopes" to "grantedScopes",
            "refreshToken" to "refreshToken", "refreshExpiresAt" to "refreshExpiresAt").forEach { (from, to) -> if (saved.has(from)) initial.put(to, saved.get(from)) }
        saved.put("initialAuthorization", initial)
    }

    /** Each action is read-only remotely. Its intent is durable before any HTTP request. */
    fun runExpiryCheck(operation: String, isCancelled: () -> Boolean = { false }, onProgress: (String) -> Unit): String {
        // The UI-owned signal exists before worker submission, so pre-start cancellation is retained.
        val cancellation = ExpiryProbeCancellation(isCancelled)
        check(!cancellation.isCancelled() && !Thread.currentThread().isInterrupted) { "Probe cancelled before start" }
        val op = ExpiryProbePolicy.operation(operation)
        check(expiryRun == null) { "Expiry action already active" }
        val started = Instant.now()
        val saved = store.update { preserveInitialAuthorization(it) }
        val initialCredential = saved.getJSONObject("initialAuthorization")
        val originalToken = op == "initial-token-session"
        val token = if (originalToken) initialCredential.getString("token") else saved.getString("token")
        val expiry = if (originalToken) initialCredential.optString("expiresAt") else saved.optString("tokenExpiresAt")
        val checks = saved.optJSONArray("expiryChecks") ?: JSONArray()
        val run = ExpiryProbePolicy.nextIndex(checks.length(), ExpiryProbePolicy.MAX_RUNS)
        val entry = JSONObject().put("operation", op).put("startedAt", started.toString())
            .put("outcome", "STARTED").put("requests", JSONArray())
            .put("credentialVersion", if (originalToken) initialCredential.optInt("version", 0) else saved.optInt("credentialVersion", 0))
            .put("tokenExpectedPhase", ExpiryProbePolicy.phase(started, expiry))
            .put("sessionExpectedPhase", ExpiryProbePolicy.phase(started, saved.optString("initialExpireTime")))
        store.update { checks.put(entry); it.put("expiryChecks", checks) }
        expiryRun = run
        expiryCancellation = cancellation
        try {
            checkActive()
            val summary = when (op) {
                "session", "initial-token-session" -> {
                    val session = poll(token, saved.getString("sessionId"))
                    if (!originalToken) store.update { it.put("credentialAssociation", "SESSION_ACCESS_CONFIRMED") }
                    "Sessão: HTTP ${session.getJSONObject("_probeHttp").getInt("status")}; acesso observado com credencial ${entry.getInt("credentialVersion")}."
                }
                "initial" -> inspectInitialUrls(saved, token, onProgress)
                else -> {
                    inspect(token, saved.getString("sessionId"), onProgress)
                    val latest = store.read() ?: error("No private probe snapshot")
                    entry.put("currentMediaChecks", JSONArray(latest.getJSONArray("mediaChecks").toString()))
                    "Relistagem e bytes atuais executados; consulte os resultados HTTP por requisição. URLs iniciais preservadas."
                }
            }
            checkActive()
            val requests = store.read()!!.getJSONArray("expiryChecks").getJSONObject(run).getJSONArray("requests")
            val failed = (0 until requests.length()).count { requests.getJSONObject(it).optString("outcome") != "SUCCESS" }
            val statusSummary = (0 until requests.length()).mapNotNull {
                requests.getJSONObject(it).optJSONObject("http")?.optInt("status")?.takeIf { status -> status > 0 }
            }.groupingBy { it }.eachCount().entries.joinToString { "${it.key} (${it.value})" }.ifEmpty { "nenhum" }
            val finalSummary = "$summary\nCredencial ${entry.getInt("credentialVersion")}: ${entry.getString("tokenExpectedPhase")}; sessão: ${entry.getString("sessionExpectedPhase")}.\n${requests.length()} requisições lógicas; $failed falhas. HTTP: $statusSummary.\nFases previstas não comprovam expiração; vídeos são prefixos parciais e não há cache local de mídia."
            checkActive()
            finishExpiryRun(run, entry, if (failed == 0) "COMPLETED" else "COMPLETED_WITH_FAILURES", finalSummary)
            return finalSummary
        } catch (error: Exception) {
            val cancelled = this.isCancelled()
            val status = if (error is ProbeHttpException) " HTTP ${error.status}." else ""
            val summary = "Ensaio $op ${if (cancelled) "interrompido" else "falhou"}.$status Categoria: ${error.javaClass.simpleName}. Evidência privada preservada; nenhuma sessão criada ou excluída."
            finishExpiryRun(run, entry, if (cancelled) "CANCELLED" else "FAILED", summary)
            if (cancelled) throw error
            return summary
        } finally { expiryRun = null; expiryCancellation = null }
    }

    private fun inspectInitialUrls(saved: JSONObject, token: String, onProgress: (String) -> Unit): String {
        val initial = saved.getJSONArray("initialCatalog")
        check(initial.length() in 1..12) { "Initial catalog unavailable or exceeds diagnostic limit" }
        val results = JSONArray()
        var successes = 0
        for (index in 0 until initial.length()) {
            checkActive()
            val item = initial.getJSONObject(index)
            val video = item.optString("type") == "VIDEO"
            val result = JSONObject().put("itemIndex", index).put("startedAt", now())
                .put("urlAgeSeconds", ExpiryProbePolicy.urlAgeSeconds(Instant.now(), item.optString("firstReceivedAt")) ?: JSONObject.NULL)
            try {
                if (item.optString("type") !in setOf("PHOTO", "VIDEO")) {
                    result.put("outcome", "UNSUPPORTED_TYPE")
                } else if (video && item.optJSONObject("mediaFile")?.optJSONObject("mediaFileMetadata")
                        ?.optJSONObject("videoMetadata")?.optString("processingStatus") != "READY") {
                    result.put("outcome", "INITIAL_VIDEO_NOT_READY")
                } else {
                    result.put("retrieval", media(ProbePolicy.mediaUrl(item.getString("firstBaseUrl"), video), token, video))
                    result.put("outcome", "SUCCESS"); successes++
                }
            } catch (error: Exception) {
                result.put("outcome", "FAILED").put("category", error.javaClass.simpleName)
                if (error is ProbeHttpException) result.put("httpStatus", error.status)
                if (isCancelled()) throw error
            } finally {
                result.put("finishedAt", now()); results.put(result)
                persistWithoutInterrupt { store.update { it.getJSONArray("expiryChecks").getJSONObject(expiryRun!!).put("initialMediaChecks", results) } }
            }
            onProgress("URLs iniciais sem relistagem: ${index + 1}/${initial.length()}, $successes acessíveis.")
        }
        val ages = (0 until results.length()).mapNotNull { index ->
            results.getJSONObject(index).opt("urlAgeSeconds").let { if (it is Number) it.toLong() else null }
        }
        val ageSummary = if (ages.isEmpty()) "idade desconhecida" else "idades ${ages.minOrNull()}–${ages.maxOrNull()} segundos"
        return "URLs iniciais: $successes/${initial.length()} acessíveis, sem relistagem; $ageSummary."
    }

    private fun finishExpiryRun(run: Int, extra: JSONObject, outcome: String, summary: String): JSONObject {
        val cancelledBeforePersistence = isCancelled()
        return persistWithoutInterrupt {
            store.update { saved ->
                val record = saved.getJSONArray("expiryChecks").getJSONObject(run)
                if (extra.has("currentMediaChecks")) record.put("currentMediaChecks", extra.get("currentMediaChecks"))
                val cancelled = cancelledBeforePersistence || isCancelled()
                val finalOutcome = if (cancelled) "CANCELLED" else outcome
                val finalSummary = if (cancelled && outcome != "CANCELLED")
                    "Medição cancelada; respostas já recebidas preservadas, sem concluir a operação." else summary
                record.put("finishedAt", now()).put("outcome", finalOutcome).put("summary", finalSummary)
                saved.put("lastExpirySummary", finalSummary)
            }
        }
    }

    private fun <T> persistWithoutInterrupt(action: () -> T): T {
        val interrupted = Thread.interrupted()
        try { return action() } finally { if (interrupted) Thread.currentThread().interrupt() }
    }

    private fun observedRequest(kind: String, action: () -> JSONObject): JSONObject {
        val run = expiryRun ?: return action()
        var index = 0
        expiryResponseHttp = null
        store.update {
            val requests = it.getJSONArray("expiryChecks").getJSONObject(run).getJSONArray("requests")
            index = ExpiryProbePolicy.nextIndex(requests.length(), ExpiryProbePolicy.MAX_REQUESTS)
            requests.put(JSONObject().put("kind", kind).put("startedAt", now()).put("outcome", "STARTED"))
        }
        try {
            val response = action()
            checkActive()
            store.update {
                val record = it.getJSONArray("expiryChecks").getJSONObject(run).getJSONArray("requests").getJSONObject(index)
                record.put("finishedAt", now()).put("outcome", "SUCCESS")
                val http = response.optJSONObject("_probeHttp") ?: response.optJSONObject("http")
                if (http != null) record.put("http", http)
                if (response.has("bytes")) record.put("bytes", response.getLong("bytes"))
                if (response.has("redirects")) record.put("redirects", response.get("redirects"))
            }
            return response
        } catch (error: Exception) {
            val cancelled = this.isCancelled()
            persistWithoutInterrupt { store.update {
                val record = it.getJSONArray("expiryChecks").getJSONObject(run).getJSONArray("requests").getJSONObject(index)
                record.put("finishedAt", now()).put("outcome", if (cancelled) "CANCELLED" else "FAILED")
                    .put("category", error.javaClass.simpleName)
                if (expiryResponseHttp != null) record.put("http", expiryResponseHttp)
                if (error is ProbeHttpException) {
                    record.put("http", error.privateHttpEvidence?.let { value -> JSONObject(value) } ?: JSONObject().put("status", error.status))
                    if (error.privateRedirectLocation != null) record.put("redirectLocation", error.privateRedirectLocation)
                    if (error.privateRedirectTrail != null) record.put("redirects", JSONArray(error.privateRedirectTrail))
                }
                if (error is ProbeMediaException) record.put("reason", error.reason.name)
            } }
            throw error
        }
    }

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
