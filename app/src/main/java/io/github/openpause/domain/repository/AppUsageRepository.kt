package io.github.openpause.domain.repository

interface AppUsageRepository {
    /**
     * Returns total foreground millis per package over the last [windowMillis].
     * Packages with no measurable foreground time are omitted.
     */
    suspend fun getUsageMillisSince(windowMillis: Long): Map<String, Long>
}
