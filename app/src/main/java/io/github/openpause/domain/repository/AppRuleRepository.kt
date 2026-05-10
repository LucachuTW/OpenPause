package io.github.openpause.domain.repository

import io.github.openpause.domain.model.AppRule
import kotlinx.coroutines.flow.Flow

interface AppRuleRepository {
    fun observeRules(): Flow<List<AppRule>>
    fun observeEnabledRules(): Flow<List<AppRule>>
    suspend fun getEnabledRule(packageName: String): AppRule?
    suspend fun upsertRule(rule: AppRule)
    suspend fun setRuleEnabled(packageName: String, enabled: Boolean)
}
