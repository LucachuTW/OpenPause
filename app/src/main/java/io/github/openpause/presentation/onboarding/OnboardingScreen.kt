package io.github.openpause.presentation.onboarding

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.openpause.core.permissions.PermissionChecker
import io.github.openpause.core.permissions.RequiredPermissionStatus

@Composable
fun OnboardingScreen(
    status: RequiredPermissionStatus,
    onStatusRefresh: () -> Unit,
) {
    val context = LocalContext.current

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Set up OpenPause",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "OpenPause needs three Android permissions to detect selected apps, show a lightweight pause overlay, and read per-app usage statistics.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PermissionCard(
                title = "Accessibility service",
                body = "Detects when a restricted app becomes the foreground window.",
                granted = status.accessibilityEnabled,
                actionLabel = "Open settings",
                onClick = {
                    context.startActivity(PermissionChecker.accessibilitySettingsIntent())
                },
            )
            PermissionCard(
                title = "Display over other apps",
                body = "Shows the countdown directly over the app without launching a blocking Activity.",
                granted = status.overlayEnabled,
                actionLabel = "Allow overlay",
                onClick = {
                    try {
                        context.startActivity(PermissionChecker.overlaySettingsIntent(context))
                    } catch (_: ActivityNotFoundException) {
                        context.startActivity(PermissionChecker.systemSettingsIntent())
                    }
                },
            )
            PermissionCard(
                title = "Usage access",
                body = "Reads per-app screen time so the app picker can highlight your biggest distractions.",
                granted = status.usageAccessEnabled,
                actionLabel = "Open settings",
                onClick = {
                    try {
                        context.startActivity(PermissionChecker.usageAccessSettingsIntent())
                    } catch (_: ActivityNotFoundException) {
                        context.startActivity(PermissionChecker.systemSettingsIntent())
                    }
                },
            )

            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = onStatusRefresh,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Refresh")
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    body: String,
    granted: Boolean,
    actionLabel: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!granted) {
                Button(onClick = onClick) {
                    Text(actionLabel)
                    Spacer(modifier = Modifier.size(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
