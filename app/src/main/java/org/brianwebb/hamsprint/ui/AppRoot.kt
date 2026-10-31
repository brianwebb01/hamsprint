package org.brianwebb.hamsprint.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import org.brianwebb.hamsprint.AppContainer
import org.brianwebb.hamsprint.ui.exam.ExamHistoryScreen
import org.brianwebb.hamsprint.ui.exam.ExamScreen
import org.brianwebb.hamsprint.ui.home.HomeScreen
import org.brianwebb.hamsprint.ui.onboarding.OnboardingScreen
import org.brianwebb.hamsprint.ui.settings.SettingsScreen
import org.brianwebb.hamsprint.ui.theme.HamTheme
import org.brianwebb.hamsprint.ui.training.TrainingScreen

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

@Composable
fun AppRoot() {
    val container = LocalAppContainer.current
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(HamTheme.colors.background)) {
        val license = settings.license
        if (license == null) {
            OnboardingScreen(onFinish = { chosen -> container.settings.update { it.copy(license = chosen) } })
        } else {
            NavHost()
        }
    }
}

@Composable
private fun NavHost() {
    val navigator: Navigator = viewModel()
    val saveable = rememberSaveableStateHolder()
    BackHandler(enabled = navigator.stack.size > 1) { navigator.pop() }

    AnimatedContent(
        targetState = navigator.current,
        transitionSpec = {
            val forward = targetState.id > initialState.id
            val dir = if (forward) 1 else -1
            (slideInHorizontally(tween(320)) { w -> dir * w / 4 } + fadeIn(tween(220)))
                .togetherWith(slideOutHorizontally(tween(320)) { w -> -dir * w / 6 } + fadeOut(tween(180)))
        },
        label = "nav",
    ) { entry ->
        CompositionLocalProvider(LocalViewModelStoreOwner provides entry) {
            saveable.SaveableStateProvider(entry.id) {
                when (val screen = entry.screen) {
                    Screen.Home -> HomeScreen(onNavigate = navigator::push)
                    is Screen.Training -> TrainingScreen(
                        subelementId = screen.subelementId,
                        onClose = { navigator.pop() },
                    )
                    Screen.Exam -> ExamScreen(
                        onClose = { navigator.pop() },
                        onTrain = { navigator.replace(Screen.Training()) },
                    )
                    Screen.ExamHistory -> ExamHistoryScreen(
                        onBack = { navigator.pop() },
                        onStartExam = { navigator.push(Screen.Exam) },
                    )
                    Screen.Settings -> SettingsScreen(onBack = { navigator.pop() })
                }
            }
        }
    }
}
