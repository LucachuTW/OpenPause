package io.github.openpause.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.openpause.domain.model.AppRule
import io.github.openpause.domain.model.InstalledApp
import io.github.openpause.domain.usecase.GetInstalledAppsUseCase
import io.github.openpause.domain.usecase.ObserveRulesUseCase
import io.github.openpause.domain.usecase.RuleConstraints
import io.github.openpause.domain.usecase.SaveAppRuleUseCase
import io.github.openpause.domain.usecase.SetRuleEnabledUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeRulesUseCase: ObserveRulesUseCase,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val saveAppRuleUseCase: SaveAppRuleUseCase,
    private val setRuleEnabledUseCase: SetRuleEnabledUseCase,
) : ViewModel() {
    private val installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val query = MutableStateFlow("")
    private val loading = MutableStateFlow(true)

    val uiState = combine(
        installedApps,
        observeRulesUseCase(),
        query,
        loading,
    ) { apps, rules, searchQuery, isLoading ->
        val rulesByPackage = rules.associateBy { it.packageName }
        val maxUsageMillis = apps.maxOfOrNull { it.usageMillis } ?: 0L
        val filteredApps = apps
            .filter { app ->
                searchQuery.isBlank() ||
                    app.label.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            }
            .map { app ->
                val rule = rulesByPackage[app.packageName]
                AppRuleUiModel(
                    packageName = app.packageName,
                    label = rule?.appLabel ?: app.label,
                    enabled = rule?.enabled ?: false,
                    waitTimeSeconds = rule?.waitTimeSeconds ?: RuleConstraints.DefaultWaitSeconds,
                    usageLimitMinutes = rule?.usageLimitMinutes ?: RuleConstraints.DefaultUsageMinutes,
                    hasSavedRule = rule != null,
                    usageMillis = app.usageMillis,
                )
            }

        HomeUiState(
            isLoading = isLoading,
            query = searchQuery,
            apps = filteredApps,
            maxUsageMillis = maxUsageMillis,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    init {
        refreshInstalledApps()
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            loading.value = true
            installedApps.value = getInstalledAppsUseCase()
            loading.value = false
        }
    }

    fun updateQuery(value: String) {
        query.value = value
    }

    fun setEnabled(app: AppRuleUiModel, enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !app.hasSavedRule) {
                saveAppRuleUseCase(app.toDomain(enabled = true))
            } else {
                setRuleEnabledUseCase(app.packageName, enabled)
            }
        }
    }

    fun saveTimers(app: AppRuleUiModel, waitSeconds: Int, usageMinutes: Int) {
        viewModelScope.launch {
            saveAppRuleUseCase(
                app.toDomain(
                    enabled = true,
                    waitSeconds = waitSeconds,
                    usageMinutes = usageMinutes,
                ),
            )
        }
    }
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val apps: List<AppRuleUiModel> = emptyList(),
    val maxUsageMillis: Long = 0L,
)

data class AppRuleUiModel(
    val packageName: String,
    val label: String,
    val enabled: Boolean,
    val waitTimeSeconds: Int,
    val usageLimitMinutes: Int,
    val hasSavedRule: Boolean,
    val usageMillis: Long = 0L,
)

private fun AppRuleUiModel.toDomain(
    enabled: Boolean,
    waitSeconds: Int = waitTimeSeconds,
    usageMinutes: Int = usageLimitMinutes,
): AppRule = AppRule(
    packageName = packageName,
    appLabel = label,
    enabled = enabled,
    waitTimeSeconds = waitSeconds,
    usageLimitMinutes = usageMinutes,
)
