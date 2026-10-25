package br.com.memora

import java.math.BigDecimal

/** Monotonic UI scheduling: later responses may shorten, but never renew, the original timeout. */
internal class PickerPollingWindow {
    private var intervalMs: Long = 0L
    var deadlineAt: Long = 0L
        private set
    var nextPollAt: Long = 0L
        private set

    @Synchronized fun update(now: Long, interval: String, timeout: String) {
        val intervalMs = durationMs(interval)
        this.intervalMs = intervalMs
        val remainingMs = durationMs(timeout)
        require(intervalMs > 0L && remainingMs > 0L) { "Polling finished or invalid" }
        val candidate = Math.addExact(now, remainingMs)
        deadlineAt = if (deadlineAt == 0L) candidate else minOf(deadlineAt, candidate)
        nextPollAt = Math.addExact(now, intervalMs)
    }

    @Synchronized fun markRequest(now: Long) {
        require(intervalMs > 0L)
        nextPollAt = Math.addExact(now, intervalMs)
    }

    @Synchronized fun delay(now: Long): Long? = if (deadlineAt == 0L || now >= deadlineAt) null
        else (nextPollAt - now).coerceAtLeast(0L).coerceAtMost(deadlineAt - now)

    companion object {
        private fun durationMs(value: String): Long {
            require(value.matches(Regex("[0-9]+(?:\\.[0-9]{1,9})?s")))
            val millis = BigDecimal(value.removeSuffix("s")).multiply(BigDecimal(1000))
            return millis.setScale(0, java.math.RoundingMode.CEILING).longValueExact()
        }
    }
}
