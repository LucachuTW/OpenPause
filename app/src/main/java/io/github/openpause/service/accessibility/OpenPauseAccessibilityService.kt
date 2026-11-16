package io.github.openpause.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.openpause.domain.model.AppRule
import io.github.openpause.domain.repository.AppRuleRepository
import io.github.openpause.service.overlay.CountdownOverlayController
import io.github.openpause.service.overlay.CountdownResult
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
    private var activeDelayJob: Job? = null
    private var activeUsageLimitJob: Job? = null
    private var pendingSessionEndJob: Job? = null

    /** Packages that are never a "real app the user switched to": our own overlay and the
     *  framework resolver/share sheet. Keeping the current session for these avoids
     *  re-triggering the countdown when a share sheet or our overlay is on screen. */
    private val ignoredForegroundPackages: Set<String> by lazy {
        setOf(applicationContext.packageName, AndroidFrameworkPackage)
    }

    /** A locked screen ends the session, so unlocking into a blocked app counts as a fresh open. */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> clearActiveSession()
                Intent.ACTION_USER_PRESENT -> handleForegroundPackage(currentForegroundPackage(fallback = ""))
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
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

        val eventPackage = event.packageName?.toString().orEmpty()
        if (eventPackage == applicationContext.packageName) return

        handleForegroundPackage(currentForegroundPackage(fallback = eventPackage))
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        activeDelayJob?.cancel()
        activeUsageLimitJob?.cancel()
        pendingSessionEndJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * The real foreground app, read from the window list rather than the raw event package.
     * This is what distinguishes "the user switched apps" from helper windows (keyboard,
     * system UI, ad frames, our overlay) that fire window events without a real app change.
     */
    private fun currentForegroundPackage(fallback: String): String {
        val snapshots = runCatching {
            windows.map { WindowSnapshot(it.type, it.layer, it.root?.packageName?.toString()) }
        }.getOrNull()
        if (snapshots.isNullOrEmpty()) return fallback
        return topApplicationPackage(snapshots, ignoredForegroundPackages) ?: fallback
    }

    private fun handleForegroundPackage(packageName: String) {
        if (packageName.isBlank() || packageName in ignoredForegroundPackages) return

        val rule = enabledRules[packageName]
        if (rule == null) {
            sessionTracker.clearSuppression()
            // A different real app (the launcher or any other app) is in the foreground, so
            // end the blocked session after a short grace; returning to the app then counts
            // as a fresh open. Same-package screens (Story/Reel/Shorts/DM/ads) and helper
            // windows never reach here, so internal navigation can't re-trigger the countdown.
            scheduleActiveSessionEnd()
            return
        }

        pendingSessionEndJob?.cancel()
        pendingSessionEndJob = null

        when (sessionTracker.onBlockedPackage(packageName)) {
            ForegroundSessionDecision.StartDelay -> startDelayForRule(rule)
            ForegroundSessionDecision.ContinueCurrentSession,
            ForegroundSessionDecision.IgnoreForcedCloseEcho,
            -> Unit
        }
    }

    private fun startDelayForRule(rule: AppRule) {
        activeDelayJob?.cancel()
        activeUsageLimitJob?.cancel()
        activeDelayJob = serviceScope.launch {
            val result = overlayController.showCountdown(
                appLabel = rule.appLabel,
                waitSeconds = rule.waitTimeSeconds,
            )
            if (sessionTracker.activeBlockedPackageName != rule.packageName) return@launch
            when (result) {
                // NotShown: no overlay permission, but the usage limit is still enforced.
                CountdownResult.Completed, CountdownResult.NotShown -> startUsageLimitForRule(rule)
                CountdownResult.Dismissed -> closeBlockedApp(rule)
            }
        }
    }

    private fun startUsageLimitForRule(rule: AppRule) {
        activeUsageLimitJob?.cancel()
        activeUsageLimitJob = serviceScope.launch {
            delay(rule.usageLimitMinutes * 60_000L)
            if (sessionTracker.activeBlockedPackageName == rule.packageName) {
                closeBlockedApp(rule)
            }
        }
    }

    private fun closeBlockedApp(rule: AppRule) {
        sessionTracker.suppressForcedCloseEcho(
            packageName = rule.packageName,
            nowEpochMillis = System.currentTimeMillis(),
            durationMillis = ForcedCloseEchoSuppressionMillis,
        )
        performGlobalAction(GLOBAL_ACTION_HOME)
        clearActiveSession()
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
        sessionTracker.endSession()
    }

    private fun cancelSessionIfCurrentRuleWasDisabled() {
        val activePackage = sessionTracker.activeBlockedPackageName ?: return
        if (activePackage !in enabledRules) {
            clearActiveSession()
        }
    }

    private companion object {
        private const val AndroidFrameworkPackage = "android"
        private const val SessionEndGraceMillis = 1_000L
        private const val ForcedCloseEchoSuppressionMillis = 5_000L
    }
}
