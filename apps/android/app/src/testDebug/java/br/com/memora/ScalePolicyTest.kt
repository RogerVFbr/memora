package br.com.memora

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

class ScalePolicyTest {
    @Test fun scaleAcceptsTwoThousandUniqueButRejectsAnExtraItem() {
        val guard = ScalePagingGuard()
        repeat(2000) { assertTrue(guard.item("id-$it")) }
        assertFalse(guard.item("id-0"))
        assertEquals(2001, guard.received)
        assertEquals(1, guard.duplicates)
        try { guard.item("one-too-many"); fail("More than 2000 unique items accepted") }
        catch (_: IllegalStateException) { }
    }
    @Test fun paginationAllowsEmptyAndShortPagesWithoutAssumingEndAndRejectsReplay() {
        val guard = ScalePagingGuard()
        guard.page("") // No items: next token still requires following page.
        guard.page("short-page"); guard.item("first")
        guard.page("last")
        assertEquals(3, guard.pages)
        try { guard.page("short-page"); fail("Repeated page token accepted") }
        catch (_: IllegalStateException) { }
        val bounded = ScalePagingGuard()
        repeat(ScalePolicy.MAX_PAGES) { bounded.page("$it") }
        try { bounded.page("extra"); fail("Unbounded paging") }
        catch (_: IllegalStateException) { }
    }
    @Test fun overlapCountsUniqueIdsIndependentlyOfWithinSelectionDuplicates() {
        assertEquals(ScaleOverlap(3, 3, 2, 4), scaleOverlap(listOf("a", "b", "b", "c"), listOf("b", "c", "d", "d")))
        assertEquals(ScaleOverlap(0, 1, 0, 1), scaleOverlap(emptyList(), listOf("a")))
    }
    @Test fun onlyNamedSlotsAndDeletedSlotsCanBeReplaced() {
        assertEquals("A", ScalePolicy.slot("A")); assertEquals("B", ScalePolicy.slot("B"))
        try { ScalePolicy.slot("../picker-probe"); fail("Arbitrary slot accepted") }
        catch (_: IllegalArgumentException) { }
        assertTrue(ScalePolicy.mayCreate("REMOTE_DELETED"))
        listOf("CREATE_UNCERTAIN", "LIST_COMPLETE", "SESSION_CREATED").forEach { assertFalse(ScalePolicy.mayCreate(it)) }
    }
    @Test fun responseBudgetNeverSilentlyTruncatesAndCountsActuallyReadBytes() {
        var read = 0
        assertArrayEquals(byteArrayOf(1, 2), readScaleBytes(ByteArrayInputStream(byteArrayOf(1, 2)), 2, onBytes = { read += it }))
        assertEquals(2, read)
        try { readScaleBytes(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 2); fail("Truncated JSON accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(1, ScalePolicy.remainingBodyBytes(ScalePolicy.MAX_SLOT_BODY_BYTES.toLong() - 1))
        try { ScalePolicy.remainingBodyBytes(ScalePolicy.MAX_SLOT_BODY_BYTES.toLong()); fail("Exhausted budget accepted") }
        catch (_: IllegalStateException) { }
    }
    @Test fun cancellationStopsAfterNativeReadClearsInterruptAndBeforeNextChunk() {
        val flag = AtomicBoolean(false)
        val cancellation = ScaleCancellation { flag.get() }
        var reads = 0
        val input = object : InputStream() {
            override fun read(): Int = error("Bulk reads expected")
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                reads++; flag.set(true); Thread.interrupted(); bytes[offset] = 1; return 1
            }
        }
        try { readScaleBytes(input, 100, cancellation::check); fail("Cancelled native read continued") }
        catch (_: IllegalStateException) { }
        assertEquals(1, reads)
        assertTrue(cancellation.isCancelled())
        assertFalse(Thread.currentThread().isInterrupted)
        val preCancelled = ScaleCancellation { true }
        try { preCancelled.check(); fail("Pre-start cancellation ignored") }
        catch (_: IllegalStateException) { }
    }
}
