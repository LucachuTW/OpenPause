package io.github.openpause.service.accessibility

import android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION
import android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD
import android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopApplicationWindowTest {
    private val ignored = setOf("io.github.openpause", "android")

    @Test
    fun picksTopmostApplicationWindowByLayer() {
        val windows = listOf(
            WindowSnapshot(TYPE_APPLICATION, layer = 1, packageName = "com.instagram.android"),
            WindowSnapshot(TYPE_APPLICATION, layer = 5, packageName = "com.android.chrome"),
        )

        assertEquals("com.android.chrome", topApplicationPackage(windows, ignored))
    }

    @Test
    fun ignoresKeyboardAndSystemWindows() {
        val windows = listOf(
            WindowSnapshot(TYPE_APPLICATION, layer = 1, packageName = "com.instagram.android"),
            WindowSnapshot(TYPE_INPUT_METHOD, layer = 9, packageName = "com.google.android.inputmethod"),
            WindowSnapshot(TYPE_SYSTEM, layer = 9, packageName = "com.android.systemui"),
        )

        assertEquals("com.instagram.android", topApplicationPackage(windows, ignored))
    }

    @Test
    fun ignoresOwnOverlayAndResolverPackages() {
        val windows = listOf(
            WindowSnapshot(TYPE_APPLICATION, layer = 1, packageName = "com.instagram.android"),
            WindowSnapshot(TYPE_APPLICATION, layer = 8, packageName = "io.github.openpause"),
            WindowSnapshot(TYPE_APPLICATION, layer = 7, packageName = "android"),
        )

        assertEquals("com.instagram.android", topApplicationPackage(windows, ignored))
    }

    @Test
    fun ignoresBlankPackages() {
        val windows = listOf(
            WindowSnapshot(TYPE_APPLICATION, layer = 9, packageName = null),
            WindowSnapshot(TYPE_APPLICATION, layer = 1, packageName = "com.instagram.android"),
        )

        assertEquals("com.instagram.android", topApplicationPackage(windows, ignored))
    }

    @Test
    fun ignoresPictureInPictureWindows() {
        val windows = listOf(
            WindowSnapshot(TYPE_APPLICATION, layer = 1, packageName = "com.whatsapp"),
            WindowSnapshot(TYPE_APPLICATION, layer = 9, packageName = "com.google.android.youtube", pictureInPicture = true),
        )

        assertEquals("com.whatsapp", topApplicationPackage(windows, ignored))
    }

    @Test
    fun returnsNullWhenNoRealApplicationWindow() {
        val windows = listOf(
            WindowSnapshot(TYPE_SYSTEM, layer = 9, packageName = "com.android.systemui"),
        )

        assertNull(topApplicationPackage(windows, ignored))
    }
}
