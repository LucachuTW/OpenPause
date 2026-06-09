package io.github.openpause.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.openpause.data.catalog.PackageManagerAppCatalogRepository
import io.github.openpause.data.repository.RoomAppRuleRepository
import io.github.openpause.data.usage.UsageStatsManagerAppUsageRepository
import io.github.openpause.domain.repository.AppCatalogRepository
import io.github.openpause.domain.repository.AppRuleRepository
import io.github.openpause.domain.repository.AppUsageRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindAppRuleRepository(repository: RoomAppRuleRepository): AppRuleRepository

    @Binds
    @Singleton
    abstract fun bindAppCatalogRepository(repository: PackageManagerAppCatalogRepository): AppCatalogRepository

    @Binds
    @Singleton
    abstract fun bindAppUsageRepository(repository: UsageStatsManagerAppUsageRepository): AppUsageRepository
}
