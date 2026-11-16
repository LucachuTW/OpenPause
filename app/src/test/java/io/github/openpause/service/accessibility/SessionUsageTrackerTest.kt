package io.github.openpause.service.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionUsageTrackerTest {
    private val minute = 60_000L
    private val tracker = SessionUsageTracker(carryOverMillis = 5 * minute)

    @Test
    fun aShortBreakResumesTheSameLimit() {
        assertEquals(10 * minute, tracker.start("app", 10 * minute, now = 0))
        tracker.pause(now = 4 * minute)

        assertEquals(6 * minute, tracker.start("app", 10 * minute, now = 5 * minute))
    }

    @Test
    fun aLongBreakOrAnEnforcedLimitStartsOver() {
        tracker.start("app", 10 * minute, now = 0)
        tracker.pause(now = 4 * minute)
        assertEquals(10 * minute, tracker.start("app", 10 * minute, now = 10 * minute))

        tracker.pause(now = 12 * minute)
        tracker.reset("app")
        assertEquals(10 * minute, tracker.start("app", 10 * minute, now = 12 * minute))
    }

    @Test
    fun switchingAppsPausesTheFirstOne() {
        tracker.start("a", 10 * minute, now = 0)
        tracker.start("b", 10 * minute, now = 3 * minute)

        assertEquals(7 * minute, tracker.start("a", 10 * minute, now = 4 * minute))
    }
}
