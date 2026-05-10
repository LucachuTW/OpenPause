package io.github.openpause.domain.usecase

import io.github.openpause.domain.model.AppRule
import io.github.openpause.domain.repository.AppRuleRepository
import javax.inject.Inject

class SaveAppRuleUseCase @Inject constructor(
    private val repository: AppRuleRepository,
) {
    suspend operator fun invoke(rule: AppRule) {
        repository.upsertRule(
            rule.copy(
                waitTimeSeconds = RuleConstraints.sanitizeWaitSeconds(rule.waitTimeSeconds),
                usageLimitMinutes = RuleConstraints.sanitizeUsageMinutes(rule.usageLimitMinutes),
            ),
        )
    }
}
