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
}
