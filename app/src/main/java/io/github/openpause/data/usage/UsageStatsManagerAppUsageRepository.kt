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
        val begin = end - windowMillis
        val stats = runCatching {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, end)
        }.getOrNull().orEmpty()

        buildMap {
            for (entry in stats) {
                val millis = entry.totalTimeInForeground
                if (millis <= 0L) continue
                merge(entry.packageName, millis) { a, b -> a + b }
            }
        }
    }
}
