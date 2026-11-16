package io.github.openpause.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRuleDao {
    @Query("SELECT * FROM app_rules ORDER BY appLabel COLLATE NOCASE ASC")
    fun observeRules(): Flow<List<AppRuleEntity>>

    @Query("SELECT * FROM app_rules WHERE enabled = 1")
    fun observeEnabledRules(): Flow<List<AppRuleEntity>>

    @Upsert
    suspend fun upsertRule(rule: AppRuleEntity)

    @Query("UPDATE app_rules SET enabled = :enabled, updatedAtEpochMillis = :updatedAtEpochMillis WHERE packageName = :packageName")
    suspend fun updateEnabled(packageName: String, enabled: Boolean, updatedAtEpochMillis: Long): Int
}
