package br.com.memora

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class PickerProbePolicyTest {
    @Test fun paginationContinuesAcrossShortPagesAndRejectsRepeatedToken() {
        val paging = ProbePagingGuard()
        paging.page("")
        assertTrue(paging.item("photo-a"))
        paging.page("next") // Even a page with zero items cannot force completion.
        paging.page("last")
        assertFalse(paging.item("photo-a"))
        assertTrue(paging.item("video-b"))
        assertEquals(3, paging.pages)
        assertEquals(3, paging.received)
        assertEquals(1, paging.duplicates)
        try { paging.page("next"); fail("Repeated token accepted") }
        catch (_: IllegalStateException) { }
    }
    @Test fun endlessFreshTokensCannotRunWithoutBound() {
        val paging = ProbePagingGuard()
        repeat(ProbePolicy.MAX_PAGES) { paging.page("page-$it") }
        try { paging.page("too-many"); fail("Unbounded pagination") }
        catch (_: IllegalStateException) { }
    }
    @Test fun boundedReadsRejectTruncationInsteadOfReturningPartialPhotoOrJson() {
        assertArrayEquals(byteArrayOf(1, 2, 3), readProbeBytes(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 3))
        try { readProbeBytes(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), 3); fail("Oversize accepted") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun onlyTrustedHttpsMediaOriginsReceiveAuthorization() {
        assertEquals("https://lh3.googleusercontent.com/p/sample=w1280-h720",
            ProbePolicy.mediaUrl("https://lh3.googleusercontent.com/p/sample", false))
        assertEquals("https://lh3.googleusercontent.com/p/sample=dv",
            ProbePolicy.mediaUrl("https://lh3.googleusercontent.com/p/sample", true))
        listOf("http://lh3.googleusercontent.com/p/x", "https://lh3.googleusercontent.com.evil.test/p/x",
            "https://user@lh3.googleusercontent.com/p/x", "https://lh3.googleusercontent.com:443/p/x",
            "https://example.test/x", "https://lh3.googleusercontent.com/p/x?redirect=evil").forEach {
            try { ProbePolicy.mediaUrl(it, false); fail("Unsafe media URL accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun sessionIdentifierCannotEscapeApiPath() {
        assertEquals("sample_session-1", ProbePolicy.sessionId("sample_session-1"))
        listOf("", "../other", "id?x=1", "id/path").forEach {
            try { ProbePolicy.sessionId(it); fail("Unsafe session path accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun redirectsAllowSignedGoogleMediaButRejectCredentialAndOriginEscapes() {
        val original = "https://lh3.googleusercontent.com/p/sample=dv"
        assertEquals("https://video-downloads.googleusercontent.com/video?signature=synthetic",
            ProbePolicy.redirectUrl(original, "https://video-downloads.googleusercontent.com/video?signature=synthetic"))
        assertEquals("https://lh3.googleusercontent.com/next?signature=synthetic",
            ProbePolicy.redirectUrl(original, "/next?signature=synthetic"))
        listOf("http://lh3.googleusercontent.com/x", "https://googleusercontent.com.evil.test/x",
            "https://user@lh3.googleusercontent.com/x", "https://lh3.googleusercontent.com:443/x",
            "https://redirect.googlevideo.com/x", "https://lh3.googleusercontent.com/x#fragment").forEach {
            try { ProbePolicy.redirectUrl(original, it); fail("Unsafe redirect accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun redirectChainCannotLoopOrExceedThreeHops() {
        val loop = ProbeRedirectGuard("https://a.googleusercontent.com/x")
        loop.follow("https://b.googleusercontent.com/x")
        try { loop.follow("https://a.googleusercontent.com/x"); fail("Redirect loop accepted") }
        catch (_: IllegalStateException) { }
        val chain = ProbeRedirectGuard("start")
        repeat(ProbePolicy.MAX_REDIRECTS) { chain.follow("hop-$it") }
        try { chain.follow("excess"); fail("Unbounded redirects") }
        catch (_: IllegalStateException) { }
    }

    @Test fun httpErrorMessageNeverIncludesPrivateRedirectCapabilities() {
        val error = ProbeHttpException(302, "https://example.test/private?signature=synthetic", "example.test")
        assertEquals("Probe HTTP 302", error.message)
        assertFalse(error.toString().contains("signature"))
        assertFalse(error.toString().contains("private"))
    }

}
