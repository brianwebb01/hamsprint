package org.brianwebb.hamsprint.ui.foundations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.brianwebb.hamsprint.domain.Foundation
import org.brianwebb.hamsprint.domain.FoundationPoint
import org.brianwebb.hamsprint.ui.aids.VisualAidCard
import org.brianwebb.hamsprint.ui.components.FigureCard
import org.brianwebb.hamsprint.ui.components.HamIcons
import org.brianwebb.hamsprint.ui.components.pressable
import org.brianwebb.hamsprint.ui.theme.HamTheme
import org.brianwebb.hamsprint.ui.theme.Radius
import org.brianwebb.hamsprint.ui.theme.Space

/**
 * The full framework: what it is, its rows, the pattern that ties them
 * together and its picture. Used by the training lesson and the library.
 */
@Composable
fun FoundationBody(foundation: Foundation, number: Int, total: Int, figures: List<String>, modifier: Modifier = Modifier) {
    val c = HamTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(HamIcons.Layers, null, tint = c.accent, modifier = Modifier.size(16.dp))
            Text(
                "FOUNDATION $number OF $total",
                style = MaterialTheme.typography.labelSmall,
                color = c.accent,
                modifier = Modifier.padding(start = Space.xs),
            )
        }
        Text(
            foundation.title,
            style = MaterialTheme.typography.headlineMedium,
            color = c.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(foundation.summary, style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
        FrameworkTable(foundation.points)
        foundation.hook?.let { HookCard(it) }
        figures.forEach { FigureCard(it) }
        foundation.aid?.let { VisualAidCard(it) }
    }
}

/** The rows of a framework as a two column table; [highlight] marks the rows the current question asks about. */
@Composable
fun FrameworkTable(points: List<FoundationPoint>, modifier: Modifier = Modifier, highlight: Set<FoundationPoint> = emptySet()) {
    val c = HamTheme.colors
    val shape = RoundedCornerShape(Radius.md)
    Column(
        modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.separator, shape),
    ) {
        points.forEachIndexed { i, p ->
            val hot = p in highlight
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (hot) c.accentSoft else c.surface)
                    .padding(horizontal = Space.lg, vertical = Space.md)
                    .clearAndSetSemantics {
                        contentDescription = "${p.label}: ${p.value}"
                        if (hot) stateDescription = "Asked in this question"
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(if (hot) c.accent else c.surfacePressed),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${i + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hot) c.onAccent else c.textSecondary,
                    )
                }
                Text(
                    p.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hot) c.textPrimary else c.textSecondary,
                    modifier = Modifier.padding(start = Space.md).weight(0.45f),
                )
                Text(
                    p.value,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (hot) c.accent else c.textPrimary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(start = Space.md).weight(0.55f),
                )
            }
            if (i < points.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(c.separator))
        }
    }
}

@Composable
private fun HookCard(hook: String) {
    val c = HamTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(c.accentSoft).padding(Space.lg),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(HamIcons.Sparkle, null, tint = c.accent, modifier = Modifier.padding(top = 2.dp).size(16.dp))
        Column(Modifier.padding(start = Space.md)) {
            Text("THE PATTERN", style = MaterialTheme.typography.labelSmall, color = c.accent)
            Text(hook, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/**
 * A compact reminder on a question card: the framework's name and the rows
 * this question asks about, expandable to the whole framework.
 */
@Composable
fun BuildsOnCard(foundation: Foundation, questionId: String, modifier: Modifier = Modifier) {
    val c = HamTheme.colors
    val shape = RoundedCornerShape(Radius.md)
    val asked = foundation.pointsFor(questionId)
    var expanded by rememberSaveable(foundation.id, questionId) { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.separator, shape)
            .animateContentSize()
            .padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { heading() }) {
            Icon(HamIcons.Layers, null, tint = c.accent, modifier = Modifier.size(14.dp))
            Text(
                "BUILDS ON",
                style = MaterialTheme.typography.labelSmall,
                color = c.accent,
                modifier = Modifier.padding(start = Space.xs, end = Space.sm),
            )
            Text(foundation.title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
        }
        val shown = if (expanded || asked.isEmpty()) foundation.points else asked
        if (asked.isEmpty() && !expanded) {
            Text(foundation.summary, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        } else {
            FrameworkTable(shown, highlight = asked.toSet())
        }
        AnimatedVisibility(expanded && foundation.hook != null) {
            foundation.hook?.let { HookCard(it) }
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(Radius.sm))
                .pressable({ expanded = !expanded }, onClickLabel = if (expanded) "Show less" else "Show the whole framework")
                .semantics {
                    role = Role.Button
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                }
                .padding(vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (expanded) "Show less" else "See all ${foundation.points.size} rows",
                style = MaterialTheme.typography.labelMedium,
                color = c.accent,
            )
            Icon(
                HamIcons.ChevronDown, null, tint = c.accent,
                modifier = Modifier.padding(start = Space.xs).size(14.dp).rotate(if (expanded) 180f else 0f),
            )
        }
    }
}
