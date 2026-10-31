package org.brianwebb.hamsprint.ui.exam

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.brianwebb.hamsprint.domain.ExamOutcome
import org.brianwebb.hamsprint.ui.LocalAppContainer
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
import org.brianwebb.hamsprint.ui.components.Ring
import org.brianwebb.hamsprint.ui.components.SecondaryButton
import org.brianwebb.hamsprint.ui.components.SectionHeader
import org.brianwebb.hamsprint.ui.components.pressable
import org.brianwebb.hamsprint.ui.formatMillis
import org.brianwebb.hamsprint.ui.theme.HamTheme
import org.brianwebb.hamsprint.ui.theme.Radius
import org.brianwebb.hamsprint.ui.theme.Space

@Composable
fun ExamScreen(onClose: () -> Unit, onTrain: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: ExamViewModel = viewModel { ExamViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }
    val inProgress = (state as? ExamUiState.Taking)?.answers?.isNotEmpty() == true
    BackHandler(enabled = inProgress) { confirmLeave = true }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        when (val s = state) {
            ExamUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = HamTheme.colors.accent)
            }
            is ExamUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState("Something went wrong", s.message) {
                    SecondaryButton("Close", onClose, Modifier.width(200.dp))
                }
            }
            is ExamUiState.Taking -> Taking(s, vm, onClose = { if (inProgress) confirmLeave = true else onClose() })
            is ExamUiState.Finished -> Results(s, onTrain, onClose)
        }
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = "Leave this exam?",
            body = "Your answers won't be scored.",
            confirm = "Leave",
            onConfirm = { confirmLeave = false; onClose() },
            onDismiss = { confirmLeave = false },
        )
    }
}

@Composable
private fun ColumnScope.Taking(s: ExamUiState.Taking, vm: ExamViewModel, onClose: () -> Unit) {
    val c = HamTheme.colors
    val haptics = LocalHaptics.current
    var confirmSubmit by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    val container = LocalAppContainer.current
    LaunchedEffect(s.startedAtMillis) {
        while (true) {
            elapsed = container.clock.nowMillis() - s.startedAtMillis
            delay(1_000)
        }
    }
    val card = s.exam.cards[s.index]
    val isLast = s.index == s.exam.cards.lastIndex

    Row(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
        CircleIconButton(HamIcons.Close, "Leave exam", onClose)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Question ${s.index + 1} of ${s.exam.cards.size}", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Text(formatClock(elapsed), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        val flagged = s.index in s.flagged
        CircleIconButton(
            HamIcons.Flag,
            if (flagged) "Remove flag" else "Flag for review",
            { haptics.tick(); vm.toggleFlag() },
            tint = if (flagged) c.onAccent else c.textPrimary,
            background = if (flagged) c.warning else c.surfaceRaised,
        )
    }

    QuestionStrip(s, onJump = vm::goTo)

    AnimatedContent(
        targetState = s.index,
        transitionSpec = { fadeIn(tween(200)).togetherWith(fadeOut(tween(120))) },
        modifier = Modifier.weight(1f),
        label = "examCard",
    ) { index ->
        val shown = s.exam.cards[index]
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter)) {
            Text(
                shown.question.id,
                style = MaterialTheme.typography.labelMedium,
                color = c.textTertiary,
                modifier = Modifier.padding(top = Space.md, bottom = Space.md),
            )
            QuestionBody(
                card = shown,
                stateFor = { pos -> if (s.answers[index] == pos) AnswerState.SELECTED else AnswerState.IDLE },
                onSelect = { pos -> haptics.tick(); vm.select(pos) },
            )
            Spacer(Modifier.height(Space.xl))
        }
    }

    Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.md), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        SecondaryButton("Back", vm::previous, Modifier.weight(1f), enabled = s.index > 0)
        Box(Modifier.weight(1.4f)) {
            if (isLast) {
                PrimaryButton(
                    "Finish",
                    { if (s.unanswered > 0) confirmSubmit = true else vm.submit() },
                    enabled = !s.submitting,
                )
            } else {
                PrimaryButton("Next", vm::next)
            }
        }
    }

    if (confirmSubmit) {
        ConfirmDialog(
            title = "Finish with ${s.unanswered} unanswered?",
            body = "Unanswered questions count as wrong, just like the real exam. Guessing is free.",
            confirm = "Finish",
            onConfirm = { confirmSubmit = false; vm.submit() },
            onDismiss = { confirmSubmit = false },
        )
    }
}

@Composable
private fun QuestionStrip(s: ExamUiState.Taking, onJump: (Int) -> Unit) {
    val c = HamTheme.colors
    val listState = rememberLazyListState()
    LaunchedEffect(s.index) { listState.animateScrollToItem((s.index - 3).coerceAtLeast(0)) }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = Space.gutter),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = Space.xs),
    ) {
        itemsIndexed(s.exam.cards) { i, _ ->
            val answered = i in s.answers
            val current = i == s.index
            val flagged = i in s.flagged
            Box(
                Modifier
                    .size(34.dp)
                    .pressable({ onJump(i) }, pressedScale = 0.9f)
                    .semantics {
                        contentDescription = "Question ${i + 1}" +
                            (if (answered) ", answered" else ", unanswered") + (if (flagged) ", flagged" else "")
                    }
                    .clip(CircleShape)
                    .background(if (answered) c.accentSoft else c.surfaceRaised)
                    .border(if (current) 2.dp else 0.dp, if (current) c.accent else c.surfaceRaised, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${i + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (answered || current) c.accent else c.textTertiary,
                )
                if (flagged) {
                    Box(Modifier.align(Alignment.TopEnd).size(9.dp).clip(CircleShape).background(c.warning))
                }
            }
        }
    }
}

@Composable
private fun Results(s: ExamUiState.Finished, onTrain: () -> Unit, onDone: () -> Unit) {
    val c = HamTheme.colors
    val r = s.result
    val haptics = LocalHaptics.current
    LaunchedEffect(r) { if (r.passed) haptics.success() else haptics.error() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        item {
            Row(Modifier.fillMaxWidth()) {
                CircleIconButton(HamIcons.Close, "Close results", onDone)
            }
        }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Ring(progress = r.score.toFloat() / r.total, modifier = Modifier.size(168.dp), stroke = 16.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${r.score}", style = MaterialTheme.typography.displayLarge, color = c.textPrimary)
                        Text("of ${r.total}", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    }
                }
                Spacer(Modifier.height(Space.xl))
                Badge(
                    if (r.passed) "Passed" else "Not yet",
                    color = if (r.passed) c.success else c.danger,
                    background = if (r.passed) c.successSoft else c.dangerSoft,
                )
                Text(
                    when {
                        r.passed -> "You'd pass the real exam."
                        r.score >= r.passingScore - 3 -> "So close. Train the misses and try again."
                        else -> "Not yet. Keep training, then try again."
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Space.md),
                )
                Text(
                    "Passing is ${r.passingScore}/${r.total} · finished in ${formatMillis(s.durationMillis)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
        }
        item {
            if (r.missed.isNotEmpty()) {
                PrimaryButton("Train missed questions", onTrain, leadingIcon = HamIcons.Bolt, subtitle = "${r.missed.size} added to the front of your queue")
            } else {
                PrimaryButton("Done", onDone)
            }
        }
        item {
            SectionHeader("By topic", Modifier.padding(top = Space.sm))
            HamCard(padding = PaddingValues(Space.lg)) {
                r.bySubelement.forEachIndexed { i, sub ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(sub.subelement.id, style = MaterialTheme.typography.labelMedium, color = c.textTertiary, modifier = Modifier.width(32.dp))
                        Column(Modifier.weight(1f)) {
                            Text(sub.subelement.title, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, maxLines = 1)
                            Spacer(Modifier.height(4.dp))
                            ProgressBar(
                                if (sub.total == 0) 0f else sub.correct.toFloat() / sub.total,
                                height = 4.dp,
                                color = if (sub.correct == sub.total) c.success else c.accent,
                            )
                        }
                        Text(
                            "${sub.correct}/${sub.total}",
                            style = MaterialTheme.typography.labelMedium,
                            color = c.textSecondary,
                            modifier = Modifier.padding(start = Space.md),
                        )
                    }
                }
            }
        }
        if (r.missed.isNotEmpty()) {
            item { SectionHeader("Missed (${r.missed.size})", Modifier.padding(top = Space.sm)) }
            items(r.missed, key = { it.card.question.id }) { MissedCard(it) }
        }
        item { Spacer(Modifier.height(Space.xl)) }
    }
}

@Composable
private fun MissedCard(o: ExamOutcome) {
    val c = HamTheme.colors
    HamCard(padding = PaddingValues(Space.lg)) {
        Text(o.card.question.id, style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
        Text(
            o.card.question.text,
            style = MaterialTheme.typography.titleMedium,
            color = c.textPrimary,
            modifier = Modifier.padding(top = Space.xs, bottom = Space.md),
        )
        o.chosenPosition?.let { chosen ->
            AnswerLine("Your answer", o.card.answerAt(chosen), c.danger, c.dangerSoft)
            Spacer(Modifier.height(Space.sm))
        } ?: run {
            AnswerLine("Your answer", "No answer", c.danger, c.dangerSoft)
            Spacer(Modifier.height(Space.sm))
        }
        AnswerLine("Correct", o.card.question.correctAnswer, c.success, c.successSoft)
    }
}

@Composable
private fun AnswerLine(label: String, text: String, color: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) {
    val c = HamTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).background(bg).padding(Space.md),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = HamTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        shape = RoundedCornerShape(Radius.lg),
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, style = MaterialTheme.typography.labelLarge, color = c.danger) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelLarge, color = c.accent) }
        },
    )
}

private fun formatClock(millis: Long): String {
    val total = millis / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
