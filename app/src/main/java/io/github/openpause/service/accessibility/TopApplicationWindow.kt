package io.github.openpause.service.accessibility

import android.view.accessibility.AccessibilityWindowInfo

/** Minimal, testable view of an on-screen window. */
internal data class WindowSnapshot(
    val type: Int,
    val layer: Int,
    val packageName: String?,
)

/**
 * The package of the topmost real application window the user is looking at.
 *
 * Only [AccessibilityWindowInfo.TYPE_APPLICATION] windows count, so keyboards, system UI,
 * toasts and accessibility overlays are ignored by type; [ignored] drops a small, stable set
 * of non-app packages (our own overlay, the `android` resolver/share sheet). The highest
 * [WindowSnapshot.layer] wins because that is the window drawn on top.
 */
internal fun topApplicationPackage(
    windows: List<WindowSnapshot>,
    ignored: Set<String>,
): String? = windows
    .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
    .filter { !it.packageName.isNullOrBlank() && it.packageName !in ignored }
    .maxByOrNull { it.layer }
    ?.packageName
