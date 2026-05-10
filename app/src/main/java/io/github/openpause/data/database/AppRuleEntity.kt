package io.github.openpause.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.openpause.domain.model.AppRule

@Entity(tableName = "app_rules")
data class AppRuleEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val enabled: Boolean,
    val waitTimeSeconds: Int,
    val usageLimitMinutes: Int,
    val updatedAtEpochMillis: Long,
)

fun AppRuleEntity.toDomain(): AppRule = AppRule(
    packageName = packageName,
    appLabel = appLabel,
    enabled = enabled,
    waitTimeSeconds = waitTimeSeconds,
    usageLimitMinutes = usageLimitMinutes,
)

fun AppRule.toEntity(now: Long): AppRuleEntity = AppRuleEntity(
    packageName = packageName,
    appLabel = appLabel,
    enabled = enabled,
    waitTimeSeconds = waitTimeSeconds,
    usageLimitMinutes = usageLimitMinutes,
    updatedAtEpochMillis = now,
)
