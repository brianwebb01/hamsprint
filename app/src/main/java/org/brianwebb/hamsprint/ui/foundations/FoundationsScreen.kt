package org.brianwebb.hamsprint.ui.foundations

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.brianwebb.hamsprint.ui.LocalAppContainer
import org.brianwebb.hamsprint.ui.components.CircleIconButton
import org.brianwebb.hamsprint.ui.components.EmptyState
import org.brianwebb.hamsprint.ui.components.HamCard
import org.brianwebb.hamsprint.ui.components.HamIcons
import org.brianwebb.hamsprint.ui.components.ProgressBar
import org.brianwebb.hamsprint.ui.components.SecondaryButton
import org.brianwebb.hamsprint.ui.theme.HamTheme
import org.brianwebb.hamsprint.ui.theme.Radius
import org.brianwebb.hamsprint.ui.theme.Space

/** Every foundation for the current exam; tapping one opens it in full. */
@Composable
fun FoundationsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val container = LocalAppContainer.current
    val vm: FoundationsViewModel = viewModel { FoundationsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Header("Foundations", onBack)
        when (val s = state) {
            FoundationsUiState.Loading -> Centered { CircularProgressIndicator(color = HamTheme.colors.accent) }
            is FoundationsUiState.Error -> Centered {
                EmptyState("Couldn't load foundations", s.message) {
                    SecondaryButton("Try again", vm::load, Modifier.width(200.dp))
                }
            }
            is FoundationsUiState.Ready ->
                if (s.items.isEmpty()) {
                    Centered { EmptyState("No foundations yet", "This exam's questions are trained without frameworks for now.") }
                } else {
                    Library(s, onOpen)
                }
        }
    }
}

@Composable
private fun Library(s: FoundationsUiState.Ready, onOpen: (String) -> Unit) {
    val c = HamTheme.colors
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.gutter),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item {
            Text(
                "The frameworks behind the facts. Training teaches each one before its questions; " +
                    "${s.taught} of ${s.items.size} taught so far.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                modifier = Modifier.padding(bottom = Space.sm),
            )
        }
        items(s.items, key = { it.foundation.id }) { item -> FoundationRow(item) { onOpen(item.foundation.id) } }
        item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars).height(Space.xl)) }
    }
}

@Composable
private fun FoundationRow(item: FoundationItem, onClick: () -> Unit) {
    val c = HamTheme.colors
    HamCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(Radius.sm)).background(if (item.seen) c.accentSoft else c.surfacePressed),
                contentAlignment = Alignment.Center,
            ) {
                Text("${item.number}", style = MaterialTheme.typography.labelMedium, color = if (item.seen) c.accent else c.textSecondary)
            }
            Column(Modifier.padding(start = Space.md).weight(1f)) {
                Text(item.foundation.title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Text(
                    item.foundation.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.height(Space.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressBar(
                        item.learned.toFloat() / item.total, height = 4.dp,
                        color = if (item.learned == item.total) c.success else c.accent,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${item.learned}/${item.total} learned",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textTertiary,
                        modifier = Modifier.padding(start = Space.md),
                    )
                }
            }
            Icon(HamIcons.Chevron, null, tint = c.textTertiary, modifier = Modifier.padding(start = Space.sm).size(18.dp))
        }
    }
}

/** One foundation in full, from the library. */
@Composable
fun FoundationDetailScreen(foundationId: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: FoundationsViewModel = viewModel { FoundationsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Header("", onBack)
        when (val s = state) {
            FoundationsUiState.Loading -> Centered { CircularProgressIndicator(color = HamTheme.colors.accent) }
            is FoundationsUiState.Error -> Centered { EmptyState("Couldn't load this foundation", s.message) }
            is FoundationsUiState.Ready -> {
                val item = s.items.firstOrNull { it.foundation.id == foundationId }
                if (item == null) {
                    Centered { EmptyState("Not found", "This foundation is no longer part of the exam.") }
                } else {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()
                            .padding(horizontal = Space.gutter),
                    ) {
                        FoundationBody(item.foundation, item.number, s.items.size, item.figures)
                        Text(
                            "${item.total} questions build on this. ${item.learned} learned.",
                            style = MaterialTheme.typography.bodySmall,
                            color = HamTheme.colors.textTertiary,
                            modifier = Modifier.padding(vertical = Space.xl),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.lg, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(HamIcons.Back, "Back", onBack)
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = HamTheme.colors.textPrimary,
            modifier = Modifier.padding(start = Space.lg).semantics { heading() },
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
