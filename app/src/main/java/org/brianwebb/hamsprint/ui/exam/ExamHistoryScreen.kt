package org.brianwebb.hamsprint.ui.exam

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.brianwebb.hamsprint.data.ExamRecord
import org.brianwebb.hamsprint.domain.Readiness
import org.brianwebb.hamsprint.ui.LocalAppContainer
import org.brianwebb.hamsprint.ui.components.Badge
import org.brianwebb.hamsprint.ui.components.CircleIconButton
import org.brianwebb.hamsprint.ui.components.EmptyState
import org.brianwebb.hamsprint.ui.components.HamCard
import org.brianwebb.hamsprint.ui.components.HamIcons
import org.brianwebb.hamsprint.ui.components.PrimaryButton
import org.brianwebb.hamsprint.ui.components.SecondaryButton
import org.brianwebb.hamsprint.ui.components.SectionHeader
import org.brianwebb.hamsprint.ui.formatMillis
import org.brianwebb.hamsprint.ui.formatPercent
import org.brianwebb.hamsprint.ui.theme.HamTheme
import org.brianwebb.hamsprint.ui.theme.Radius
import org.brianwebb.hamsprint.ui.theme.Space
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ExamHistoryScreen(onBack: () -> Unit, onStartExam: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: ExamHistoryViewModel = viewModel { ExamHistoryViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()

    when (val s = state) {
        ExamHistoryUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = HamTheme.colors.accent)
        }
        is ExamHistoryUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState("Something went wrong", s.message) { SecondaryButton("Back", onBack, Modifier.width(200.dp)) }
        }
        is ExamHistoryUiState.Ready -> HistoryContent(s.summary, onBack, onStartExam)
    }
}

@Composable
private fun HistoryContent(summary: ExamHistorySummary, onBack: () -> Unit, onStartExam: () -> Unit) {
    val c = HamTheme.colors
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.gutter),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        item {
            Row(Modifier.statusBarsPadding().padding(top = Space.sm)) { CircleIconButton(HamIcons.Back, "Back", onBack) }
            Text(
                "Practice exams",
                style = MaterialTheme.typography.headlineLarge,
                color = c.textPrimary,
                modifier = Modifier.padding(top = Space.lg).semantics { heading() },
            )
            Text(
                "${summary.total} questions drawn the way real exams are · pass with ${summary.passingScore}",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                modifier = Modifier.padding(top = Space.xs),
            )
        }
        item { VerdictCard(summary) }
        item {
            PrimaryButton(
                if (summary.attempts.isEmpty()) "Take your first exam" else "Start a new exam",
                onStartExam,
                leadingIcon = HamIcons.Play,
            )
        }
        if (summary.attempts.isEmpty()) {
            item {
                EmptyState(
                    "No attempts yet",
                    "Take a practice exam anytime. Each one shows exactly what you'd score today, and every miss goes to the front of your training queue.",
                )
            }
        } else {
            if (summary.attempts.size >= 2) {
                item {
                    SectionHeader("Score trend", Modifier.padding(top = Space.sm))
                    HamCard(padding = PaddingValues(Space.lg)) { ScoreTrend(summary) }
                }
            }
            item { SectionHeader("All attempts (${summary.attempts.size})", Modifier.padding(top = Space.sm)) }
            item {
                HamCard(padding = PaddingValues(vertical = Space.xs)) {
                    summary.attempts.forEachIndexed { i, record ->
                        AttemptRow(record, number = summary.attempts.size - i)
                        if (i < summary.attempts.lastIndex) {
                            Box(Modifier.padding(start = Space.lg).fillMaxWidth().height(1.dp).background(c.separator))
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(Space.lg).windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
}

@Composable
private fun VerdictCard(s: ExamHistorySummary) {
    val c = HamTheme.colors
    HamCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(if (s.examReady) c.success else c.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (s.examReady) HamIcons.Check else HamIcons.Target, null,
                    tint = if (s.examReady) c.onAccent else c.accent, modifier = Modifier.size(22.dp),
                )
            }
            Column(Modifier.padding(start = Space.lg)) {
                Text(
                    if (s.examReady) "Exam ready" else "Not confirmed yet",
                    style = MaterialTheme.typography.titleLarge,
                    color = c.textPrimary,
                )
                Text(verdictDetail(s), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }
        if (s.attempts.isEmpty()) return@HamCard
        Spacer(Modifier.height(Space.xl))
        Row(Modifier.fillMaxWidth()) {
            Metric("Pass chance", formatPercent(s.passProbability), Modifier.weight(1f))
            Metric("Recent avg", s.recentAverage?.let { "%.1f".format(it) } ?: "-", Modifier.weight(1f))
            Metric("Best", s.best?.score?.toString() ?: "-", Modifier.weight(1f))
            Metric("Streak", "${s.passStreak}", Modifier.weight(1f))
        }
    }
}

private fun verdictDetail(s: ExamHistorySummary): String {
    val needPasses = (ExamHistorySummary.CONFIRMING_PASSES - s.passStreak).coerceAtLeast(0)
    val modelReady = s.passProbability >= Readiness.READY_THRESHOLD
    return when {
        s.examReady -> "Passed your last ${s.passStreak} in a row with a ${formatPercent(s.passProbability)} pass chance. Book your session."
        !modelReady && s.attempts.isEmpty() -> "Train until your pass chance hits 95%, then pass 2 exams in a row."
        !modelReady -> "Keep training until your pass chance reaches 95%."
        else -> "Your pass chance is there. Pass $needPasses more exam${if (needPasses == 1) "" else "s"} in a row to confirm."
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    val c = HamTheme.colors
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}

/** Scores over time (oldest → newest) against the passing line. Tap a point to inspect it. */
@Composable
private fun ScoreTrend(s: ExamHistorySummary) {
    val c = HamTheme.colors
    val points = s.attempts.take(20).reversed()
    var selected by remember(points) { mutableStateOf(points.lastIndex) }
    val sel = points.getOrNull(selected)

    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            sel?.let { "${it.score}/${it.total}" } ?: "",
            style = MaterialTheme.typography.headlineSmall,
            color = c.textPrimary,
        )
        Text(
            sel?.let { "  ${dateLabel(it.takenAtMillis)} · ${if (it.passed) "passed" else "not passed"}" } ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
            modifier = Modifier.padding(bottom = 3.dp),
        )
    }
    Spacer(Modifier.height(Space.md))

    val minY = (minOf(points.minOf { it.score }, s.passingScore) - 3).coerceAtLeast(0)
    val maxY = s.total
    val description = "Score trend for the last ${points.size} exams: " +
        points.joinToString { "${it.score}" } + ". Passing score ${s.passingScore}."
    val measurer = rememberTextMeasurer()
    val passLabelStyle = MaterialTheme.typography.labelSmall.copy(color = c.textTertiary)
    Box {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .semantics { contentDescription = description }
                .pointerInput(points) {
                    detectTapGestures { tap ->
                        val inset = 10.dp.toPx()
                        val step = if (points.size > 1) (size.width - 2 * inset) / (points.size - 1f) else 0f
                        selected = if (step == 0f) 0 else ((tap.x - inset) / step).let { kotlin.math.round(it).toInt() }.coerceIn(0, points.lastIndex)
                    }
                },
        ) {
            val pad = 8.dp.toPx()
            val inset = 10.dp.toPx() // keeps end markers inside the card
            val w = size.width
            val h = size.height - pad * 2
            fun x(i: Int) = if (points.size > 1) inset + i * (w - 2 * inset) / (points.size - 1) else w / 2
            fun y(score: Int) = pad + h * (1f - (score - minY).toFloat() / (maxY - minY))

            // Recessive grid: top (perfect score) and bottom.
            drawLine(c.separator, Offset(0f, y(maxY)), Offset(w, y(maxY)), 1.dp.toPx())
            drawLine(c.separator, Offset(0f, y(minY)), Offset(w, y(minY)), 1.dp.toPx())
            // Passing line.
            drawLine(
                c.textTertiary, Offset(0f, y(s.passingScore)), Offset(w, y(s.passingScore)), 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
            val label = measurer.measure("Pass ${s.passingScore}", passLabelStyle)
            drawText(label, topLeft = Offset(w - label.size.width, y(s.passingScore) - label.size.height - 2.dp.toPx()))
            val path = androidx.compose.ui.graphics.Path().apply {
                points.forEachIndexed { i, p -> if (i == 0) moveTo(x(i), y(p.score)) else lineTo(x(i), y(p.score)) }
            }
            drawPath(path, c.accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (sel != null) {
                drawLine(c.separator, Offset(x(selected), 0f), Offset(x(selected), size.height), 1.dp.toPx())
            }
            points.forEachIndexed { i, p ->
                val r = if (i == selected) 6.dp.toPx() else 4.dp.toPx()
                drawCircle(c.surface, r + 2.dp.toPx(), Offset(x(i), y(p.score)))
                drawCircle(if (p.passed) c.success else c.danger, r, Offset(x(i), y(p.score)))
            }
        }
    }
    Text(
        "Last ${points.size} exams · dashed line is the passing score",
        style = MaterialTheme.typography.bodySmall,
        color = c.textTertiary,
        textAlign = TextAlign.Start,
        modifier = Modifier.padding(top = Space.sm),
    )
}

@Composable
private fun AttemptRow(record: ExamRecord, number: Int) {
    val c = HamTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(Radius.sm)).background(c.surfacePressed),
            contentAlignment = Alignment.Center,
        ) {
            Text("#$number", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
        }
        Column(Modifier.padding(start = Space.md).weight(1f)) {
            Text("${record.score} / ${record.total}", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Text(
                "${dateLabel(record.takenAtMillis)} · ${formatMillis(record.durationMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
        }
        Badge(
            if (record.passed) "Passed" else "Not yet",
            color = if (record.passed) c.success else c.danger,
            background = if (record.passed) c.successSoft else c.dangerSoft,
        )
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US)

private fun dateLabel(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dateFormat)
