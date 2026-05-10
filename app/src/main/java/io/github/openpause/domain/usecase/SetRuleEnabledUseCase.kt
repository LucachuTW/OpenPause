package io.github.openpause.domain.usecase

import io.github.openpause.domain.repository.AppRuleRepository
import javax.inject.Inject

class SetRuleEnabledUseCase @Inject constructor(
    private val repository: AppRuleRepository,
) {
    suspend operator fun invoke(packageName: String, enabled: Boolean) {
        repository.setRuleEnabled(packageName = packageName, enabled = enabled)
    }
}
