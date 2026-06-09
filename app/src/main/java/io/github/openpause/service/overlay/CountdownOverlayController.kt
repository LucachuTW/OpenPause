package io.github.openpause.service.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.openpause.presentation.theme.OpenPauseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
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

    suspend fun showCountdown(appLabel: String, waitSeconds: Int): Boolean {
        if (waitSeconds <= 0) return true
        if (!canDrawOverlay()) return false

        return overlayMutex.withLock {
            withContext(Dispatchers.Main.immediate) {
                val secondsLeft = mutableIntStateOf(waitSeconds)
                val lifecycleOwner = OverlayLifecycleOwner().also { it.start() }
                val composeView = ComposeView(context).apply {
                    setViewTreeLifecycleOwner(lifecycleOwner)
                    setViewTreeViewModelStoreOwner(lifecycleOwner)
                    setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                    setContent {
                        OpenPauseTheme {
                            CountdownOverlay(
                                appLabel = appLabel,
                                secondsLeft = secondsLeft.intValue,
                            )
                        }
                    }
                }

                val added = runCatching {
                    windowManager.addView(composeView, overlayLayoutParams())
                }.isSuccess
                if (!added) {
                    lifecycleOwner.destroy()
                    return@withContext false
                }

                try {
                    for (remaining in waitSeconds downTo 1) {
                        secondsLeft.intValue = remaining
                        delay(1_000)
                    }
                    true
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
        }
    }

    private fun removeOverlay(view: View) {
        if (view.parent != null) {
            windowManager.removeViewImmediate(view)
        }
    }

}

@Composable
private fun CountdownOverlay(
    appLabel: String,
    secondsLeft: Int,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Surface(
                modifier = Modifier.size(144.dp),
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
            Text(
                text = appLabel,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Take a breath before opening this app.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.86f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
