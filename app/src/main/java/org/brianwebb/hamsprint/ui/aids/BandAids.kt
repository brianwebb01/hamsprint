package org.brianwebb.hamsprint.ui.aids

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.brianwebb.hamsprint.domain.Band
import org.brianwebb.hamsprint.domain.BandPlanAid
import org.brianwebb.hamsprint.domain.Mode
import org.brianwebb.hamsprint.domain.ModesAid
import org.brianwebb.hamsprint.domain.Privilege
import org.brianwebb.hamsprint.domain.RepeaterAid
import org.brianwebb.hamsprint.domain.SegmentMode
import org.brianwebb.hamsprint.domain.SpectrumAid
import org.brianwebb.hamsprint.domain.WavelengthAid
import org.brianwebb.hamsprint.domain.Zone
import org.brianwebb.hamsprint.domain.logPosition
import org.brianwebb.hamsprint.ui.theme.HamColors
import org.brianwebb.hamsprint.ui.theme.HamTheme
import org.brianwebb.hamsprint.ui.theme.Radius
import org.brianwebb.hamsprint.ui.theme.Space
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin

// Mode colors use only the three slots that stay distinct for every pair; "all modes" adds a hatch.
internal fun HamColors.modeColor(mode: SegmentMode): Color = when (mode) {
    SegmentMode.CW_ONLY -> viz.slot2
    SegmentMode.CW_DATA -> viz.slot3
    SegmentMode.PHONE, SegmentMode.ALL -> viz.slot1
}

/** Bands are color coded by ITU zone, so "is this HF or VHF" has a color to hang on. */
internal fun HamColors.zoneColor(zone: Zone): Color = when (zone) {
    Zone.LF, Zone.MF -> viz.slot1
    Zone.HF -> viz.slot2
    Zone.VHF -> viz.slot3
    Zone.UHF -> viz.slot4
}

private fun DrawScope.hatch(rect: Rect, color: Color) = clipRect(rect.left, rect.top, rect.right, rect.bottom) {
    val step = 6.dp.toPx()
    var x = rect.left - rect.height
    while (x < rect.right) {
        drawLine(color, Offset(x, rect.bottom), Offset(x + rect.height, rect.top), 1.5.dp.toPx())
        x += step
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BandPlanView(aid: BandPlanAid) {
    val c = HamTheme.colors
    val band = aid.band
    val measurer = rememberTextMeasurer()
    val type = MaterialTheme.typography
    val rowLabel = type.labelMedium.copy(color = c.textSecondary)
    val tick = type.bodySmall.copy(fontSize = type.labelSmall.fontSize, color = c.textTertiary)
    val markStyle = type.labelMedium.copy(color = c.accent, fontWeight = FontWeight.Bold)
    val span = band.highMHz - band.lowMHz

    Text(
        formatRange(band.lowMHz, band.highMHz),
        style = type.labelMedium, color = c.textSecondary,
    )
    Canvas(Modifier.fillMaxWidth().height(146.dp).padding(top = Space.xs)) {
        val labelW = 56.dp.toPx()
        val left = labelW
        val width = size.width - labelW
        val top = 22.dp.toPx()
        val rowH = 22.dp.toPx()
        val gap = 10.dp.toPx()
        fun x(mhz: Double) = left + ((mhz - band.lowMHz) / span * width).toFloat()
        val radius = CornerRadius(6.dp.toPx())

        Privilege.entries.forEachIndexed { i, p ->
            val y = top + i * (rowH + gap)
            val nameLayout = measurer.measure(p.label, rowLabel)
            label(measurer, p.label, Offset(0f, y + (rowH - nameLayout.size.height) / 2), rowLabel, rowLabel.color)
            drawRoundRect(c.viz.track, Offset(left, y), Size(width, rowH), radius)
            val segs = band.segments(p)
            if (segs.isEmpty()) {
                label(measurer, "No privileges", Offset(left + width / 2, y + rowH / 2), tick, c.textTertiary, center = true)
            }
            segs.forEach { s ->
                val inset = 1.dp.toPx()
                val x0 = x(s.lowMHz) + inset
                val x1 = max(x(s.highMHz) - inset, x0 + 3.dp.toPx())
                val rect = Rect(x0, y, x1, y + rowH)
                drawRoundRect(c.modeColor(s.mode), rect.topLeft, rect.size, radius)
                if (s.mode == SegmentMode.ALL) hatch(rect, c.onAccent.copy(alpha = 0.28f))
            }
        }
        val rowsBottom = top + 3 * rowH + 2 * gap

        // Edge and boundary labels, skipping any that would collide.
        val bounds = (listOf(band.lowMHz, band.highMHz) +
            Privilege.entries.flatMap { p -> band.segments(p).flatMap { listOf(it.lowMHz, it.highMHz) } })
            .distinct().sorted()
        val placed = mutableListOf<ClosedFloatingPointRange<Float>>()
        val pad = 6.dp.toPx()
        (listOf(band.lowMHz, band.highMHz) + bounds).distinct().forEach { f ->
            val text = formatMHz(f)
            val layout = measurer.measure(text, tick)
            val tx = (x(f) - layout.size.width / 2f).coerceIn(left, size.width - layout.size.width)
            val span = (tx - pad)..(tx + layout.size.width + pad)
            if (placed.any { it.start < span.endInclusive && span.start < it.endInclusive }) return@forEach
            placed += span
            label(measurer, text, Offset(tx, rowsBottom + 6.dp.toPx()), tick, tick.color)
        }

        aid.mark?.let { m ->
            val x0 = x(m.start)
            val x1 = x(m.endInclusive)
            if (x1 - x0 < 3.dp.toPx()) {
                val cx = (x0 + x1) / 2
                drawLine(c.accent, Offset(cx, top - 4.dp.toPx()), Offset(cx, rowsBottom + 2.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                drawCircle(c.accent, 4.dp.toPx(), Offset(cx, top - 4.dp.toPx()))
            } else {
                drawRoundRect(c.accent.copy(alpha = 0.16f), Offset(x0, top - 4.dp.toPx()), Size(x1 - x0, rowsBottom - top + 8.dp.toPx()), CornerRadius(4.dp.toPx()))
                drawRoundRect(
                    c.accent, Offset(x0, top - 4.dp.toPx()), Size(x1 - x0, rowsBottom - top + 8.dp.toPx()), CornerRadius(4.dp.toPx()),
                    style = Stroke(2.dp.toPx()),
                )
            }
            val text = if (m.start == m.endInclusive) formatMHz(m.start) else "${formatMHz(m.start)} to ${formatMHz(m.endInclusive)}"
            val layout = measurer.measure(text, markStyle)
            val tx = ((x0 + x1) / 2 - layout.size.width / 2f).coerceIn(left, size.width - layout.size.width)
            label(measurer, text, Offset(tx, 0f), markStyle, c.accent)
        }
    }
    val modes = Privilege.entries.flatMap { band.segments(it) }.map { it.mode }.distinct().sortedBy { it.ordinal }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        modes.forEach { mode -> LegendChip(c.modeColor(mode), mode.label, hatched = mode == SegmentMode.ALL) }
    }
}

@Composable
internal fun LegendChip(color: Color, text: String, hatched: Boolean = false) {
    val c = HamTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(color).drawWithContent {
                drawContent()
                if (hatched) hatch(Rect(Offset.Zero, size), c.onAccent.copy(alpha = 0.35f))
            },
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(start = 6.dp))
    }
}

private const val SPECTRUM_LOW = 0.1
private const val SPECTRUM_HIGH = 3000.0

@Composable
internal fun SpectrumView(aid: SpectrumAid) {
    val c = HamTheme.colors
    val measurer = rememberTextMeasurer()
    val type = MaterialTheme.typography
    val zoneStyle = type.labelSmall.copy(color = c.textSecondary)
    val tick = type.bodySmall.copy(fontSize = type.labelSmall.fontSize, color = c.textTertiary)
    val bandStyle = type.labelMedium.copy(color = c.textPrimary)
    val highlighted = aid.highlighted.toSet()

    Canvas(Modifier.fillMaxWidth().height(124.dp)) {
        fun x(mhz: Double) = logPosition(mhz, SPECTRUM_LOW, SPECTRUM_HIGH) * size.width
        val stripTop = 0f
        val stripH = 22.dp.toPx()
        val zones = listOf(
            Triple("MF", SPECTRUM_LOW, 3.0) to Zone.MF,
            Triple("HF", 3.0, 30.0) to Zone.HF,
            Triple("VHF", 30.0, 300.0) to Zone.VHF,
            Triple("UHF", 300.0, SPECTRUM_HIGH) to Zone.UHF,
        )
        zones.forEach { (z, zone) ->
            val (name, lo, hi) = z
            val x0 = x(lo) + 1.dp.toPx()
            val x1 = x(hi) - 1.dp.toPx()
            drawRoundRect(c.zoneColor(zone).copy(alpha = 0.18f), Offset(x0, stripTop), Size(x1 - x0, stripH), CornerRadius(6.dp.toPx()))
            label(measurer, name, Offset((x0 + x1) / 2, stripTop + stripH / 2), zoneStyle, zoneStyle.color, center = true)
        }

        val baseY = 58.dp.toPx()
        Band.entries.forEach { b ->
            val isHot = b in highlighted
            val color = c.zoneColor(b.zone).copy(alpha = if (isHot || highlighted.isEmpty()) 1f else 0.4f)
            val h = if (isHot) 26.dp.toPx() else 14.dp.toPx()
            val w = if (isHot) 6.dp.toPx() else 3.dp.toPx()
            val cx = x(kotlin.math.sqrt(b.lowMHz * b.highMHz))
            drawRoundRect(color, Offset(cx - w / 2, baseY - h), Size(w, h), CornerRadius(w / 2))
        }
        // Labels for highlighted bands, staggered onto two lines when they crowd.
        val placed = mutableListOf<Pair<Float, Int>>()
        aid.highlighted.sortedBy { it.lowMHz }.forEach { b ->
            val cx = x(kotlin.math.sqrt(b.lowMHz * b.highMHz))
            val layout = measurer.measure(b.id, bandStyle)
            val half = layout.size.width / 2f + 3.dp.toPx()
            val line = if (placed.any { (px, l) -> l == 0 && kotlin.math.abs(px - cx) < half * 2 }) 1 else 0
            placed += cx to line
            val ty = baseY + 6.dp.toPx() + line * 18.dp.toPx()
            val tx = (cx - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
            label(measurer, b.id, Offset(tx, ty), bandStyle, bandStyle.color)
        }
        aid.markMHz?.let { m ->
            val mx = x(m)
            val path = Path().apply {
                moveTo(mx, baseY - 30.dp.toPx())
                lineTo(mx - 6.dp.toPx(), baseY - 40.dp.toPx())
                lineTo(mx + 6.dp.toPx(), baseY - 40.dp.toPx())
                close()
            }
            drawPath(path, c.accent)
        }
        val axisY = size.height - 14.dp.toPx()
        listOf(1.0 to "1 MHz", 10.0 to "10", 100.0 to "100", 1000.0 to "1 GHz").forEach { (f, t) ->
            val px = x(f)
            drawLine(c.separator, Offset(px, axisY - 6.dp.toPx()), Offset(px, axisY - 2.dp.toPx()), 1.dp.toPx())
            label(measurer, t, Offset(px, axisY + 5.dp.toPx()), tick, tick.color, center = true)
        }
    }
    aid.highlighted.forEach { b ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(c.zoneColor(b.zone)))
            Text(b.label, style = type.titleSmall, color = c.textPrimary, modifier = Modifier.padding(start = Space.sm).weight(1f))
            Text(
                "${formatRange(b.lowMHz, b.highMHz)}  ${b.zone.label}",
                style = type.bodySmall, color = c.textSecondary,
            )
        }
    }
    aid.markMHz?.let { m ->
        Text("Marked: ${formatFrequency(m)}", style = type.labelMedium, color = c.accent)
    }
}

@Composable
internal fun WavelengthView(aid: WavelengthAid) {
    val c = HamTheme.colors
    val type = MaterialTheme.typography
    FormulaLine(if (aid.fromMeters) "f = 300 / λ" else "λ = 300 / f")
    WorkedLine(
        (if (aid.example) "Example: " else "") +
            if (aid.fromMeters) "300 / ${formatNumber(aid.meters)} m" else "300 / ${formatNumber(aid.freqMHz)} MHz",
        if (aid.fromMeters) "${formatNumber(aid.freqMHz)} MHz" else "${formatNumber(aid.meters)} m",
    )
    val wave = c.accent
    val measurer = rememberTextMeasurer()
    val tick = type.labelMedium.copy(color = c.textSecondary)
    Canvas(Modifier.fillMaxWidth().height(64.dp)) {
        val mid = size.height * 0.4f
        val amp = size.height * 0.28f
        val path = Path()
        val steps = 120
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val px = t * size.width
            val py = mid - amp * sin(t * 2 * PI * 1.5).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, wave, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        // One full cycle spans two thirds of the width (1.5 cycles drawn).
        val y = size.height - 12.dp.toPx()
        val end = size.width * (2f / 3f)
        drawLine(c.textSecondary, Offset(0f, y), Offset(end, y), 1.5.dp.toPx())
        drawLine(c.textSecondary, Offset(0f, y - 5.dp.toPx()), Offset(0f, y + 5.dp.toPx()), 1.5.dp.toPx())
        drawLine(c.textSecondary, Offset(end, y - 5.dp.toPx()), Offset(end, y + 5.dp.toPx()), 1.5.dp.toPx())
        label(measurer, "one wavelength", Offset(end + 8.dp.toPx(), y - 8.dp.toPx()), tick, tick.color)
    }
    aid.band?.let {
        Text("In the ${it.label.replace("meters", "meter").replace("centimeters", "centimeter")} band", style = type.labelMedium, color = c.textSecondary)
    }
}

@Composable
internal fun FormulaLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = HamTheme.colors.textPrimary,
    )
}

/** "inputs = result", with the result in the accent color. */
@Composable
internal fun WorkedLine(inputs: String, result: String) {
    val c = HamTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).background(c.accentSoft).padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$inputs = ", style = MaterialTheme.typography.titleMedium, color = c.textSecondary)
        Text(result, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = c.accent)
    }
}

@Composable
internal fun RepeaterView(aid: RepeaterAid) {
    val c = HamTheme.colors
    val type = MaterialTheme.typography
    val r = aid.repeater
    val offset = if (r.offsetMHz < 1) "${formatNumber(r.offsetMHz * 1000)} kHz" else "${formatNumber(r.offsetMHz)} MHz"
    Row(verticalAlignment = Alignment.CenterVertically) {
        FrequencyTile("You transmit", formatMHz(r.sampleInputMHz), "repeater input", c.viz.slot2, Modifier.weight(1f))
        Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("± $offset", style = type.titleSmall, color = c.accent, textAlign = TextAlign.Center)
            Canvas(Modifier.fillMaxWidth().height(14.dp).padding(horizontal = Space.sm)) {
                val y = size.height / 2
                drawLine(c.accent, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), StrokeCap.Round)
                drawLine(c.accent, Offset(size.width, y), Offset(size.width - 6.dp.toPx(), y - 5.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                drawLine(c.accent, Offset(size.width, y), Offset(size.width - 6.dp.toPx(), y + 5.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            }
            Text("offset", style = type.bodySmall, color = c.textTertiary)
        }
        FrequencyTile("You listen", formatMHz(r.sampleOutputMHz), "repeater output", c.viz.slot1, Modifier.weight(1f))
    }
    if (aid.tone) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).background(c.viz.track).padding(Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(28.dp, 16.dp)) {
                val path = Path()
                for (i in 0..40) {
                    val t = i / 40f
                    val py = size.height / 2 - size.height * 0.4f * sin(t * 2 * PI * 2).toFloat()
                    if (i == 0) path.moveTo(t * size.width, py) else path.lineTo(t * size.width, py)
                }
                drawPath(path, c.viz.slot3, style = Stroke(2.dp.toPx()))
            }
            Text(
                "Plus a CTCSS tone, 67 to 254 Hz, too low to hear. It opens the repeater.",
                style = type.bodySmall, color = c.textSecondary, modifier = Modifier.padding(start = Space.md),
            )
        }
    }
}

@Composable
private fun FrequencyTile(top: String, value: String, bottom: String, color: Color, modifier: Modifier) {
    val c = HamTheme.colors
    val shape = RoundedCornerShape(Radius.sm)
    Column(
        modifier.clip(shape).background(color.copy(alpha = 0.12f)).border(1.5.dp, color, shape).padding(Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(top, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        Text(bottom, style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
    }
}

@Composable
internal fun ModesView(aid: ModesAid) {
    val c = HamTheme.colors
    val type = MaterialTheme.typography
    val maxLog = log10(Mode.ATV.bandwidthHz / 10)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Mode.entries.forEach { mode ->
            val hot = mode == aid.highlight
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    mode.label,
                    style = if (hot) type.labelMedium else type.bodySmall,
                    color = if (hot) c.textPrimary else c.textSecondary,
                    modifier = Modifier.width(92.dp),
                )
                Box(Modifier.weight(1f).height(12.dp)) {
                    val fraction = (log10(mode.bandwidthHz / 10) / maxLog).toFloat().coerceIn(0.04f, 1f)
                    Box(
                        Modifier.fillMaxWidth(fraction).height(12.dp).clip(RoundedCornerShape(4.dp))
                            .background(if (hot) c.accent else c.textTertiary.copy(alpha = 0.35f)),
                    )
                }
                Text(
                    mode.bandwidthLabel,
                    style = if (hot) type.labelMedium else type.bodySmall,
                    color = if (hot) c.accent else c.textTertiary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(86.dp),
                )
            }
        }
    }
    Text("Log scale: each step right is about 10 times wider.", style = type.bodySmall, color = c.textTertiary)
}

/** A dashed horizontal guide, used by several aids. */
internal fun DrawScope.dashed(color: Color, from: Offset, to: Offset) =
    drawLine(color, from, to, 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
