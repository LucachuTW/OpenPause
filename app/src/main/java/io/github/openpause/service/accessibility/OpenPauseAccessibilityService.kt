package io.github.openpause.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.provider.Settings
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
    private val sessionTracker = ForegroundSessionTracker()
    private var activeRule: AppRule? = null
    private var activeDelayJob: Job? = null
    private var activeUsageLimitJob: Job? = null
    private var pendingSessionEndJob: Job? = null

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
        activeDelayJob?.cancel()
        activeUsageLimitJob?.cancel()
        pendingSessionEndJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleForegroundPackage(packageName: String) {
        val rule = enabledRules[packageName]
        if (rule == null) {
            if (!isTransientForegroundPackage(packageName)) {
                scheduleActiveSessionEnd()
            }
            return
        }

        pendingSessionEndJob?.cancel()
        pendingSessionEndJob = null

        when (sessionTracker.onBlockedPackage(packageName)) {
            ForegroundSessionDecision.ContinueCurrentSession -> {
                activeRule = rule
            }
            ForegroundSessionDecision.StartDelay -> {
                startDelayForRule(rule)
            }
            ForegroundSessionDecision.IgnoreForcedCloseEcho -> Unit
        }
    }

    private fun startDelayForRule(rule: AppRule) {
        activeDelayJob?.cancel()
        activeUsageLimitJob?.cancel()
        activeRule = rule
        activeDelayJob = serviceScope.launch {
            val overlayCompleted = overlayController.showCountdown(
                appLabel = rule.appLabel,
                waitSeconds = rule.waitTimeSeconds,
            )
            if (overlayCompleted && sessionTracker.activeBlockedPackageName == rule.packageName) {
                startUsageLimitForRule(rule)
            }
        }
    }

    private fun startUsageLimitForRule(rule: AppRule) {
        activeUsageLimitJob?.cancel()
        activeUsageLimitJob = serviceScope.launch {
            delay(rule.usageLimitMinutes * 60_000L)
            if (sessionTracker.activeBlockedPackageName == rule.packageName) {
                sessionTracker.suppressForcedCloseEcho(
                    packageName = rule.packageName,
                    nowEpochMillis = System.currentTimeMillis(),
                    durationMillis = ForcedCloseEchoSuppressionMillis,
                )
                performGlobalAction(GLOBAL_ACTION_HOME)
                scheduleActiveSessionEnd()
            }
        }
    }

    private fun scheduleActiveSessionEnd() {
        if (sessionTracker.activeBlockedPackageName == null) return

        pendingSessionEndJob?.cancel()
        pendingSessionEndJob = serviceScope.launch {
            delay(SessionEndGraceMillis)
            clearActiveSession()
        }
    }

    private fun clearActiveSession() {
        activeDelayJob?.cancel()
        activeDelayJob = null
        activeUsageLimitJob?.cancel()
        activeUsageLimitJob = null
        pendingSessionEndJob?.cancel()
        pendingSessionEndJob = null
        activeRule = null
        sessionTracker.endSession()
    }

    private fun cancelSessionIfCurrentRuleWasDisabled() {
        val activePackage = sessionTracker.activeBlockedPackageName ?: return
        val updatedRule = enabledRules[activePackage]
        if (updatedRule == null) {
            clearActiveSession()
        } else {
            activeRule = updatedRule
        }
    }

    private fun isTransientForegroundPackage(packageName: String): Boolean {
        return packageName == AndroidFrameworkPackage ||
            packageName == SystemUiPackage ||
            packageName == currentInputMethodPackageName()
    }

    private fun currentInputMethodPackageName(): String? {
        val inputMethodId = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD,
        ) ?: return null

        return ComponentName.unflattenFromString(inputMethodId)?.packageName
            ?: inputMethodId.substringBefore('/', missingDelimiterValue = inputMethodId)
    }

    private companion object {
        private const val AndroidFrameworkPackage = "android"
        private const val SystemUiPackage = "com.android.systemui"
        private const val SessionEndGraceMillis = 750L
        private const val ForcedCloseEchoSuppressionMillis = 5_000L
    }
}
