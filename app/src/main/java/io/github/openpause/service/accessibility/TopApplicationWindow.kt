package io.github.openpause.service.accessibility

import android.view.accessibility.AccessibilityWindowInfo

/** Minimal, testable view of an on-screen window. */
internal data class WindowSnapshot(
    val type: Int,
    val layer: Int,
    val packageName: String?,
    val pictureInPicture: Boolean = false,
)

/**
 * The package of the topmost real application window the user is looking at.
 *
 * Only [AccessibilityWindowInfo.TYPE_APPLICATION] windows count, so keyboards, system UI,
 * toasts and accessibility overlays are ignored by type; [ignored] drops a small, stable set
 * of non-app packages (our own overlay, the `android` resolver/share sheet). The highest
 * [WindowSnapshot.layer] wins because that is the window drawn on top. Picture-in-picture
 * windows float above everything but are not the app the user is using, so they are skipped.
 */
internal fun topApplicationPackage(
    windows: List<WindowSnapshot>,
    ignored: Set<String>,
): String? = windows
    .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && !it.pictureInPicture }
    .filter { !it.packageName.isNullOrBlank() && it.packageName !in ignored }
    .maxByOrNull { it.layer }
    ?.packageName

/**
 * Whether [packageName] may still be on screen: it has a real application window, or some
 * application window cannot be read yet (mid-transition), so its absence is not proven.
 */
internal fun mayStillBeOnScreen(windows: List<WindowSnapshot>, packageName: String): Boolean =
    windows.any {
        it.type == AccessibilityWindowInfo.TYPE_APPLICATION && !it.pictureInPicture &&
            (it.packageName == packageName || it.packageName == null)
    }
