package io.github.openpause.service.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.openpause.R
import io.github.openpause.core.settings.OverlayPreferences
import io.github.openpause.presentation.theme.OpenPauseTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CountdownOverlayController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val overlayMutex = Mutex()

    fun canDrawOverlay(): Boolean = Settings.canDrawOverlays(context)

    suspend fun showCountdown(appLabel: String, waitSeconds: Int): CountdownResult {
        if (waitSeconds <= 0) return CountdownResult.Completed
        if (!canDrawOverlay()) return CountdownResult.NotShown

        val coverBackground = OverlayPreferences.coverBackground(context)
        return overlayMutex.withLock {
            withContext(Dispatchers.Main.immediate) {
                val secondsLeft = mutableIntStateOf(waitSeconds)
                val closeRequested = CompletableDeferred<Unit>()
                val lifecycleOwner = OverlayLifecycleOwner().also { it.start() }
                val composeView = ComposeView(context).apply {
                    setViewTreeLifecycleOwner(lifecycleOwner)
                    setViewTreeViewModelStoreOwner(lifecycleOwner)
                    setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                    setContent {
                        // The backdrop is always black, so use the dark palette for contrast.
                        OpenPauseTheme(darkTheme = true) {
                            CountdownOverlay(
                                appLabel = appLabel,
                                secondsLeft = secondsLeft.intValue,
                                totalSeconds = waitSeconds,
                                coverBackground = coverBackground,
                                onCloseApp = { closeRequested.complete(Unit) },
                            )
                        }
                    }
                }

                val added = runCatching {
                    windowManager.addView(composeView, overlayLayoutParams())
                }.isSuccess
                if (!added) {
                    lifecycleOwner.destroy()
                    return@withContext CountdownResult.NotShown
                }

                try {
                    coroutineScope {
                        val countdown = launch {
                            for (remaining in waitSeconds downTo 1) {
                                secondsLeft.intValue = remaining
                                delay(1_000)
                            }
                        }
                        select {
                            countdown.onJoin { CountdownResult.Completed }
                            closeRequested.onAwait {
                                countdown.cancel()
                                CountdownResult.Dismissed
                            }
                        }
                    }
                } finally {
                    withContext(NonCancellable + Dispatchers.Main.immediate) {
                        removeOverlay(composeView)
                        lifecycleOwner.destroy()
                    }
                }
            }
        }
    }

    private fun overlayLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Without this the overlay stops short of a display cutout and the app shows through.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun removeOverlay(view: View) {
        if (view.parent != null) {
            windowManager.removeViewImmediate(view)
        }
    }

}

/** Why a countdown ended, so the service can decide what happens to the blocked app. */
enum class CountdownResult {
    /** The wait elapsed; the user may use the app. */
    Completed,

    /** The user chose to close the app instead of waiting. */
    Dismissed,

    /** The overlay could not be shown; the wait was not enforced. */
    NotShown,
}

@Composable
private fun CountdownOverlay(
    appLabel: String,
    secondsLeft: Int,
    totalSeconds: Int,
    coverBackground: Boolean,
    onCloseApp: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (coverBackground) 1f else 0.72f))
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val remainingFraction by animateFloatAsState(
                targetValue = secondsLeft / totalSeconds.coerceAtLeast(1).toFloat(),
                label = "countdownRing",
            )
            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { remainingFraction },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 6.dp,
                    trackColor = Color.White.copy(alpha = 0.24f),
                )
                Surface(
                    modifier = Modifier.size(136.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    tonalElevation = 6.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = secondsLeft.toString(),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Text(
                text = appLabel,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.overlay_take_a_breath),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.86f),
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onCloseApp) {
                Text(
                    text = stringResource(R.string.overlay_close_app),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
            }
        }
    }
}
