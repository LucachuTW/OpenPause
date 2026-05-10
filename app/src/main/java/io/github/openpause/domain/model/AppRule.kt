package io.github.openpause.domain.model

data class AppRule(
    val packageName: String,
    val appLabel: String,
    val enabled: Boolean,
    val waitTimeSeconds: Int,
    val usageLimitMinutes: Int,
)
