package io.github.openpause.data.usage

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageAggregationTest {
    @Test
    fun sumsResumeToPauseIntervalsPerPackage() {
        val totals = foregroundMillis(
            transitions = listOf(
                ForegroundTransition("a", "Main", resumed = true, timestamp = 100),
                ForegroundTransition("a", "Main", resumed = false, timestamp = 400),
                ForegroundTransition("b", "Feed", resumed = true, timestamp = 400),
                // In-app screen change where the new resume arrives before the old pause.
                ForegroundTransition("b", "Detail", resumed = true, timestamp = 600),
                ForegroundTransition("b", "Feed", resumed = false, timestamp = 600),
                ForegroundTransition("b", "Detail", resumed = false, timestamp = 800),
                // Still in the foreground when the window ends.
                ForegroundTransition("a", "Main", resumed = true, timestamp = 900),
                // A pause with no resume inside the window is ignored.
                ForegroundTransition("c", "Main", resumed = false, timestamp = 950),
            ),
            endMillis = 1_000,
        )

        assertEquals(mapOf("a" to 400L, "b" to 400L), totals)
    }
}
