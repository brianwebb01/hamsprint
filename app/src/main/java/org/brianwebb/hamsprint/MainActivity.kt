package org.brianwebb.hamsprint

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.brianwebb.hamsprint.ui.AppRoot
import org.brianwebb.hamsprint.ui.LocalAppContainer
import org.brianwebb.hamsprint.ui.components.LocalHaptics
import org.brianwebb.hamsprint.ui.components.rememberHaptics
import org.brianwebb.hamsprint.ui.theme.HamSprintTheme
import org.brianwebb.hamsprint.ui.theme.HamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as HamSprintApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            HamSprintTheme(settings.theme) {
                val dark = HamTheme.colors.isDark
                LaunchedEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                CompositionLocalProvider(
                    LocalAppContainer provides container,
                    LocalHaptics provides rememberHaptics(settings.haptics),
                ) {
                    AppRoot()
                }
            }
        }
    }
}
