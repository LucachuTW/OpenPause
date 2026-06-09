package io.github.openpause.data.usage

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
        val stats = runCatching {
            manager.queryAndAggregateUsageStats(end - windowMillis, end)
        }.getOrNull().orEmpty()

        buildMap {
            for ((packageName, usage) in stats) {
                val millis = usage.totalTimeInForeground
                if (millis > 0L) put(packageName, millis)
            }
        }
    }
}
