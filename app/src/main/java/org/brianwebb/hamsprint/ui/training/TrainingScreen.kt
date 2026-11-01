package org.brianwebb.hamsprint.ui.training

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import androidx.lifecycle.viewmodel.compose.viewModel
import org.brianwebb.hamsprint.domain.CardKind
import org.brianwebb.hamsprint.ui.LocalAppContainer
import org.brianwebb.hamsprint.ui.aids.VisualAidCard
import org.brianwebb.hamsprint.ui.components.AnswerState
import org.brianwebb.hamsprint.ui.components.Badge
import org.brianwebb.hamsprint.ui.components.CircleIconButton
import org.brianwebb.hamsprint.ui.components.EmptyState
import org.brianwebb.hamsprint.ui.components.HamCard
import org.brianwebb.hamsprint.ui.components.HamIcons
import org.brianwebb.hamsprint.ui.components.LocalHaptics
import org.brianwebb.hamsprint.ui.components.PrimaryButton
import org.brianwebb.hamsprint.ui.components.ProgressBar
import org.brianwebb.hamsprint.ui.components.QuestionBody
import org.brianwebb.hamsprint.ui.components.SecondaryButton
import org.brianwebb.hamsprint.ui.formatPercent
import org.brianwebb.hamsprint.ui.theme.HamTheme
import org.brianwebb.hamsprint.ui.theme.Space

@Composable
fun TrainingScreen(subelementId: String?, onClose: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: TrainingViewModel = viewModel { TrainingViewModel(container, subelementId) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        when (val s = state) {
            TrainingUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = HamTheme.colors.accent)
            }
            is TrainingUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState("Couldn't continue", s.message) {
                    SecondaryButton("Close", onClose, Modifier.width(200.dp))
                }
            }
            is TrainingUiState.Studying -> Studying(s, vm, onClose)
            is TrainingUiState.Summary -> Summary(s, onKeepGoing = vm::startRound, onDone = onClose)
        }
    }
}

private const val AID_REVEAL_DELAY_MILLIS = 360L

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnScope.Studying(s: TrainingUiState.Studying, vm: TrainingViewModel, onClose: () -> Unit) {
    val c = HamTheme.colors
    val haptics = LocalHaptics.current
    val phase = s.phase
    LaunchedEffect(phase) {
        if (phase is CardPhase.Revealed) if (phase.correct) haptics.success() else haptics.error()
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(HamIcons.Close, "End training", onClose)
        ProgressBar(
            s.cardsShown.toFloat() / s.roundLength,
            brush = c.brand,
            modifier = Modifier.weight(1f).padding(horizontal = Space.lg),
        )
        Text("${s.cardsShown}/${s.roundLength}", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
    }

    AnimatedContent(
        targetState = s.card to s.cardsShown,
        transitionSpec = {
            (slideInVertically(tween(300)) { it / 10 } + fadeIn(tween(240)))
                .togetherWith(fadeOut(tween(120)))
        },
        contentKey = { it.second },
        modifier = Modifier.weight(1f),
        label = "card",
    ) { (card, _) ->
        val isPreview = card.kind == CardKind.PREVIEW
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter),
        ) {
            Row(
                Modifier.padding(top = Space.md, bottom = Space.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                when (card.kind) {
                    CardKind.PREVIEW, CardKind.NEW -> Badge("New", c.accent, c.accentSoft)
                    CardKind.LEARNING -> Badge("Learning", c.warning, c.warning.copy(alpha = 0.14f))
                    CardKind.REVIEW -> Badge("Review", c.success, c.successSoft)
                    CardKind.AHEAD -> Badge("Practice", c.textSecondary, c.surfaceRaised)
                }
                Text(
                    listOfNotNull(card.question.id, s.scopeTitle).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textTertiary,
                )
            }
            QuestionBody(
                card = card,
                stateFor = { pos ->
                    when {
                        isPreview -> if (pos == card.correctPosition) AnswerState.REVEAL else AnswerState.DIMMED
                        phase is CardPhase.Revealed && pos == phase.chosen ->
                            if (phase.correct) AnswerState.CORRECT else AnswerState.WRONG
                        phase is CardPhase.Revealed && pos == phase.correctPosition -> AnswerState.REVEAL
                        phase is CardPhase.Revealed -> AnswerState.DIMMED
                        else -> AnswerState.IDLE
                    }
                },
                onSelect = if (!isPreview && phase == CardPhase.Asking) vm::select else null,
                compactDimmed = s.aidVisible,
            )
            s.aid?.let { aid ->
                // A wrong answer slides the aid in and scrolls to it; a preview shows it in place.
                val bringIntoView = remember { BringIntoViewRequester() }
                LaunchedEffect(s.aidVisible, isPreview) {
                    if (s.aidVisible && !isPreview) {
                        delay(AID_REVEAL_DELAY_MILLIS)
                        bringIntoView.bringIntoView()
                    }
                }
                AnimatedVisibility(
                    visible = s.aidVisible,
                    enter = expandVertically(tween(320)) + fadeIn(tween(320, delayMillis = 80)),
                    exit = fadeOut(tween(120)),
                ) {
                    VisualAidCard(aid, Modifier.padding(top = Space.lg).bringIntoViewRequester(bringIntoView))
                }
            }
            Spacer(Modifier.height(Space.xl))
        }
    }

    Footer(s, onAcknowledge = vm::acknowledgePreview, onNext = vm::next)
}

@Composable
private fun Footer(s: TrainingUiState.Studying, onAcknowledge: () -> Unit, onNext: () -> Unit) {
    val c = HamTheme.colors
    val phase = s.phase
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.md)) {
        when {
            s.card.kind == CardKind.PREVIEW -> {
                Text(
                    if (s.aid != null) "Lock in the highlighted answer and its picture. You'll be asked in a moment."
                    else "Lock in the highlighted answer. You'll be asked in a moment.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = Space.md),
                )
                PrimaryButton("Got it", onAcknowledge)
            }
            phase is CardPhase.Revealed -> {
                AnimatedVisibility(true) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = Space.md).semantics { liveRegion = LiveRegionMode.Polite },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        val tint = if (phase.correct) c.success else c.danger
                        Box(Modifier.size(22.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
                            Icon(
                                if (phase.correct) HamIcons.Check else HamIcons.Close, null,
                                tint = c.onAccent, modifier = Modifier.size(14.dp),
                            )
                        }
                        Text(
                            if (phase.correct) "Correct" else "Not quite. Remember the green answer.",
                            style = MaterialTheme.typography.titleSmall,
                            color = tint,
                            modifier = Modifier.padding(start = Space.sm),
                        )
                    }
                }
                PrimaryButton("Continue", onNext)
            }
            else -> Text(
                "Choose an answer",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 19.dp),
            )
        }
    }
}

@Composable
private fun Summary(s: TrainingUiState.Summary, onKeepGoing: () -> Unit, onDone: () -> Unit) {
    val c = HamTheme.colors
    val sum = s.summary
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        Box(Modifier.size(88.dp).clip(CircleShape).background(c.brand), contentAlignment = Alignment.Center) {
            Icon(if (sum.caughtUp) HamIcons.Sparkle else HamIcons.Check, null, tint = c.onAccent, modifier = Modifier.size(40.dp))
        }
        Text(
            if (sum.caughtUp) "All caught up" else "Round complete",
            style = MaterialTheme.typography.headlineLarge,
            color = c.textPrimary,
            modifier = Modifier.padding(top = Space.xl),
        )
        Text(
            if (sum.caughtUp) "Nothing left to study here right now. Come back later for reviews."
            else s.scopeTitle?.let { "Topic: $it" } ?: "Nice work. Short rounds add up fast.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Space.sm),
        )
        Spacer(Modifier.height(Space.xxl))
        HamCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()) {
                SummaryStat("Accuracy", if (sum.answered == 0) "-" else formatPercent(sum.correct.toDouble() / sum.answered), Modifier.weight(1f))
                SummaryStat("New", "${sum.introduced}", Modifier.weight(1f))
                SummaryStat("Reviewed", "${sum.reviewed}", Modifier.weight(1f))
            }
            Box(Modifier.padding(vertical = Space.lg).fillMaxWidth().height(1.dp).background(c.separator))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pass chance", style = MaterialTheme.typography.titleSmall, color = c.textSecondary, modifier = Modifier.weight(1f))
                Text(formatPercent(s.passBefore), style = MaterialTheme.typography.titleMedium, color = c.textTertiary)
                Icon(HamIcons.Chevron, null, tint = c.textTertiary, modifier = Modifier.padding(horizontal = Space.xs).size(16.dp))
                Text(formatPercent(s.passAfter), style = MaterialTheme.typography.titleLarge, color = c.accent)
            }
        }
        Spacer(Modifier.height(40.dp))
        PrimaryButton("Keep going", onKeepGoing, leadingIcon = HamIcons.Bolt)
        Spacer(Modifier.height(Space.md))
        SecondaryButton("Done", onDone)
        Spacer(Modifier.height(Space.lg))
    }
}

@Composable
private fun SummaryStat(label: String, value: String, modifier: Modifier = Modifier) {
    val c = HamTheme.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}
