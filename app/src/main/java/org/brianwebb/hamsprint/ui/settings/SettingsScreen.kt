package org.brianwebb.hamsprint.ui.settings

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.brianwebb.hamsprint.BuildConfig
import org.brianwebb.hamsprint.data.ThemeMode
import org.brianwebb.hamsprint.domain.LicenseClass
import org.brianwebb.hamsprint.domain.QuestionPool
import org.brianwebb.hamsprint.ui.LocalAppContainer
import org.brianwebb.hamsprint.ui.components.CircleIconButton
import org.brianwebb.hamsprint.ui.components.HamCard
import org.brianwebb.hamsprint.ui.components.HamIcons
import org.brianwebb.hamsprint.ui.components.LocalHaptics
import org.brianwebb.hamsprint.ui.components.SectionHeader
import org.brianwebb.hamsprint.ui.components.pressable
import org.brianwebb.hamsprint.ui.exam.ConfirmDialog
import org.brianwebb.hamsprint.ui.formatDate
import org.brianwebb.hamsprint.ui.theme.HamTheme
import org.brianwebb.hamsprint.ui.theme.Radius
import org.brianwebb.hamsprint.ui.theme.Space

private const val SOURCE_URL = "https://github.com/brianwebb01/hamsprint"

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val c = HamTheme.colors
    val container = LocalAppContainer.current
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    val context = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    val pools by produceState<Map<LicenseClass, QuestionPool>>(emptyMap()) {
        value = withContext(Dispatchers.IO) { LicenseClass.entries.associateWith { container.pools.load(it) } }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
            .navigationBarsPadding().padding(horizontal = Space.gutter),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Row(Modifier.padding(top = Space.sm), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(HamIcons.Back, "Back", onBack)
        }
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineLarge,
            color = c.textPrimary,
            modifier = Modifier.padding(vertical = Space.md).semantics { heading() },
        )

        SectionHeader("Exam")
        Group {
            LicenseClass.entries.forEachIndexed { i, license ->
                val pool = pools[license]
                ChoiceRow(
                    title = license.displayName,
                    subtitle = pool?.let { "${it.questions.size} questions · valid through ${formatDate(it.validThrough)}" },
                    selected = settings.license == license,
                    onClick = { haptics.tick(); container.settings.update { it.copy(license = license) } },
                )
                if (i < LicenseClass.entries.lastIndex) Divider()
            }
        }

        SectionHeader("Study", Modifier.padding(top = Space.lg))
        Group {
            ToggleRow("Preview new questions", "See the answer before you're asked for it", settings.previewNew) { v ->
                container.settings.update { it.copy(previewNew = v) }
            }
            Divider()
            ToggleRow("Shuffle answers", "Learn the answer itself, not its letter", settings.shuffleAnswers) { v ->
                container.settings.update { it.copy(shuffleAnswers = v) }
            }
            Divider()
            ToggleRow("Auto-advance", "Move on automatically after a correct answer", settings.autoAdvance) { v ->
                container.settings.update { it.copy(autoAdvance = v) }
            }
            Divider()
            ToggleRow("Haptics", null, settings.haptics) { v -> container.settings.update { it.copy(haptics = v) } }
        }

        SectionHeader("Appearance", Modifier.padding(top = Space.lg))
        Segmented(
            options = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"),
            selected = settings.theme,
            onSelect = { mode -> haptics.tick(); container.settings.update { it.copy(theme = mode) } },
        )

        SectionHeader("Progress", Modifier.padding(top = Space.lg))
        Group {
            ActionRow(
                "Reset ${settings.license?.displayName ?: ""} progress",
                destructive = true,
                onClick = { confirmReset = true },
            )
        }

        SectionHeader("About", Modifier.padding(top = Space.lg))
        Group {
            InfoRow("Version", BuildConfig.VERSION_NAME)
            Divider()
            ActionRow("Source code", onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri()))
            })
        }
        Text(
            buildString {
                append("Questions are the official NCVEC Question Pool Committee pools, released into the public domain:\n")
                pools.values.forEach { append("• ${it.name}, ${it.revision}\n") }
                append("\nHamSprint is free software under the GNU GPL v3. It is not affiliated with the FCC, NCVEC, or ARRL.")
            },
            style = MaterialTheme.typography.bodySmall,
            color = c.textTertiary,
            modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.md),
        )
        Spacer(Modifier.height(Space.xl))
    }

    if (confirmReset) {
        val license = settings.license
        ConfirmDialog(
            title = "Reset progress?",
            body = "This erases everything you've learned for ${license?.displayName} and your exam history. It can't be undone.",
            confirm = "Reset",
            onConfirm = {
                confirmReset = false
                if (license != null) container.appScope.launch { container.progress.reset(license) }
            },
            onDismiss = { confirmReset = false },
        )
    }
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    HamCard(padding = PaddingValues(0.dp), shape = RoundedCornerShape(Radius.md), content = content)
}

@Composable
private fun Divider() {
    Box(Modifier.padding(start = Space.lg).fillMaxWidth().height(1.dp).background(HamTheme.colors.separator))
}

@Composable
private fun ChoiceRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    val c = HamTheme.colors
    Row(
        Modifier.fillMaxWidth().pressable(onClick, pressedScale = 0.99f, role = Role.RadioButton)
            .padding(horizontal = Space.lg, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        if (selected) Icon(HamIcons.Check, "Selected", tint = c.accent, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = HamTheme.colors
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = Space.lg, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = Space.md)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.success,
                checkedThumbColor = c.onAccent,
                uncheckedTrackColor = c.surfacePressed,
                uncheckedThumbColor = c.onAccent,
                uncheckedBorderColor = c.surfacePressed,
                checkedBorderColor = c.success,
            ),
        )
    }
}

@Composable
private fun ActionRow(title: String, destructive: Boolean = false, onClick: () -> Unit) {
    val c = HamTheme.colors
    Row(
        Modifier.fillMaxWidth().pressable(onClick, pressedScale = 0.99f).padding(horizontal = Space.lg, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = if (destructive) c.danger else c.textPrimary, modifier = Modifier.weight(1f))
        if (!destructive) Icon(HamIcons.Chevron, null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    val c = HamTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = 16.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
    }
}

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val c = HamTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).background(c.surfacePressed).padding(3.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val bg by animateColorAsState(if (isSelected) c.surface else c.surfacePressed, label = "seg")
            Box(
                Modifier.weight(1f).pressable({ onSelect(value) }, pressedScale = 0.97f, role = Role.RadioButton)
                    .clip(RoundedCornerShape(10.dp)).background(bg).padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = if (isSelected) c.textPrimary else c.textSecondary)
            }
        }
    }
}
