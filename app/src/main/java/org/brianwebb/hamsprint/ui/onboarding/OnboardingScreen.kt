package org.brianwebb.hamsprint.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.brianwebb.hamsprint.R
import org.brianwebb.hamsprint.domain.LicenseClass
import org.brianwebb.hamsprint.domain.QuestionPool
import org.brianwebb.hamsprint.ui.LocalAppContainer
import org.brianwebb.hamsprint.ui.components.HamIcons
import org.brianwebb.hamsprint.ui.components.LocalHaptics
import org.brianwebb.hamsprint.ui.components.PrimaryButton
import org.brianwebb.hamsprint.ui.components.pressable
import org.brianwebb.hamsprint.ui.theme.HamTheme
import org.brianwebb.hamsprint.ui.theme.Radius
import org.brianwebb.hamsprint.ui.theme.Space

@Composable
fun OnboardingScreen(onFinish: (LicenseClass) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var chosen by rememberSaveable { mutableStateOf<LicenseClass?>(null) }
    BackHandler(enabled = step > 0) { step-- }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = Space.gutter),
    ) {
        StepDots(step, total = 3, modifier = Modifier.padding(top = Space.lg))
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(340)) { dir * it / 3 } + fadeIn(tween(260)))
                    .togetherWith(slideOutHorizontally(tween(340)) { -dir * it / 3 } + fadeOut(tween(180)))
            },
            modifier = Modifier.weight(1f),
            label = "onboarding",
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> MethodPage()
                else -> ChooseExamPage(chosen) { chosen = it }
            }
        }
        val license = chosen
        PrimaryButton(
            text = when (step) { 0 -> "Get started"; 1 -> "Continue"; else -> "Start studying" },
            enabled = step < 2 || license != null,
            onClick = { if (step < 2) step++ else if (license != null) onFinish(license) },
            modifier = Modifier.padding(vertical = Space.lg),
        )
    }
}

@Composable
private fun StepDots(step: Int, total: Int, modifier: Modifier = Modifier) {
    val c = HamTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(total) { i ->
            val width by animateDpAsState(if (i == step) 22.dp else 7.dp, label = "dot")
            val color by animateColorAsState(if (i == step) c.accent else c.separator, label = "dotColor")
            Box(Modifier.padding(horizontal = 3.dp).height(7.dp).width(width).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun WelcomePage() {
    val c = HamTheme.colors
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Image(
            painterResource(R.drawable.logo_mark),
            contentDescription = "HamSprint logo",
            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(26.dp)),
        )
        Spacer(Modifier.height(Space.xxl))
        Text("Get your ham radio license.", style = MaterialTheme.typography.displayMedium, color = c.textPrimary)
        Text("Fast.", style = MaterialTheme.typography.displayMedium, color = c.accent)
        Spacer(Modifier.height(Space.lg))
        Text(
            "Learn exactly what's on the FCC exam and nothing more. Know the moment you're ready to pass.",
            style = MaterialTheme.typography.bodyLarge,
            color = c.textSecondary,
        )
    }
}

@Composable
private fun MethodPage() {
    val c = HamTheme.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(Space.xxl))
        Text("Built to get you\ntest-ready fast", style = MaterialTheme.typography.headlineLarge, color = c.textPrimary)
        Spacer(Modifier.height(Space.xxl))
        Feature(
            HamIcons.Bolt,
            "Learn the answers, not the theory",
            "Every question comes from the official NCVEC pool. You see each answer first, then recall it until it sticks.",
        )
        Feature(
            HamIcons.Refresh,
            "Reviews timed to your memory",
            "Questions you miss or are about to forget come back at exactly the right moment.",
        )
        Feature(
            HamIcons.Target,
            "Know you'll pass",
            "A live pass probability, computed from your answers against the real exam format.",
        )
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    val c = HamTheme.colors
    Row(Modifier.padding(bottom = Space.xl)) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(Radius.sm)).background(c.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.padding(start = Space.lg)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun ChooseExamPage(chosen: LicenseClass?, onChoose: (LicenseClass) -> Unit) {
    val c = HamTheme.colors
    val container = LocalAppContainer.current
    val pools by produceState<Map<LicenseClass, QuestionPool>>(emptyMap()) {
        value = withContext(Dispatchers.IO) { LicenseClass.entries.associateWith { container.pools.load(it) } }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(Space.xxl))
        Text("Which exam are you taking?", style = MaterialTheme.typography.headlineLarge, color = c.textPrimary)
        Text(
            "You can change this anytime in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
            modifier = Modifier.padding(top = Space.sm, bottom = Space.xl),
        )
        LicenseClass.entries.forEach { license ->
            LicenseOption(license, pools[license], selected = license == chosen) { onChoose(license) }
        }
        Text(
            "Exams are taken in order: Technician first. You can take Technician and General at the same session.",
            style = MaterialTheme.typography.bodySmall,
            color = c.textTertiary,
            modifier = Modifier.padding(top = Space.sm, bottom = Space.lg),
        )
    }
}

@Composable
private fun LicenseOption(license: LicenseClass, pool: QuestionPool?, selected: Boolean, onClick: () -> Unit) {
    val c = HamTheme.colors
    val haptics = LocalHaptics.current
    val border by animateColorAsState(if (selected) c.accent else c.separator, label = "licBorder")
    val bg by animateColorAsState(if (selected) c.accentSoft else c.surface, label = "licBg")
    val shape = RoundedCornerShape(Radius.lg)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = Space.md)
            .pressable({ haptics.tick(); onClick() }, role = Role.RadioButton)
            .semantics { this.selected = selected }
            .clip(shape)
            .background(bg)
            .border(if (selected) 2.dp else 1.5.dp, border, shape)
            .padding(Space.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(license.displayName, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            Text(
                pool?.let { "Element ${license.element} · ${it.questions.size} questions in pool" } ?: "Element ${license.element}",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )
            if (pool != null) {
                Text(
                    "${pool.examQuestions} on the exam · pass with ${pool.passingScore}",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary,
                )
            }
        }
        Box(
            Modifier.size(26.dp).clip(CircleShape)
                .background(if (selected) c.accent else c.surface)
                .border(1.5.dp, if (selected) c.accent else c.separator, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(HamIcons.Check, null, tint = c.onAccent, modifier = Modifier.size(16.dp))
        }
    }
}
