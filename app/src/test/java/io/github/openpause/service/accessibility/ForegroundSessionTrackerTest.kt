package io.github.openpause.service.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundSessionTrackerTest {
    @Test
    fun firstBlockedPackageStartsDelay() {
        val tracker = ForegroundSessionTracker()

        assertEquals(
            ForegroundSessionDecision.StartDelay,
            tracker.onBlockedPackage("com.instagram.android"),
        )
    }

    @Test
    fun sameBlockedPackageContinuesCurrentSession() {
        val tracker = ForegroundSessionTracker()

        tracker.onBlockedPackage("com.instagram.android")

        assertEquals(
            ForegroundSessionDecision.ContinueCurrentSession,
            tracker.onBlockedPackage("com.instagram.android"),
        )
    }

    @Test
    fun differentBlockedPackageStartsNewDelay() {
        val tracker = ForegroundSessionTracker()

        tracker.onBlockedPackage("com.instagram.android")

        assertEquals(
            ForegroundSessionDecision.StartDelay,
            tracker.onBlockedPackage("com.whatsapp"),
        )
    }

    @Test
    fun endedSessionStartsDelayAgainForSamePackage() {
        val tracker = ForegroundSessionTracker()

        tracker.onBlockedPackage("com.instagram.android", nowEpochMillis = 1_000L)
        tracker.endSession()

        assertEquals(
            ForegroundSessionDecision.StartDelay,
            tracker.onBlockedPackage("com.instagram.android", nowEpochMillis = 2_000L),
        )
    }

    @Test
    fun endSessionClearsActiveBlockedPackage() {
        val tracker = ForegroundSessionTracker()

        tracker.onBlockedPackage("com.instagram.android")
        tracker.endSession()

        assertNull(tracker.activeBlockedPackageName)
    }

    @Test
    fun forcedCloseEchoForSamePackageIsIgnoredOnceDuringSuppressionWindow() {
        val tracker = ForegroundSessionTracker()

        tracker.onBlockedPackage("com.whatsapp", nowEpochMillis = 1_000L)
        tracker.suppressForcedCloseEcho(
            packageName = "com.whatsapp",
            nowEpochMillis = 2_000L,
            durationMillis = 5_000L,
        )
        tracker.endSession()

        assertEquals(
            ForegroundSessionDecision.IgnoreForcedCloseEcho,
            tracker.onBlockedPackage("com.whatsapp", nowEpochMillis = 3_000L),
        )
    }

    @Test
    fun samePackageStartsDelayAfterSuppressionIsConsumed() {
        val tracker = ForegroundSessionTracker()

        tracker.onBlockedPackage("com.whatsapp", nowEpochMillis = 1_000L)
        tracker.suppressForcedCloseEcho(
            packageName = "com.whatsapp",
            nowEpochMillis = 2_000L,
            durationMillis = 5_000L,
        )
        tracker.endSession()

        assertEquals(
            ForegroundSessionDecision.IgnoreForcedCloseEcho,
            tracker.onBlockedPackage("com.whatsapp", nowEpochMillis = 3_000L),
        )

        assertEquals(
            ForegroundSessionDecision.StartDelay,
            tracker.onBlockedPackage("com.whatsapp", nowEpochMillis = 3_100L),
        )
    }

    @Test
    fun differentBlockedPackageStartsDelayDuringSuppressionWindow() {
        val tracker = ForegroundSessionTracker()

        tracker.onBlockedPackage("com.whatsapp", nowEpochMillis = 1_000L)
        tracker.suppressForcedCloseEcho(
            packageName = "com.whatsapp",
            nowEpochMillis = 2_000L,
            durationMillis = 5_000L,
        )
        tracker.endSession()

        assertEquals(
            ForegroundSessionDecision.StartDelay,
            tracker.onBlockedPackage("com.instagram.android", nowEpochMillis = 3_000L),
        )
    }

    @Test
    fun clearSuppressionMakesReopenStartDelay() {
        val tracker = ForegroundSessionTracker()
        tracker.onBlockedPackage("com.instagram.android", nowEpochMillis = 0L)
        tracker.suppressForcedCloseEcho("com.instagram.android", nowEpochMillis = 0L, durationMillis = 5_000L)
        tracker.endSession()

        tracker.clearSuppression()

        assertEquals(
            ForegroundSessionDecision.StartDelay,
            tracker.onBlockedPackage("com.instagram.android", nowEpochMillis = 1_000L),
        )
    }
}
