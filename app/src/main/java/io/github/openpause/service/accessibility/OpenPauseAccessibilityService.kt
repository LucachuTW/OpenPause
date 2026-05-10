package io.github.openpause.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import io.github.openpause.domain.model.AppRule
import io.github.openpause.domain.repository.AppRuleRepository
import io.github.openpause.service.overlay.CountdownOverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OpenPauseAccessibilityService : AccessibilityService() {
    @Inject lateinit var repository: AppRuleRepository
    @Inject lateinit var overlayController: CountdownOverlayController

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var enabledRules: Map<String, AppRule> = emptyMap()
    private var activePackageName: String? = null
    private var activeRule: AppRule? = null
    private var activeSessionJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceScope.launch {
            repository.observeEnabledRules()
                .catch { enabledRules = emptyMap() }
                .collect { rules ->
                    enabledRules = rules.associateBy { it.packageName }
                    cancelSessionIfCurrentRuleWasDisabled()
                }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString().orEmpty()
        if (packageName.isBlank() || packageName == applicationContext.packageName) return

        handleForegroundPackage(packageName)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        activeSessionJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleForegroundPackage(packageName: String) {
        if (packageName == activePackageName && activeSessionJob?.isActive == true) return

        val rule = enabledRules[packageName]
        if (rule == null) {
            clearActiveSession()
            activePackageName = packageName
            return
        }

        activeSessionJob?.cancel()
        activePackageName = packageName
        activeRule = rule
        activeSessionJob = serviceScope.launch {
            runRestrictionLoop(rule)
        }
    }

    private suspend fun runRestrictionLoop(rule: AppRule) {
        while (activePackageName == rule.packageName) {
            val overlayCompleted = overlayController.showCountdown(
                appLabel = rule.appLabel,
                waitSeconds = rule.waitTimeSeconds,
            )
            if (!overlayCompleted || activePackageName != rule.packageName) return

            // Accessibility services can be killed and recreated; the timer is intentionally
            // in-memory so stale sessions do not survive a service restart.
            delay(rule.usageLimitMinutes * 60_000L)
        }
    }

    private fun clearActiveSession() {
        activeSessionJob?.cancel()
        activeSessionJob = null
        activeRule = null
    }

    private fun cancelSessionIfCurrentRuleWasDisabled() {
        val activePackage = activePackageName ?: return
        val updatedRule = enabledRules[activePackage]
        when {
            updatedRule == null -> clearActiveSession()
            activeRule != null && activeRule != updatedRule -> {
                activeSessionJob?.cancel()
                activeRule = updatedRule
                activeSessionJob = serviceScope.launch {
                    runRestrictionLoop(updatedRule)
                }
            }
        }
    }
}
