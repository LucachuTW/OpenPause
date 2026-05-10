package io.github.openpause

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dagger.hilt.android.AndroidEntryPoint
import io.github.openpause.core.permissions.PermissionChecker
import io.github.openpause.core.permissions.RequiredPermissionStatus
import io.github.openpause.presentation.home.HomeScreen
import io.github.openpause.presentation.onboarding.OnboardingScreen
import io.github.openpause.presentation.theme.OpenPauseTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            OpenPauseTheme {
                OpenPauseAppRoot()
            }
        }
    }
}

@Composable
private fun OpenPauseAppRoot() {
    val context = LocalContext.current
    var permissions by remember { mutableStateOf(PermissionChecker.status(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissions = PermissionChecker.status(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (permissions.allGranted) {
        HomeScreen(
            permissionStatus = permissions,
            onRefreshPermissionStatus = { permissions = PermissionChecker.status(context) },
        )
    } else {
        OnboardingScreen(
            status = permissions,
            onStatusRefresh = { permissions = PermissionChecker.status(context) },
        )
    }
}
