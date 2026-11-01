package org.brianwebb.hamsprint.ui.aids

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.brianwebb.hamsprint.data.ThemeMode
import org.brianwebb.hamsprint.domain.LicenseClass
import org.brianwebb.hamsprint.domain.VisualAid
import org.brianwebb.hamsprint.domain.realAids
import org.brianwebb.hamsprint.ui.theme.HamSprintTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Draws one bundled aid of every kind, in both themes, so a drawing bug can't crash training. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class VisualAidRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val samples: List<VisualAid> = LicenseClass.entries
        .flatMap { realAids.load(it).values }
        .groupBy { it::class }
        .values.flatMap { group -> group.take(3) }

    /** Draws the whole window into a bitmap, which runs every Canvas block. */
    private fun drawWindow() = rule.runOnUiThread {
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
    }

    @Test
    fun `bundled aids cover every kind`() {
        assertEquals(25, samples.map { it::class }.distinct().size)
    }

    @Test
    fun `one aid of every kind draws in light and dark`() {
        var current by mutableStateOf(samples.first())
        var mode by mutableStateOf(ThemeMode.LIGHT)
        rule.setContent { HamSprintTheme(mode) { VisualAidCard(current) } }
        for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            for (aid in samples) {
                mode = theme
                current = aid
                rule.waitForIdle()
                rule.onNodeWithText(title(aid)).assertExists()
                drawWindow()
            }
        }
    }
}
