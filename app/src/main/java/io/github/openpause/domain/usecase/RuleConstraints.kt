package io.github.openpause.domain.usecase

object RuleConstraints {
    const val DefaultWaitSeconds = 10
    const val DefaultUsageMinutes = 15
    const val MaxWaitSeconds = 120
    const val MaxUsageMinutes = 240

    fun sanitizeWaitSeconds(value: Int): Int = value.coerceIn(0, MaxWaitSeconds)

    fun sanitizeUsageMinutes(value: Int): Int = value.coerceIn(1, MaxUsageMinutes)
}
