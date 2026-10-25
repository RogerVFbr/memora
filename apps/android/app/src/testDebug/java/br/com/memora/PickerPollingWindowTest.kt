package br.com.memora

import org.junit.Assert.*
import org.junit.Test

class PickerPollingWindowTest {
    @Test fun preservesOriginalDeadlineAcrossPollsAndBackground() {
        val w = PickerPollingWindow()
        w.update(1000, "2s", "10s")
        assertEquals(2000L, w.delay(1000))
        w.update(3000, "2s", "10s")
        assertEquals(11000L, w.deadlineAt)
        assertEquals(0L, w.delay(8000))
        assertNull(w.delay(11000))
    }
    @Test fun acceptsFractionalDurationsAndServerShortening() {
        val w = PickerPollingWindow()
        w.update(0, "0.5s", "10s")
        assertEquals(500L, w.delay(0))
        w.update(1000, "1.5s", "2s")
        assertEquals(3000L, w.deadlineAt)
    }
    @Test fun interruptedResponseDoesNotPermitImmediateBackgroundResumePoll() {
        val w = PickerPollingWindow()
        w.update(1000, "2s", "10s")
        w.markRequest(3000)
        assertEquals(1500L, w.delay(3500))
        assertEquals(11000L, w.deadlineAt)
    }
    @Test fun rejectsMissingNegativeZeroAndMalformedDurations() {
        for (duration in listOf("", "-1s", "NaNs", "0s", "5", "1e3s")) {
            try { PickerPollingWindow().update(0, "1s", duration); fail(duration) }
            catch (_: IllegalArgumentException) { }
        }
    }
}
