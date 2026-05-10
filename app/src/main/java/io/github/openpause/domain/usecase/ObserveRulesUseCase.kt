package io.github.openpause.domain.usecase

import io.github.openpause.domain.model.AppRule
import io.github.openpause.domain.repository.AppRuleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveRulesUseCase @Inject constructor(
    private val repository: AppRuleRepository,
) {
    operator fun invoke(): Flow<List<AppRule>> = repository.observeRules()
}
