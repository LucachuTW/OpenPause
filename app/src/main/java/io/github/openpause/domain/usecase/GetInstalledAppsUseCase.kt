package io.github.openpause.domain.usecase

import io.github.openpause.domain.model.InstalledApp
import io.github.openpause.domain.repository.AppCatalogRepository
import io.github.openpause.domain.repository.AppUsageRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class GetInstalledAppsUseCase @Inject constructor(
    private val catalog: AppCatalogRepository,
    private val usage: AppUsageRepository,
) {
    suspend operator fun invoke(): List<InstalledApp> {
        val apps = catalog.getLaunchableApps()
        val usageByPackage = usage.getUsageMillisSince(DefaultUsageWindowMillis)
        return apps
            .map { app -> app.copy(usageMillis = usageByPackage[app.packageName] ?: 0L) }
            .sortedWith(
                compareByDescending<InstalledApp> { it.usageMillis }
                    .thenBy { it.label.lowercase() },
            )
    }

    companion object {
        val DefaultUsageWindowMillis: Long = TimeUnit.DAYS.toMillis(1)
    }
}
