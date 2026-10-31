package org.brianwebb.hamsprint.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.brianwebb.hamsprint.ui.theme.HamTheme
import org.brianwebb.hamsprint.ui.theme.Radius
import org.brianwebb.hamsprint.ui.theme.Space

// ---------------------------------------------------------------------------
// Haptics
// ---------------------------------------------------------------------------

/** Semantic haptics; a no-op when the user has turned haptics off. */
class Haptics(private val view: View?, private val enabled: Boolean) {
    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)
    fun tap() = perform(HapticFeedbackConstants.VIRTUAL_KEY)
    fun success() = perform(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
    )
    fun error() = perform(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS,
    )

    private fun perform(constant: Int) {
        if (enabled) view?.performHapticFeedback(constant)
    }
}

val LocalHaptics = staticCompositionLocalOf { Haptics(null, false) }

@Composable
fun rememberHaptics(enabled: Boolean): Haptics {
    val view = LocalView.current
    return remember(view, enabled) { Haptics(view, enabled) }
}

// ---------------------------------------------------------------------------
// Press interaction: a soft spring scale instead of a ripple.
// ---------------------------------------------------------------------------

@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    role: Role = Role.Button,
    onClickLabel: String? = null,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return this
        .scale(scale)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
}

// ---------------------------------------------------------------------------
// Surfaces
// ---------------------------------------------------------------------------

@Composable
fun HamCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.lg),
    padding: PaddingValues = PaddingValues(Space.xl),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = HamTheme.colors
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.pressable(onClick) else Modifier)
            .shadow(if (c.isDark) 0.dp else 12.dp, shape, ambientColor = c.shadow, spotColor = c.shadow)
            .clip(shape)
            .background(c.surface)
            .padding(padding),
        content = content,
    )
}

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val c = HamTheme.colors
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(Radius.md)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .pressable({ haptics.tap(); onClick() }, enabled = enabled)
            .shadow(if (c.isDark || !enabled) 0.dp else 16.dp, shape, ambientColor = c.brandStart, spotColor = c.brandStart)
            .clip(shape)
            .background(if (enabled) c.brand else Brush.linearGradient(listOf(c.surfacePressed, c.surfacePressed)))
            .padding(horizontal = Space.xl, vertical = Space.md),
        contentAlignment = Alignment.Center,
    ) {
        val fg = if (enabled) c.onAccent else c.textTertiary
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
                Box(Modifier.size(Space.sm))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = HamTheme.colors
    val haptics = LocalHaptics.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .pressable({ haptics.tap(); onClick() }, enabled = enabled)
            .clip(RoundedCornerShape(Radius.md))
            .background(c.accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) c.accent else c.textTertiary)
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = HamTheme.colors.textPrimary,
    background: Color = HamTheme.colors.surfaceRaised,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .pressable(onClick, pressedScale = 0.9f, onClickLabel = contentDescription)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp))
    }
}

// ---------------------------------------------------------------------------
// Indicators
// ---------------------------------------------------------------------------

@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    brush: Brush? = null,
    color: Color = HamTheme.colors.accent,
    track: Color = HamTheme.colors.separator,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(450), label = "progress")
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = size.height / 2
        drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
        if (animated > 0f) {
            val w = (size.width * animated).coerceAtLeast(size.height)
            if (brush != null) {
                drawRoundRect(brush, size = Size(w, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
            } else {
                drawRoundRect(color, size = Size(w, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
            }
        }
    }
}

/** Gradient arc ring, used for the pass-probability gauge. */
@Composable
fun Ring(
    progress: Float,
    modifier: Modifier = Modifier,
    stroke: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val c = HamTheme.colors
    val animated by animateFloatAsState(
        progress.coerceIn(0f, 1f),
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow),
        label = "ring",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val sw = stroke.toPx()
            val arcSize = Size(size.width - sw, size.height - sw)
            val topLeft = Offset(sw / 2, sw / 2)
            drawArc(c.separator, 0f, 360f, false, topLeft, arcSize, style = Stroke(sw))
            if (animated > 0.004f) rotate(-90f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(c.brandEnd, c.brandStart, c.brandEnd)),
                    startAngle = 0f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

@Composable
fun Badge(text: String, color: Color, background: Color, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = HamTheme.colors.textSecondary,
        modifier = modifier.padding(start = Space.xs, bottom = Space.sm),
    )
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.padding(Space.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = HamTheme.colors.textPrimary, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = HamTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Space.sm),
        )
        if (action != null) {
            Box(Modifier.padding(top = Space.xl)) { action() }
        }
    }
}
