package io.github.openpause.service.accessibility

/**
 * Time spent in each blocked app against its usage limit. Leaving the app only pauses the
 * count: coming back within [carryOverMillis] resumes where it stopped, so a quick trip to
 * another app cannot be used to get a fresh limit. A longer break starts over.
 */
internal class SessionUsageTracker(private val carryOverMillis: Long) {
    private class Entry(var usedMillis: Long, var stoppedAt: Long)

    private val entries = HashMap<String, Entry>()
    private var running: String? = null
    private var startedAt = 0L

    /** Starts counting for [packageName] and returns the millis left before its limit. */
    fun start(packageName: String, limitMillis: Long, now: Long): Long {
        pause(now)
        val entry = entries[packageName]?.takeIf { now - it.stoppedAt <= carryOverMillis }
            ?: Entry(usedMillis = 0L, stoppedAt = now).also { entries[packageName] = it }
        running = packageName
        startedAt = now
        return (limitMillis - entry.usedMillis).coerceAtLeast(0L)
    }

    fun pause(now: Long) {
        val entry = entries[running ?: return]
        running = null
        if (entry != null) {
            entry.usedMillis += now - startedAt
            entry.stoppedAt = now
        }
    }

    /** The limit was enforced, so the next open gets a full one. */
    fun reset(packageName: String) {
        entries.remove(packageName)
        if (running == packageName) running = null
    }
}
