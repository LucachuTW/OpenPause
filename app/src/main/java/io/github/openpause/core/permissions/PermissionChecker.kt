package io.github.openpause.core.permissions

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import io.github.openpause.service.accessibility.OpenPauseAccessibilityService

data class RequiredPermissionStatus(
    val accessibilityEnabled: Boolean,
    val overlayEnabled: Boolean,
    val usageAccessEnabled: Boolean,
) {
    val allGranted: Boolean = accessibilityEnabled && overlayEnabled && usageAccessEnabled
}

object PermissionChecker {
    fun status(context: Context): RequiredPermissionStatus = RequiredPermissionStatus(
        accessibilityEnabled = isAccessibilityServiceEnabled(context),
        overlayEnabled = Settings.canDrawOverlays(context),
        usageAccessEnabled = isUsageAccessGranted(context),
    )

    fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun overlaySettingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        "package:${context.packageName}".toUri(),
    )

    fun usageAccessSettingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

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

    private fun isUsageAccessGranted(context: Context): Boolean {
        val appOps = context.getSystemService<AppOpsManager>() ?: return false
        val uid = Process.myUid()
        val packageName = context.packageName
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
