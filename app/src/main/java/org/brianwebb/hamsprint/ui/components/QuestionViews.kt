package org.brianwebb.hamsprint.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.brianwebb.hamsprint.domain.Card
import org.brianwebb.hamsprint.ui.theme.HamTheme
import org.brianwebb.hamsprint.ui.theme.Radius
import org.brianwebb.hamsprint.ui.theme.Space

enum class AnswerState { IDLE, SELECTED, CORRECT, WRONG, REVEAL, DIMMED }

@Composable
fun AnswerOption(
    letter: Char,
    text: String,
    state: AnswerState,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val c = HamTheme.colors
    val bg by animateColorAsState(
        when (state) {
            AnswerState.SELECTED -> c.accentSoft
            AnswerState.CORRECT, AnswerState.REVEAL -> c.successSoft
            AnswerState.WRONG -> c.dangerSoft
            else -> c.surface
        },
        tween(220), label = "answerBg",
    )
    val border by animateColorAsState(
        when (state) {
            AnswerState.SELECTED -> c.accent
            AnswerState.CORRECT, AnswerState.REVEAL -> c.success
            AnswerState.WRONG -> c.danger
            else -> c.separator
        },
        tween(220), label = "answerBorder",
    )
    val badgeBg by animateColorAsState(
        when (state) {
            AnswerState.SELECTED -> c.accent
            AnswerState.CORRECT, AnswerState.REVEAL -> c.success
            AnswerState.WRONG -> c.danger
            else -> c.surfacePressed
        },
        tween(220), label = "badgeBg",
    )
    val alpha by animateFloatAsState(if (state == AnswerState.DIMMED) 0.4f else 1f, tween(250), label = "answerAlpha")

    // A short horizontal shake on a wrong answer: feedback you can feel without reading.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(state) {
        if (state == AnswerState.WRONG) {
            shake.animateTo(0f, keyframes {
                durationMillis = 380
                -14f at 50; 12f at 110; -8f at 180; 5f at 250; -2f at 310
            })
        }
    }

    val shape = RoundedCornerShape(Radius.md)
    val stateLabel = when (state) {
        AnswerState.SELECTED -> "selected"
        AnswerState.CORRECT -> "correct, your answer"
        AnswerState.WRONG -> "incorrect, your answer"
        AnswerState.REVEAL -> "correct answer"
        else -> null
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = shake.value }
            .alpha(alpha)
            .then(if (onClick != null) Modifier.pressable(onClick, pressedScale = 0.98f, role = Role.RadioButton) else Modifier)
            .clip(shape)
            .background(bg)
            .border(1.5.dp, border, shape)
            .padding(horizontal = Space.lg, vertical = 14.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$letter. $text"
                if (stateLabel != null) stateDescription = stateLabel
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(badgeBg),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                AnswerState.CORRECT, AnswerState.REVEAL ->
                    Icon(HamIcons.Check, null, tint = c.onAccent, modifier = Modifier.size(18.dp))
                AnswerState.WRONG ->
                    Icon(HamIcons.Close, null, tint = c.onAccent, modifier = Modifier.size(16.dp))
                else -> Text(
                    letter.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state == AnswerState.SELECTED) c.onAccent else c.textSecondary,
                )
            }
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (state == AnswerState.CORRECT || state == AnswerState.REVEAL) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = c.textPrimary,
            modifier = Modifier.padding(start = Space.md).weight(1f),
        )
    }
}

/** Question stem, optional figure, and four answers. [stateFor] decides each answer's look. */
@Composable
fun QuestionBody(
    card: Card,
    stateFor: (position: Int) -> AnswerState,
    onSelect: ((position: Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val c = HamTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Text(
            card.question.text,
            style = MaterialTheme.typography.headlineSmall,
            color = c.textPrimary,
            modifier = Modifier.padding(bottom = Space.sm),
        )
        card.question.figure?.let { FigureCard(it, Modifier.padding(bottom = Space.sm)) }
        card.answerOrder.indices.forEach { pos ->
            AnswerOption(
                letter = 'A' + pos,
                text = card.answerAt(pos),
                state = stateFor(pos),
                onClick = onSelect?.let { { it(pos) } },
            )
        }
    }
}
