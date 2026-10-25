package br.com.memora

import java.time.Instant

/** Bench scheduling hints only: server responses, not these hints, prove access. */
internal object ExpiryProbePolicy {
    const val MAX_RUNS = 32
    const val MAX_REQUESTS = 128
    const val MAX_AUTHORIZATIONS = 16
    val operations = setOf("session", "initial", "relist", "initial-token-session")

    fun operation(value: String): String = value.also { require(it in operations) { "Unknown expiry operation" } }
    fun phase(observedAt: Instant, expectedExpiry: String): String {
        val expiry = try { Instant.parse(expectedExpiry) } catch (_: Exception) { return "UNKNOWN" }
        return if (observedAt.isBefore(expiry)) "EXPECTED_VALID" else "EXPECTED_EXPIRED"
    }
    fun urlAgeSeconds(observedAt: Instant, receivedAt: String): Long? = try {
        java.time.Duration.between(Instant.parse(receivedAt), observedAt).seconds.takeIf { it >= 0 }
    } catch (_: Exception) { null }
    fun nextIndex(size: Int, limit: Int): Int = size.also {
        check(size in 0 until limit) { "Expiry diagnostic history limit reached" }
    }
}

/** Cancellation survives native I/O clearing the thread interruption flag. */
internal class ExpiryProbeCancellation(private val external: () -> Boolean = { false }) {
    @Volatile private var cancelled = false
    fun cancel() { cancelled = true }
    fun isCancelled(): Boolean = cancelled || external()
}
