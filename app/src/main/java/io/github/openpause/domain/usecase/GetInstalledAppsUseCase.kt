package io.github.openpause.domain.usecase

import io.github.openpause.domain.model.InstalledApp
import io.github.openpause.domain.repository.AppCatalogRepository
import javax.inject.Inject

class GetInstalledAppsUseCase @Inject constructor(
    private val repository: AppCatalogRepository,
) {
    suspend operator fun invoke(): List<InstalledApp> = repository.getLaunchableApps()
}
