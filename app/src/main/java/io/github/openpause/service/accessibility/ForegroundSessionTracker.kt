package io.github.openpause.service.accessibility

internal class ForegroundSessionTracker {
    var activeBlockedPackageName: String? = null
        private set

    private var suppressedPackageName: String? = null
    private var suppressedUntilEpochMillis: Long = 0L

    fun onBlockedPackage(
        packageName: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): ForegroundSessionDecision {
        if (isSuppressed(packageName, nowEpochMillis)) {
            return ForegroundSessionDecision.IgnoreForcedCloseEcho
        }

        return if (activeBlockedPackageName == packageName) {
            ForegroundSessionDecision.ContinueCurrentSession
        } else {
            clearExpiredSuppression(nowEpochMillis)
            activeBlockedPackageName = packageName
            ForegroundSessionDecision.StartDelay
        }
    }

    fun suppressForcedCloseEcho(
        packageName: String,
        nowEpochMillis: Long,
        durationMillis: Long,
    ) {
        suppressedPackageName = packageName
        suppressedUntilEpochMillis = nowEpochMillis + durationMillis
    }

    fun endSession() {
        activeBlockedPackageName = null
    }

    private fun isSuppressed(packageName: String, nowEpochMillis: Long): Boolean {
        clearExpiredSuppression(nowEpochMillis)
        if (suppressedPackageName != packageName) return false

        suppressedPackageName = null
        suppressedUntilEpochMillis = 0L
        return true
    }

    private fun clearExpiredSuppression(nowEpochMillis: Long) {
        if (suppressedPackageName != null && nowEpochMillis >= suppressedUntilEpochMillis) {
            suppressedPackageName = null
            suppressedUntilEpochMillis = 0L
        }
    }
}

internal enum class ForegroundSessionDecision {
    StartDelay,
    ContinueCurrentSession,
    IgnoreForcedCloseEcho,
}
