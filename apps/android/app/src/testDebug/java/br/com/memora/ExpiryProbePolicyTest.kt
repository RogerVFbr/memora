package br.com.memora

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ExpiryProbePolicyTest {
    @Test fun validityHintsTreatBoundaryAsExpiredAndMalformedTimeAsUnknown() {
        val expiry = "2026-10-25T23:38:40Z"
        assertEquals("EXPECTED_VALID", ExpiryProbePolicy.phase(Instant.parse("2026-10-25T23:38:39Z"), expiry))
        assertEquals("EXPECTED_EXPIRED", ExpiryProbePolicy.phase(Instant.parse(expiry), expiry))
        assertEquals("EXPECTED_EXPIRED", ExpiryProbePolicy.phase(Instant.parse("2026-10-26T23:38:40Z"), expiry))
        assertEquals("UNKNOWN", ExpiryProbePolicy.phase(Instant.parse(expiry), ""))
        assertEquals("UNKNOWN", ExpiryProbePolicy.phase(Instant.parse(expiry), "not-a-time"))
    }
    @Test fun urlAgeRetainsSecondsBeyondOneHourWithoutClaimingActualExpiry() {
        val start = "2026-10-25T22:41:23Z"
        assertEquals(3601L, ExpiryProbePolicy.urlAgeSeconds(Instant.parse("2026-10-25T23:41:24Z"), start))
        assertNull(ExpiryProbePolicy.urlAgeSeconds(Instant.parse("2026-10-25T22:41:22Z"), start))
        assertNull(ExpiryProbePolicy.urlAgeSeconds(Instant.now(), "missing"))
    }
    @Test fun remoteMutationsCannotBeSelectedAsExpiryOperations() {
        listOf("session", "initial", "relist", "initial-token-session").forEach {
            assertEquals(it, ExpiryProbePolicy.operation(it))
        }
        listOf("create", "delete", "cleanup", "https://example.test", "").forEach {
            try { ExpiryProbePolicy.operation(it); fail("Remote mutation or arbitrary operation accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun fullHistoryRefusesNewNetworkIntentInsteadOfDroppingOriginalEvidence() {
        assertEquals(31, ExpiryProbePolicy.nextIndex(31, ExpiryProbePolicy.MAX_RUNS))
        try { ExpiryProbePolicy.nextIndex(32, ExpiryProbePolicy.MAX_RUNS); fail("Unbounded history") }
        catch (_: IllegalStateException) { }
        try { ExpiryProbePolicy.nextIndex(128, ExpiryProbePolicy.MAX_REQUESTS); fail("Unbounded request history") }
        catch (_: IllegalStateException) { }
    }
    @Test fun cancellationSurvivesNativeIoClearingThreadInterruption() {
        val cancellation = ExpiryProbeCancellation()
        try {
            Thread.currentThread().interrupt()
            cancellation.cancel()
            assertTrue(Thread.interrupted()) // Model native I/O consuming interruption.
            assertFalse(Thread.currentThread().isInterrupted)
            assertTrue(cancellation.isCancelled())
            assertTrue(cancellation.isCancelled()) // Repeated requests cannot reset it.
        } finally { Thread.interrupted() }
    }
    @Test fun signalCreatedBeforeWorkerSubmissionPreservesPreStartCancellation() {
        val signal = java.util.concurrent.atomic.AtomicBoolean(false)
        signal.set(true) // UI cancellation before worker enters runExpiryCheck.
        val cancellation = ExpiryProbeCancellation { signal.get() }
        assertTrue(cancellation.isCancelled())
        val nextSignal = java.util.concurrent.atomic.AtomicBoolean(false)
        val nextRun = ExpiryProbeCancellation { nextSignal.get() }
        assertFalse(nextRun.isCancelled())
        assertTrue(cancellation.isCancelled())
    }
}
