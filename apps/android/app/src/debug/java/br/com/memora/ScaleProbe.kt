package br.com.memora

import android.content.Context
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.time.Instant
import javax.net.ssl.HttpsURLConnection

/** G5 metadata-only bench. No media endpoint exists here; G4 storage is never opened. */
class ScaleProbe(context: Context) {
    private val store = ScaleStore(context)
    private val api = "https://photospicker.googleapis.com/v1"
    @Volatile private var cancellation: ScaleCancellation? = null
    private var listDeadline: Long? = null
    fun cancel() { cancellation?.cancel() }
    private fun now() = Instant.now().toString()
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun saved(slot: String): JSONObject = store.read()?.getJSONObject("slots")?.getJSONObject(ScalePolicy.slot(slot))
        ?: error("No scale slot")
    private fun update(slot: String, change: (JSONObject) -> Unit) = store.update { change(it.getJSONObject("slots").getJSONObject(slot)) }
    private fun checkActive() {
        cancellation?.check() ?: check(!Thread.currentThread().isInterrupted) { "Scale action cancelled" }
        check(listDeadline?.let { SystemClock.elapsedRealtime() < it } != false) { "Scale listing time budget reached" }
    }
    private fun <T> persist(action: () -> T): T {
        val interrupted = Thread.interrupted()
        try { return action() } finally { if (interrupted) Thread.currentThread().interrupt() }
    }

    fun create(token: String, expiresInSeconds: Long, slot: String): JSONObject {
        ScalePolicy.slot(slot)
        require(token.isNotBlank() && expiresInSeconds in 1..31_536_000)
        checkActive()
        val snapshot = store.read() ?: JSONObject().put("version", 1).put("slots", JSONObject())
        val slots = snapshot.getJSONObject("slots")
        check(!slots.has(slot) || ScalePolicy.mayCreate(slots.getJSONObject(slot).optString("state"))) { "Scale slot already exists" }
        val started = Instant.now()
        slots.put(slot, JSONObject().put("token", token).put("tokenReceivedAt", started.toString())
            .put("tokenExpiresAt", started.plusSeconds(expiresInSeconds).toString()).put("createdAt", now())
            .put("state", "CREATE_INTENT").put("requests", JSONArray()).put("catalog", JSONArray())
            .put("pages", JSONArray()).put("listingComplete", false))
        snapshot.remove("summary"); store.save(snapshot) // Durable intent before POST, including uncertain transport outcomes.
        try {
            val session = request(slot, "$api/sessions", "POST", "CREATE",
                JSONObject().put("pickingConfig", JSONObject().put("maxItemCount", "2000")))
            update(slot) { it.put("sessionId", ScalePolicy.sessionId(session.getString("id")))
                .put("session", session).put("state", "SESSION_CREATED") }
            return session
        } catch (error: Exception) {
            persist { update(slot) {
                // request() persists a returned ID before returning, so later local failure stays trackable.
                it.put("state", if (it.has("sessionId")) "SESSION_CREATED" else "CREATE_UNCERTAIN")
                it.put("summary", "Criação G5 não concluída; resultado remoto deve ser conferido antes de descartar evidência.")
            } }
            throw error
        }
    }

    fun poll(slot: String): JSONObject {
        val current = saved(slot)
        val id = ScalePolicy.sessionId(current.getString("sessionId"))
        val session = request(slot, "$api/sessions/$id", "GET", "POLL")
        update(slot) { it.put("session", session).put("lastPollAt", now()) }
        return session
    }

    fun list(slot: String, isCancelled: () -> Boolean = { false }, onProgress: (String) -> Unit): String {
        ScalePolicy.slot(slot)
        val signal = ScaleCancellation(isCancelled)
        signal.check()
        check(cancellation == null) { "Scale listing already active" }
        val current = saved(slot)
        check(current.getJSONObject("session").optBoolean("mediaItemsSet")) { "Scale selection not ready" }
        val id = ScalePolicy.sessionId(current.getString("sessionId"))
        val guard = ScalePagingGuard()
        val catalog = JSONArray(); val pages = JSONArray(); val inventory = linkedSetOf<String>()
        val fieldCounts = linkedMapOf<String, Int>()
        val started = SystemClock.elapsedRealtime()
        update(slot) { it.put("state", "LIST_STARTED").put("listingComplete", false)
            .put("listStartedAt", now()).put("catalog", catalog).put("pages", pages) }
        cancellation = signal
        listDeadline = started + ScalePolicy.LIST_MILLIS
        try {
            var token = ""
            do {
                signal.check(); guard.page(token)
                val suffix = if (token.isEmpty()) "" else "&pageToken=${encode(token)}"
                val page = request(slot, "$api/mediaItems?sessionId=${encode(id)}&pageSize=100$suffix", "GET", "LIST")
                val items = page.optJSONArray("mediaItems") ?: JSONArray()
                check(items.length() <= ScalePolicy.PAGE_SIZE) { "Scale page exceeds requested bound" }
                for (index in 0 until items.length()) {
                    signal.check()
                    val item = items.getJSONObject(index)
                    val paths = fieldPaths(item)
                    inventory.addAll(paths)
                    check(inventory.size <= ScalePolicy.MAX_FIELD_PATHS) { "Scale field inventory limit" }
                    if (guard.item(item.getString("id"))) {
                        // Deliberately no filename, URL, capture timestamp, camera values, or source album name.
                        catalog.put(JSONObject().put("id", item.getString("id")).put("type", ScalePolicy.type(item.optString("type")))
                            .put("fields", JSONArray(paths.sorted())))
                        paths.forEach { fieldCounts[it] = (fieldCounts[it] ?: 0) + 1 }
                    }
                }
                token = page.optString("nextPageToken")
                require(token.length <= 16_384) { "Scale page token exceeds bound" }
                pages.put(JSONObject().put("at", now()).put("count", items.length()).put("hasNextPage", token.isNotEmpty())
                    .put("http", page.getJSONObject("_scaleHttp")))
                update(slot) { it.put("catalog", catalog).put("pages", pages).put("received", guard.received)
                    .put("duplicates", guard.duplicates).put("unique", catalog.length())
                    .put("fieldPaths", JSONArray(inventory.sorted())).put("fieldPresenceCounts", JSONObject(fieldCounts as Map<*, *>)) }
                onProgress("G5 $slot: ${guard.pages} páginas, ${guard.received} recebidos, ${catalog.length()} únicos; sem bytes de mídia.")
            } while (token.isNotEmpty())
            signal.check()
            val history = saved(slot).getJSONArray("requests")
            var bodyBytes = 0L
            for (i in 0 until history.length()) bodyBytes += history.getJSONObject(i).optLong("bodyBytes", 0)
            val summary = "G5 $slot: ${catalog.length()} únicos, ${guard.received} recebidos, ${guard.duplicates} duplicatas, ${guard.pages} páginas até o fim.\n${SystemClock.elapsedRealtime() - started} ms; ${history.length()} tentativas HTTP, $bodyBytes bytes de corpo JSON lidos; nenhum byte de mídia solicitado. Contagem de origem ainda depende de confirmação do usuário."
            update(slot) { signal.check(); it.put("listingComplete", true).put("state", "LIST_COMPLETE")
                .put("listFinishedAt", now()).put("listDurationMs", SystemClock.elapsedRealtime() - started).put("summary", summary) }
            return summary
        } catch (error: Exception) {
            val cancelled = signal.isCancelled()
            val status = if (error is ScaleHttpException) " HTTP ${error.status}." else ""
            val summary = "G5 $slot: ${if (cancelled) "cancelado" else "incompleto"}.$status ${catalog.length()} únicos em ${pages.length()} páginas recebidas; sem comprovar travessia completa. Categoria ${error.javaClass.simpleName}."
            persist { update(slot) { it.put("state", if (cancelled) "LIST_CANCELLED" else "LIST_FAILED")
                .put("listingComplete", false).put("listFinishedAt", now()).put("summary", summary)
                .put("catalog", catalog).put("pages", pages).put("received", guard.received)
                .put("unique", catalog.length()).put("duplicates", guard.duplicates) } }
            if (cancelled) throw error
            return summary
        } finally { cancellation = null; listDeadline = null }
    }

    fun compare(): String {
        val snapshot = store.read() ?: error("No scale evidence")
        val slots = snapshot.getJSONObject("slots")
        val a = slots.getJSONObject("A"); val b = slots.getJSONObject("B")
        check(a.optBoolean("listingComplete") && b.optBoolean("listingComplete")) { "Both scale listings must be complete" }
        fun ids(slot: JSONObject): List<String> = slot.getJSONArray("catalog").let { rows ->
            (0 until rows.length()).map { rows.getJSONObject(it).getString("id") }
        }
        val result = scaleOverlap(ids(a), ids(b))
        val summary = "G5: A=${result.a}, B=${result.b}; ${result.shared} IDs comuns, ${result.union} únicos na união.\nComparação por IDs não comprova membership de álbum nem sincronização. Tamanho de origem não confirmado pela API; aceite de escala depende da evidência do usuário."

        store.update { it.put("summary", summary).put("comparedAt", now()) }
        return summary
    }

    /** Removes only this G5 remote session. Compact evidence remains until clearAll(). */
    fun cleanup(slot: String): Boolean {
        return try {
            val current = saved(slot)
            if (current.optString("state") == "REMOTE_DELETED") return true
            val id = ScalePolicy.sessionId(current.getString("sessionId"))
            request(slot, "$api/sessions/$id", "DELETE", "DELETE")
            update(slot) { it.put("state", "REMOTE_DELETED").put("remoteDeletedAt", now())
                .remove("token"); it.remove("session"); it.remove("sessionId"); it.remove("tokenExpiresAt") }
            true
        } catch (_: Exception) { false } // A 404 can also mean not owned; preserve evidence.
    }

    /** Explicit local evidence erasure after all tracked remote sessions have confirmed deletion. */
    fun clearAll(): Boolean {
        val snapshot = store.read() ?: return true
        val slots = snapshot.getJSONObject("slots")
        val keys = slots.keys()
        while (keys.hasNext()) if (slots.getJSONObject(keys.next()).optString("state") != "REMOTE_DELETED") return false
        store.clear(); return true
    }

    private fun fieldPaths(item: JSONObject): Set<String> {
        val found = linkedSetOf<String>()
        fun walk(value: JSONObject, prefix: String, depth: Int) {
            check(depth <= 6) { "Scale metadata nesting exceeds bound" }
            val keys = value.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                require(key.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,63}"))) { "Scale field name exceeds schema bound" }
                val path = if (prefix.isEmpty()) key else "$prefix.$key"
                found.add(path)
                check(found.size <= ScalePolicy.MAX_FIELD_PATHS) { "Scale field inventory limit" }
                val child = value.opt(key)
                if (child is JSONObject) walk(child, path, depth + 1)
                // The current API model has no repeated metadata object fields. Record array presence only.
            }
        }
        walk(item, "", 0); return found
    }

    private fun request(slot: String, url: String, method: String, kind: String, body: JSONObject? = null): JSONObject {
        require(url.startsWith("$api/") && URI(url).host == "photospicker.googleapis.com")
        checkActive()
        val current = saved(slot)
        val token = current.getString("token")
        val requests = current.getJSONArray("requests")
        var cleanupRequests = 0
        for (i in 0 until requests.length()) if (requests.getJSONObject(i).optString("kind") == "DELETE") cleanupRequests++
        check(if (kind == "DELETE") cleanupRequests < 8 else requests.length() - cleanupRequests < ScalePolicy.MAX_REQUESTS) {
            "Scale request budget reached"
        }
        var consumed = 0L
        for (i in 0 until requests.length()) consumed += requests.getJSONObject(i).optLong("bodyBytes", 0)
        val bodyLimit = if (kind == "DELETE") 64 * 1024 else ScalePolicy.remainingBodyBytes(consumed)
        val index = requests.length()
        update(slot) { it.getJSONArray("requests").put(JSONObject().put("kind", kind).put("startedAt", now()).put("outcome", "STARTED")) }
        val started = SystemClock.elapsedRealtime()
        val requestDeadline = started + ScalePolicy.REQUEST_MILLIS
        fun requestActive() {
            checkActive()
            check(SystemClock.elapsedRealtime() < requestDeadline) { "Scale request time budget reached" }
        }
        var connection: HttpsURLConnection? = null
        var http: JSONObject? = null
        var responseBytes = 0
        try {
            checkActive()
            connection = (URI(url).toURL().openConnection() as HttpsURLConnection).apply {
                instanceFollowRedirects = false; connectTimeout = 15_000; readTimeout = 15_000; useCaches = false
                requestMethod = method; setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Cache-Control", "no-cache, no-store"); setRequestProperty("Pragma", "no-cache")
            }
            if (body != null) {
                connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            http = JSONObject().put("status", status).put("at", now())
                .put("date", connection.getHeaderField("Date") ?: JSONObject.NULL)
                .put("age", connection.getHeaderField("Age") ?: JSONObject.NULL)
                .put("cacheControl", connection.getHeaderField("Cache-Control") ?: JSONObject.NULL)
            if (status !in 200..299) throw ScaleHttpException(status)
            requestActive()
            val bytes = connection.inputStream.use {
                readScaleBytes(it, bodyLimit, ::requestActive) { count -> responseBytes += count }
            }
            val response = try { if (bytes.isEmpty()) JSONObject() else JSONObject(String(bytes, Charsets.UTF_8)) }
                finally { bytes.fill(0) }
            // Persist returned POST identity before any further cancellable work or success notification.
            if (kind == "CREATE") update(slot) { it.put("sessionId", ScalePolicy.sessionId(response.getString("id")))
                .put("session", response).put("state", "SESSION_CREATED") }
            checkActive()
            update(slot) { it.getJSONArray("requests").getJSONObject(index).put("outcome", "SUCCESS")
                .put("finishedAt", now()).put("durationMs", SystemClock.elapsedRealtime() - started)
                .put("bodyBytes", responseBytes).put("http", http) }
            return response.put("_scaleHttp", http)
        } catch (error: Exception) {
            val cancelled = cancellation?.isCancelled() == true || Thread.currentThread().isInterrupted
            persist { update(slot) { it.getJSONArray("requests").getJSONObject(index)
                .put("outcome", if (cancelled) "CANCELLED" else "FAILED").put("category", error.javaClass.simpleName)
                .put("finishedAt", now()).put("durationMs", SystemClock.elapsedRealtime() - started)
                .put("bodyBytes", responseBytes).put("http", http ?: JSONObject.NULL) } }
            throw error
        } finally { connection?.disconnect() }
    }
}

class ScaleHttpException(val status: Int) : Exception("Scale HTTP $status")
