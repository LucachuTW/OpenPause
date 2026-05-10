package io.github.openpause.data.repository

import io.github.openpause.data.database.AppRuleDao
import io.github.openpause.data.database.toDomain
import io.github.openpause.data.database.toEntity
import io.github.openpause.domain.model.AppRule
import io.github.openpause.domain.repository.AppRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomAppRuleRepository @Inject constructor(
    private val dao: AppRuleDao,
) : AppRuleRepository {
    override fun observeRules(): Flow<List<AppRule>> = dao.observeRules()
        .map { rules -> rules.map { it.toDomain() } }

    override fun observeEnabledRules(): Flow<List<AppRule>> = dao.observeEnabledRules()
        .map { rules -> rules.map { it.toDomain() } }

    override suspend fun getEnabledRule(packageName: String): AppRule? {
        return dao.getEnabledRule(packageName)?.toDomain()
    }

    override suspend fun upsertRule(rule: AppRule) {
        dao.upsertRule(rule.toEntity(now = System.currentTimeMillis()))
    }

    override suspend fun setRuleEnabled(packageName: String, enabled: Boolean) {
        dao.updateEnabled(
            packageName = packageName,
            enabled = enabled,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
    }
}
