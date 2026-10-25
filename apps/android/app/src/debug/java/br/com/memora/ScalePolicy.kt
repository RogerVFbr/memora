package br.com.memora

import java.io.ByteArrayOutputStream
import java.io.InputStream

internal object ScalePolicy {
    const val MAX_ITEMS = 2000
    const val PAGE_SIZE = 100
    const val MAX_PAGES = 128
    const val MAX_REQUESTS = 256
    const val JSON_BYTES = 2 * 1024 * 1024
    const val MAX_FIELD_PATHS = 128
    const val MAX_SLOT_BODY_BYTES = 32 * 1024 * 1024
    const val REQUEST_MILLIS = 60_000L
    const val LIST_MILLIS = 15 * 60_000L
    fun slot(value: String) = value.also { require(it == "A" || it == "B") { "Invalid scale slot" } }
    fun sessionId(value: String) = value.also {
        require(it.matches(Regex("[A-Za-z0-9_-]{1,1024}"))) { "Invalid scale session" }
    }
    fun mediaId(value: String) = value.also { require(it.isNotBlank() && it.length <= 1024) { "Invalid scale item" } }
    fun mayCreate(state: String) = state == "REMOTE_DELETED"
    fun type(value: String) = if (value in setOf("PHOTO", "VIDEO", "TYPE_UNSPECIFIED")) value else "UNKNOWN"
    fun remainingBodyBytes(consumed: Long): Int {
        require(consumed >= 0)
        check(consumed < MAX_SLOT_BODY_BYTES) { "Scale metadata byte budget reached" }
        return minOf(JSON_BYTES.toLong(), MAX_SLOT_BODY_BYTES - consumed).toInt()
    }
}

internal class ScalePagingGuard {
    private val tokens = mutableSetOf<String>()
    private val ids = linkedSetOf<String>()
    var pages = 0; private set
    var received = 0; private set
    var duplicates = 0; private set
    fun page(token: String) {
        check(pages < ScalePolicy.MAX_PAGES && tokens.add(token)) { "Scale pagination limit or replay" }
        pages++
    }
    fun item(id: String): Boolean {
        ScalePolicy.mediaId(id); received++
        if (id in ids) { duplicates++; return false }
        check(ids.size < ScalePolicy.MAX_ITEMS) { "Scale item limit exceeded" }
        ids.add(id); return true
    }
}

internal data class ScaleOverlap(val a: Int, val b: Int, val shared: Int, val union: Int)
internal fun scaleOverlap(a: Collection<String>, b: Collection<String>): ScaleOverlap {
    val first = a.toSet(); val second = b.toSet()
    return ScaleOverlap(first.size, second.size, first.intersect(second).size, first.union(second).size)
}

internal class ScaleCancellation(private val external: () -> Boolean = { false }) {
    @Volatile private var cancelled = false
    fun cancel() { cancelled = true }
    fun isCancelled() = cancelled || external() || Thread.currentThread().isInterrupted
    fun check() { check(!isCancelled()) { "Scale action cancelled" } }
}

internal fun readScaleBytes(input: InputStream, limit: Int, active: () -> Unit = {
    check(!Thread.currentThread().isInterrupted) { "Scale read cancelled" }
}, onBytes: (Int) -> Unit = {}): ByteArray {
    require(limit > 0)
    val output = ByteArrayOutputStream(minOf(limit, 32 * 1024))
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        active()
        val read = input.read(buffer, 0, minOf(buffer.size, limit - total + 1))
        if (read == -1) return output.toByteArray()
        onBytes(read)
        active()
        total += read
        require(total <= limit) { "Scale response exceeds diagnostic limit" }
        output.write(buffer, 0, read)
    }
}
