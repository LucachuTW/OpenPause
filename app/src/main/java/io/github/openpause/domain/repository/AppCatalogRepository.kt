package io.github.openpause.domain.repository

import io.github.openpause.domain.model.InstalledApp

interface AppCatalogRepository {
    suspend fun getLaunchableApps(): List<InstalledApp>
}
