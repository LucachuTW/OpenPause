package io.github.openpause.data.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.openpause.domain.repository.AppUsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class UsageStatsManagerAppUsageRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AppUsageRepository {
    override suspend fun getUsageMillisSince(windowMillis: Long): Map<String, Long> = withContext(Dispatchers.IO) {
        val manager = context.getSystemService<UsageStatsManager>() ?: return@withContext emptyMap()
        val end = System.currentTimeMillis()
        // Events, not queryAndAggregateUsageStats: that one returns whole daily buckets
        // touching the range, which over-reports a 24h window by up to a day.
        val transitions = runCatching {
            val events = manager.queryEvents(end - windowMillis, end)
            val event = UsageEvents.Event()
            buildList {
                while (events.getNextEvent(event)) {
                    when (event.eventType) {
                        ActivityResumed -> add(transition(event, resumed = true))
                        ActivityPaused -> add(transition(event, resumed = false))
                    }
                }
            }
        }.getOrNull().orEmpty()

        foregroundMillis(transitions, end)
    }

    private fun transition(event: UsageEvents.Event, resumed: Boolean) =
        ForegroundTransition(event.packageName, event.className.orEmpty(), resumed, event.timeStamp)

    private companion object {
        // UsageEvents.Event.ACTIVITY_RESUMED / ACTIVITY_PAUSED; same values as the pre-29 names.
        private const val ActivityResumed = 1
        private const val ActivityPaused = 2
    }
}

internal data class ForegroundTransition(
    val packageName: String,
    val activity: String,
    val resumed: Boolean,
    val timestamp: Long,
)

/**
 * Total foreground millis per package from time-ordered resume/pause transitions.
 * Intervals are tracked per activity, so an in-app screen change (whose resume and pause
 * can arrive in either order) never drops time. An activity still resumed at the end counts
 * up to [endMillis]; packages with no measurable time are omitted.
 */
// ponytail: time already in the foreground before the window start is not counted.
internal fun foregroundMillis(
    transitions: List<ForegroundTransition>,
    endMillis: Long,
): Map<String, Long> {
    val resumedAt = HashMap<Pair<String, String>, Long>()
    val totals = HashMap<String, Long>()
    fun close(key: Pair<String, String>, at: Long) {
        val start = resumedAt.remove(key) ?: return
        if (at > start) totals[key.first] = (totals[key.first] ?: 0L) + (at - start)
    }
    for (transition in transitions) {
        val key = transition.packageName to transition.activity
        close(key, transition.timestamp)
        if (transition.resumed) resumedAt[key] = transition.timestamp
    }
    resumedAt.keys.toList().forEach { close(it, endMillis) }
    return totals
}
