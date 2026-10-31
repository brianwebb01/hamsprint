package org.brianwebb.hamsprint.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.brianwebb.hamsprint.domain.LicenseClass
import org.brianwebb.hamsprint.domain.MemoryModel
import org.brianwebb.hamsprint.domain.SubelementReadiness
import org.brianwebb.hamsprint.ui.LocalAppContainer
import org.brianwebb.hamsprint.ui.Screen
import org.brianwebb.hamsprint.ui.components.Badge
import org.brianwebb.hamsprint.ui.components.CircleIconButton
import org.brianwebb.hamsprint.ui.components.EmptyState
import org.brianwebb.hamsprint.ui.components.HamCard
import org.brianwebb.hamsprint.ui.components.HamIcons
import org.brianwebb.hamsprint.ui.components.LocalHaptics
import org.brianwebb.hamsprint.ui.components.PrimaryButton
import org.brianwebb.hamsprint.ui.components.ProgressBar
import org.brianwebb.hamsprint.ui.components.Ring
import org.brianwebb.hamsprint.ui.components.SecondaryButton
import org.brianwebb.hamsprint.ui.components.SectionHeader
import org.brianwebb.hamsprint.ui.components.pressable
import org.brianwebb.hamsprint.ui.formatDate
import org.brianwebb.hamsprint.ui.formatDuration
import org.brianwebb.hamsprint.ui.formatMillis
import org.brianwebb.hamsprint.ui.theme.HamTheme
import org.brianwebb.hamsprint.ui.theme.Radius
import org.brianwebb.hamsprint.ui.theme.Space
import kotlin.math.roundToInt

@Composable
fun HomeScreen(onNavigate: (Screen) -> Unit) {
    val container = LocalAppContainer.current
    val vm: HomeViewModel = viewModel { HomeViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) {
        vm.refresh()
        onPauseOrDispose { }
    }
    var switching by remember { mutableStateOf(false) }

    when (val s = state) {
        HomeUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = HamTheme.colors.accent)
        }
        is HomeUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState("Something went wrong", s.message) {
                SecondaryButton("Try again", vm::refresh, Modifier.width(200.dp))
            }
        }
        is HomeUiState.Ready -> HomeContent(s, onNavigate, onSwitchLicense = { switching = true })
    }

    if (switching) {
        LicenseSheet(
            current = (state as? HomeUiState.Ready)?.license,
            onPick = { license ->
                container.settings.update { it.copy(license = license) }
                switching = false
            },
            onDismiss = { switching = false },
        )
    }
}

@Composable
private fun HomeContent(s: HomeUiState.Ready, onNavigate: (Screen) -> Unit, onSwitchLicense: () -> Unit) {
    val c = HamTheme.colors
    val r = s.report
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.gutter),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(top = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LicenseChip(s.license, onSwitchLicense)
                Spacer(Modifier.weight(1f))
                CircleIconButton(HamIcons.Settings, "Settings", { onNavigate(Screen.Settings) })
            }
        }
        item {
            val title = when {
                r.seen == 0 -> "Let's get you licensed."
                r.isReady -> "You're test ready."
                else -> "Keep sprinting."
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                color = c.textPrimary,
                modifier = Modifier.padding(top = Space.sm).semantics { heading() },
            )
        }
        item { ReadinessCard(s) }
        item {
            val subtitle = when {
                r.due > 0 -> "${r.due} review${if (r.due == 1) "" else "s"} due" +
                    if (r.unseen > 0) " · ${r.unseen} new left" else ""
                r.unseen > 0 -> "${r.unseen} new questions left"
                else -> "Sharpen your weakest questions"
            }
            PrimaryButton(
                text = if (r.seen == 0) "Start training" else "Continue training",
                subtitle = subtitle,
                leadingIcon = HamIcons.Play,
                onClick = { onNavigate(Screen.Training()) },
            )
        }
        item { ExamCard(s) { onNavigate(Screen.ExamHistory) } }
        item {
            SectionHeader("Topics", Modifier.padding(top = Space.sm))
            HamCard(padding = PaddingValues(vertical = Space.xs)) {
                r.subelements.forEachIndexed { i, sub ->
                    TopicRow(sub) { onNavigate(Screen.Training(sub.subelement.id)) }
                    if (i < r.subelements.lastIndex) {
                        Box(Modifier.padding(start = 68.dp).fillMaxWidth().height(1.dp).background(c.separator))
                    }
                }
            }
        }
        item {
            Text(
                "${s.pool.name}\n${s.pool.revision} · valid through ${formatDate(s.pool.validThrough)}",
                style = MaterialTheme.typography.bodySmall,
                color = c.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = Space.lg),
            )
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun LicenseChip(license: LicenseClass, onClick: () -> Unit) {
    val c = HamTheme.colors
    Row(
        Modifier
            .pressable(onClick, onClickLabel = "Change exam")
            .clip(RoundedCornerShape(50))
            .background(c.surfaceRaised)
            .padding(start = Space.lg, end = Space.md, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c.brand))
        Text(
            license.displayName,
            style = MaterialTheme.typography.titleSmall,
            color = c.textPrimary,
            modifier = Modifier.padding(start = Space.sm, end = Space.xs),
        )
        Icon(HamIcons.ChevronDown, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ReadinessCard(s: HomeUiState.Ready) {
    val c = HamTheme.colors
    val r = s.report
    HamCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ring(progress = r.passProbability.toFloat(), modifier = Modifier.size(128.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(r.passProbability * 100).roundToInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        color = c.textPrimary,
                    )
                    Text("pass chance", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
            Column(Modifier.padding(start = Space.xl).weight(1f), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Stat("Seen", "${r.seen} / ${r.totalQuestions}")
                Stat("Mastered", "${r.mastered}")
                Stat(
                    if (r.isReady) "Status" else "To ready",
                    if (r.isReady) "Ready" else "≈ ${formatDuration(r.timeToReady)}",
                    highlight = r.isReady,
                )
            }
        }
        Spacer(Modifier.height(Space.lg))
        ProgressBar(r.seen.toFloat() / r.totalQuestions, brush = c.brand)
        Spacer(Modifier.height(Space.md))
        Text(
            readinessMessage(s),
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
        )
    }
}

private fun readinessMessage(s: HomeUiState.Ready): String {
    val r = s.report
    val lastTwoPassed = s.recentExams.size >= 2 && s.recentExams.take(2).all { it.passed }
    return when {
        r.seen == 0 -> "Your first round takes about 4 minutes. Each new question shows its answer first, then you recall it."
        r.isReady && lastTwoPassed -> "You've passed your last two practice exams. Go book your exam session!"
        r.isReady -> "Confirm it: pass two practice exams in a row, then book your exam."
        r.passProbability >= 0.5 -> "You'd likely pass today, but it's not a sure thing yet. Keep training."
        else -> "Studied ${formatMillis(s.studyTimeMillis)} so far. Short, frequent sessions beat one long one."
    }
}

@Composable
private fun Stat(label: String, value: String, highlight: Boolean = false) {
    val c = HamTheme.colors
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium, color = if (highlight) c.success else c.textPrimary)
    }
}

@Composable
private fun ExamCard(s: HomeUiState.Ready, onClick: () -> Unit) {
    val c = HamTheme.colors
    HamCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(Radius.sm)).background(c.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(HamIcons.Clock, null, tint = c.accent, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.padding(start = Space.lg).weight(1f)) {
                Text("Practice exam", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Text(
                    "${s.pool.examQuestions} questions · pass with ${s.pool.passingScore}",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
            Icon(HamIcons.Chevron, null, tint = c.textTertiary, modifier = Modifier.size(20.dp))
        }
        if (s.recentExams.isNotEmpty()) {
            Spacer(Modifier.height(Space.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
                s.recentExams.reversed().forEach { e ->
                    Badge(
                        "${e.score}/${e.total}",
                        color = if (e.passed) c.success else c.danger,
                        background = if (e.passed) c.successSoft else c.dangerSoft,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopicRow(sub: SubelementReadiness, onClick: () -> Unit) {
    val c = HamTheme.colors
    // Map recall from "pure guessing" (0.25) to near-certain (0.98) onto 0..1.
    val strength = ((sub.recall - MemoryModel.GUESS) / (0.98 - MemoryModel.GUESS)).coerceIn(0.0, 1.0).toFloat()
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick, pressedScale = 0.985f, onClickLabel = "Train ${sub.subelement.title}")
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(Radius.sm)).background(c.surfacePressed),
            contentAlignment = Alignment.Center,
        ) {
            Text(sub.subelement.id, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
        }
        Column(Modifier.padding(start = Space.md).weight(1f)) {
            Text(sub.subelement.title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            ProgressBar(strength, height = 4.dp, color = if (strength >= 0.85f) c.success else c.accent)
        }
        Text(
            "${sub.mastered}/${sub.total}",
            style = MaterialTheme.typography.bodySmall,
            color = c.textTertiary,
            modifier = Modifier.padding(start = Space.md),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicenseSheet(current: LicenseClass?, onPick: (LicenseClass) -> Unit, onDismiss: () -> Unit) {
    val c = HamTheme.colors
    val haptics = LocalHaptics.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
    ) {
        Column(Modifier.padding(horizontal = Space.gutter).padding(bottom = Space.xxl)) {
            Text("Studying for", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            Text(
                "Progress is kept separately for each exam.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                modifier = Modifier.padding(top = Space.xs, bottom = Space.lg),
            )
            LicenseClass.entries.forEach { license ->
                val selected = license == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable({ haptics.tick(); onPick(license) })
                        .clip(RoundedCornerShape(Radius.md))
                        .background(if (selected) c.accentSoft else c.surface)
                        .padding(Space.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        license.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) c.accent else c.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) Icon(HamIcons.Check, null, tint = c.accent)
                }
            }
        }
    }
}
