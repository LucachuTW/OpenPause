package io.github.openpause.core.permissions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import io.github.openpause.service.accessibility.OpenPauseAccessibilityService

data class RequiredPermissionStatus(
    val accessibilityEnabled: Boolean,
    val overlayEnabled: Boolean,
) {
    val allGranted: Boolean = accessibilityEnabled && overlayEnabled
}

object PermissionChecker {
    fun status(context: Context): RequiredPermissionStatus = RequiredPermissionStatus(
        accessibilityEnabled = isAccessibilityServiceEnabled(context),
        overlayEnabled = Settings.canDrawOverlays(context),
    )

    fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun overlaySettingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        "package:${context.packageName}".toUri(),
    )

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, OpenPauseAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        return enabledServices
            .split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it == expected }
    }
}
