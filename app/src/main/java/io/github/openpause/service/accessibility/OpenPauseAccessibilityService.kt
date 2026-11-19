package io.github.openpause.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.openpause.R
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
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OpenPauseAccessibilityService : AccessibilityService() {
    @Inject lateinit var repository: AppRuleRepository
    @Inject lateinit var overlayController: CountdownOverlayController

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var enabledRules: Map<String, AppRule> = emptyMap()
    private val sessionTracker = ForegroundSessionTracker()
    private val usageTracker = SessionUsageTracker(UsageCarryOverMillis)
    private var activeDelayJob: Job? = null
    private var activeUsageLimitJob: Job? = null
    private var pendingCheckJob: Job? = null

    /** A locked screen ends the session, so unlocking into a blocked app counts as a fresh open. */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    pendingCheckJob?.cancel()
                    clearActiveSession()
                }
                // With no lock screen USER_PRESENT never arrives, so SCREEN_ON is the unlock;
                // the check itself skips while the keyguard is still up.
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> scheduleForegroundCheck()
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
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        serviceScope.launch {
            repository.observeEnabledRules()
                // Keep the last known rules and try again; giving up would silently stop blocking.
                .retry {
                    delay(RulesRetryMillis)
                    true
                }
                .collect { rules ->
                    val previousRules = enabledRules
                    enabledRules = rules.associateBy { it.packageName }
                    cancelSessionIfCurrentRuleWasDisabled()
                    applyChangedUsageLimit(previousRules)
                }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() == applicationContext.packageName) return

        // The event only says "something changed". It fires for the keyboard, heads-up
        // notifications, dialogs and launch animations too, so the decision is made from the
        // window stack once the burst has settled, never from the event's package.
        scheduleForegroundCheck()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        activeDelayJob?.cancel()
        activeUsageLimitJob?.cancel()
        pendingCheckJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    /** Replaces any pending check, so a burst of events leads to one read of the settled stack. */
    private fun scheduleForegroundCheck(
        delayMillis: Long = SettleMillis,
        retriesLeft: Int = ConfirmRetries,
    ) {
        pendingCheckJob?.cancel()
        pendingCheckJob = serviceScope.launch {
            delay(delayMillis)
            evaluateForeground(retriesLeft)
        }
    }

    private fun evaluateForeground(retriesLeft: Int) {
        if (isKeyguardLocked()) return
        val snapshots = windowSnapshots() ?: return
        val top = topApplicationPackage(snapshots, ignoredPackages())
        if (top == null) {
            // Mid-transition every app window can briefly report no content.
            retryForegroundCheck(retriesLeft)
            return
        }

        val rule = enabledRules[top]
        if (rule != null) {
            when (sessionTracker.onBlockedPackage(top)) {
                ForegroundSessionDecision.StartDelay -> startDelayForRule(rule)
                ForegroundSessionDecision.ContinueCurrentSession,
                ForegroundSessionDecision.IgnoreForcedCloseEcho,
                -> Unit
            }
            return
        }

        sessionTracker.clearSuppression()
        val active = sessionTracker.activeBlockedPackageName ?: return
        // A window of another app over the still-visible blocked app (a sheet, dialog or
        // sign-in popup) rides on its session. A replaced app drops out of the window list
        // once covered, but it can still be animating out, so look again before ending.
        if (mayStillBeOnScreen(snapshots, active)) {
            retryForegroundCheck(retriesLeft)
            return
        }
        // The user really is somewhere else; coming back counts as a fresh open.
        clearActiveSession()
    }

    /** Growing delays so a slow exit animation cannot outlast the retries. */
    private fun retryForegroundCheck(retriesLeft: Int) {
        if (retriesLeft <= 0) return
        val attempt = ConfirmRetries - retriesLeft + 1
        scheduleForegroundCheck(SettleMillis shl attempt, retriesLeft - 1)
    }

    private fun windowSnapshots(): List<WindowSnapshot>? = runCatching {
        windows.map {
            WindowSnapshot(
                type = it.type,
                layer = it.layer,
                packageName = it.root?.packageName?.toString(),
                pictureInPicture = it.isInPictureInPictureMode,
            )
        }
    }.getOrNull()

    /** Packages that are never "the app the user switched to": our own windows, System UI, the
     *  keyboard, and system screens an app opens on its own behalf (share sheet, permission
     *  dialog, photo/file picker), so attaching a photo from a chat keeps the session. */
    private fun ignoredPackages(): Set<String> {
        val ime = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')
        return SystemHelperPackages + listOfNotNull(applicationContext.packageName, ime)
    }

    private fun isKeyguardLocked(): Boolean =
        getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    private fun startDelayForRule(rule: AppRule) {
        activeDelayJob?.cancel()
        activeUsageLimitJob?.cancel()
        usageTracker.pause(SystemClock.elapsedRealtime())
        activeDelayJob = serviceScope.launch {
            val result = overlayController.showCountdown(
                windowContext = this@OpenPauseAccessibilityService,
                appLabel = rule.appLabel,
                waitSeconds = rule.waitTimeSeconds,
            )
            if (sessionTracker.activeBlockedPackageName != rule.packageName) return@launch
            when (result) {
                // NotShown: the overlay could not be added, but the usage limit is still enforced.
                CountdownResult.Completed, CountdownResult.NotShown -> startUsageLimitForRule(rule)
                CountdownResult.Dismissed -> closeBlockedApp(rule)
            }
        }
    }

    private fun startUsageLimitForRule(rule: AppRule) {
        activeUsageLimitJob?.cancel()
        val remainingMillis = usageTracker.start(
            packageName = rule.packageName,
            limitMillis = rule.usageLimitMinutes * 60_000L,
            now = SystemClock.elapsedRealtime(),
        )
        activeUsageLimitJob = serviceScope.launch {
            if (remainingMillis > UsageWarningMillis) {
                delay(remainingMillis - UsageWarningMillis)
                launch {
                    overlayController.showNotice(
                        windowContext = this@OpenPauseAccessibilityService,
                        text = getString(R.string.usage_limit_warning, rule.appLabel),
                    )
                }
                delay(UsageWarningMillis)
            } else {
                delay(remainingMillis)
            }
            if (sessionTracker.activeBlockedPackageName == rule.packageName) {
                usageTracker.reset(rule.packageName)
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

    private fun clearActiveSession() {
        activeDelayJob?.cancel()
        activeDelayJob = null
        activeUsageLimitJob?.cancel()
        activeUsageLimitJob = null
        usageTracker.pause(SystemClock.elapsedRealtime())
        sessionTracker.endSession()
    }

    private fun cancelSessionIfCurrentRuleWasDisabled() {
        val activePackage = sessionTracker.activeBlockedPackageName ?: return
        if (activePackage !in enabledRules) {
            clearActiveSession()
        }
    }

    /** An edited limit applies to the session already running, not just the next one. */
    private fun applyChangedUsageLimit(previousRules: Map<String, AppRule>) {
        val activePackage = sessionTracker.activeBlockedPackageName ?: return
        val rule = enabledRules[activePackage] ?: return
        val limitChanged = previousRules[rule.packageName]?.usageLimitMinutes != rule.usageLimitMinutes
        if (limitChanged && activeUsageLimitJob?.isActive == true) {
            startUsageLimitForRule(rule)
        }
    }

    private companion object {
        private val SystemHelperPackages = setOf(
            "android", // resolver / share sheet up to Android 13
            "com.android.intentresolver", // share sheet on Android 14+
            "com.android.systemui", // shade, heads-up notifications, toasts
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.providers.media.module", // photo picker
            "com.google.android.providers.media.module",
            "com.android.documentsui",
            "com.google.android.documentsui",
        )
        private const val SettleMillis = 250L
        private const val ConfirmRetries = 2
        private const val ForcedCloseEchoSuppressionMillis = 5_000L
        private const val RulesRetryMillis = 1_000L
        private const val UsageWarningMillis = 60_000L
        private const val UsageCarryOverMillis = 5 * 60_000L
    }
}
