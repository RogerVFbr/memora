package br.com.memora

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

class RefreshProbePolicyTest {
    private val at = Instant.parse("2026-10-25T23:51:00Z")
    private val scope = "https://www.googleapis.com/auth/photospicker.mediaitems.readonly"
    private val previous get() = mapOf("refresh_token" to "synthetic-refresh", "scope" to scope,
        "refreshExpiresAt" to "2026-11-01T22:50:26Z")
    private val response get() = mapOf("access_token" to "synthetic-access", "expires_in" to "3600", "token_type" to "Bearer")

    @Test fun missingRotationAndScopePreserveCurrentGrantAndOriginalRefreshExpiry() {
        val merged = RefreshProbePolicy.merge(previous, response, at)
        assertEquals("synthetic-refresh", merged["refresh_token"])
        assertEquals(scope, merged["scope"])
        assertEquals("2026-11-01T22:50:26Z", merged["refreshExpiresAt"])
        assertEquals("synthetic-access", merged["access_token"])
    }
    @Test fun rotationReplacesCredentialButDoesNotInventANewLifetime() {
        val merged = RefreshProbePolicy.merge(previous, response + ("refresh_token" to "synthetic-rotated"), at)
        assertEquals("synthetic-rotated", merged["refresh_token"])
        assertEquals(previous["refreshExpiresAt"], merged["refreshExpiresAt"])
        val explicitExpiry = RefreshProbePolicy.merge(previous,
            response + mapOf("refresh_token" to "synthetic-rotated", "refresh_token_expires_in" to "600"), at)
        assertEquals("2026-10-26T00:01:00Z", explicitExpiry["refreshExpiresAt"])
    }
    @Test fun invalidRefreshResponsesCannotReplaceTheCurrentGrant() {
        listOf(response - "access_token", response + ("expires_in" to "0"), response + ("token_type" to "Other"),
            response + ("scope" to "profile"), response + ("refresh_token" to ""),
            response + ("refresh_token_expires_in" to "invalid")).forEach {
            try { RefreshProbePolicy.merge(previous, it, at); fail("Invalid refresh response accepted") }
            catch (_: IllegalArgumentException) { }
        }
        try { RefreshProbePolicy.merge(emptyMap(), response, at); fail("Missing current refresh grant accepted") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun unknownOAuthErrorCannotBecomeAUserVisibleMessage() {
        assertEquals("invalid_grant", RefreshProbePolicy.safeError("invalid_grant"))
        assertEquals("OTHER", RefreshProbePolicy.safeError("private-token-or-server-description"))
    }
    @Test fun oversizedResponseIsRejectedWithoutSilentTruncation() {
        assertArrayEquals(byteArrayOf(1, 2), readRefreshBody(ByteArrayInputStream(byteArrayOf(1, 2))) {})
        try { readRefreshBody(ByteArrayInputStream(ByteArray(RefreshProbePolicy.MAX_BODY + 1))) {}; fail("Oversize accepted") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun cancellationAfterNativeReadIsCheckedBeforeParsingOrAnotherRead() {
        val cancelled = AtomicBoolean(false)
        var reads = 0
        val input = object : InputStream() {
            override fun read(): Int = error("Bulk reads expected")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                reads++; cancelled.set(true); Thread.interrupted(); buffer[offset] = 1; return 1
            }
        }
        try { readRefreshBody(input) { check(!cancelled.get()) }; fail("Cancellation ignored") }
        catch (_: IllegalStateException) { }
        assertEquals(1, reads)
    }
}
