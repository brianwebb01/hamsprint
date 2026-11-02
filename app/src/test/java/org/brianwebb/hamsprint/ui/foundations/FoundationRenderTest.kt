package org.brianwebb.hamsprint.ui.foundations

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.brianwebb.hamsprint.data.ThemeMode
import org.brianwebb.hamsprint.domain.Foundation
import org.brianwebb.hamsprint.domain.LicenseClass
import org.brianwebb.hamsprint.domain.realFoundations
import org.brianwebb.hamsprint.domain.realPools
import org.brianwebb.hamsprint.ui.theme.HamSprintTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Draws every bundled foundation and the question reminder, so bad content can't crash a lesson. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FoundationRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val all: List<Pair<Foundation, List<String>>> = LicenseClass.entries.flatMap { license ->
        val pool = realPools.load(license)
        realFoundations.load(license).foundations.map { it to it.figures(pool) }
    }

    private fun drawWindow() = rule.runOnUiThread {
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
    }

    @Test
    fun `every foundation draws as a lesson`() {
        var current by mutableStateOf(all.first())
        var mode by mutableStateOf(ThemeMode.LIGHT)
        rule.setContent {
            HamSprintTheme(mode) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    FoundationBody(current.first, 1, all.size, current.second)
                }
            }
        }
        all.forEachIndexed { i, item ->
            mode = if (i % 2 == 0) ThemeMode.LIGHT else ThemeMode.DARK
            current = item
            rule.waitForIdle()
            rule.onNodeWithText(item.first.title).assertExists()
            drawWindow()
        }
    }

    @Test
    fun `the reminder shows the asked rows and expands to the whole framework`() {
        val foundation = all.first { (f, _) -> f.points.size >= 3 && f.pointsFor(f.questionIds.first()).size == 1 }.first
        val asked = foundation.pointsFor(foundation.questionIds.first()).single()
        val other = foundation.points.first { it != asked }
        rule.setContent { HamSprintTheme(ThemeMode.LIGHT) { BuildsOnCard(foundation, foundation.questionIds.first()) } }
        rule.onNodeWithText(foundation.title).assertExists()
        rule.onNodeWithContentDescription("${asked.label}: ${asked.value}").assertExists()
        rule.onNodeWithContentDescription("${other.label}: ${other.value}").assertDoesNotExist()
        rule.onNodeWithText("See all ${foundation.points.size} rows").performClick()
        rule.onNodeWithContentDescription("${other.label}: ${other.value}").assertExists()
        drawWindow()
    }
}
