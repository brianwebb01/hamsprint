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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.brianwebb.hamsprint.domain.CallSignAid
import org.brianwebb.hamsprint.domain.CodeSet
import org.brianwebb.hamsprint.domain.CodesAid
import org.brianwebb.hamsprint.domain.DecibelAid
import org.brianwebb.hamsprint.domain.FactAid
import org.brianwebb.hamsprint.domain.PhoneticAid
import org.brianwebb.hamsprint.domain.PrefixAid
import org.brianwebb.hamsprint.domain.SiPrefix
import org.brianwebb.hamsprint.domain.SwrAid
import org.brianwebb.hamsprint.domain.WaveMeasure
import org.brianwebb.hamsprint.domain.WaveformAid
import org.brianwebb.hamsprint.ui.theme.HamTheme
import org.brianwebb.hamsprint.ui.theme.Radius
import org.brianwebb.hamsprint.ui.theme.Space
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private fun formatDb(db: Double): String {
    val r = (db * 10).roundToInt() / 10.0
    val text = if (abs(r - r.roundToInt()) < 0.05) "${r.roundToInt()}" else "$r"
    return if (db > 0) "+$text" else text
}

private val DB_STEPS = listOf(-10.0 to "1/10", -6.0 to "1/4", -3.0 to "1/2", 3.0 to "2×", 6.0 to "4×", 10.0 to "10×", 20.0 to "100×")

@Composable
internal fun DecibelView(aid: DecibelAid) {
    val c = HamTheme.colors
    val type = MaterialTheme.typography
    val ratio = aid.ratio
    val ratioText = if (ratio >= 1) "${formatNumber(ratio)}× the power" else "${formatNumber(ratio * 100)}% of the power"
    FormulaLine("${formatDb(aid.db)} dB = $ratioText")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        DB_STEPS.forEach { (db, label) ->
            val hot = abs(db - aid.db) < 0.3
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (hot) c.accent else c.viz.track).padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(formatDb(db), style = type.labelMedium, color = if (hot) c.onAccent else c.textPrimary)
                Text(label, style = type.bodySmall, color = if (hot) c.onAccent else c.textTertiary)
            }
        }
    }
    Text("Every 3 dB doubles the power. Every 10 dB multiplies it by 10.", style = type.bodySmall, color = c.textSecondary)
    if (ratio < 1 && aid.watts == null) {
        WorkedLine("Power lost", "${formatNumber((1 - ratio) * 100)}%")
    }
    aid.resultWatts?.let { WorkedLine("${formatNumber(aid.watts!!)} W × ${formatNumber(ratio)}", "${formatNumber(it)} W") }
}

private val LADDER = listOf(SiPrefix.GIGA, SiPrefix.MEGA, SiPrefix.KILO, SiPrefix.NONE, SiPrefix.MILLI, SiPrefix.MICRO, SiPrefix.NANO, SiPrefix.PICO)

@Composable
internal fun PrefixView(aid: PrefixAid) {
    val c = HamTheme.colors
    val type = MaterialTheme.typography
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        LADDER.forEach { p ->
            val color = when (p) { aid.from -> c.viz.slot2; aid.to -> c.accent; else -> null }
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(color ?: c.viz.track).padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(p.symbol.ifEmpty { "1" }, style = type.titleSmall, color = if (color != null) c.onAccent else c.textPrimary)
                Text(if (p == SiPrefix.NONE) "base" else p.word, style = type.bodySmall.copy(fontSize = type.labelSmall.fontSize), color = if (color != null) c.onAccent else c.textTertiary)
            }
        }
    }
    val shift = aid.from.exponent - aid.to.exponent
    Text(
        "Each step is 1000 times. Move the decimal ${abs(shift)} places ${if (shift > 0) "right" else "left"}.",
        style = type.bodySmall, color = c.textSecondary,
    )
    fun withWord(v: Double, p: SiPrefix) = "${formatNumber(v, 7)}${if (p == SiPrefix.NONE) "" else " ${p.word}"}"
    WorkedLine(withWord(aid.value, aid.from), withWord(aid.result, aid.to))
}

@Composable
internal fun WaveformView(aid: WaveformAid) {
    val c = HamTheme.colors
    val measurer = rememberTextMeasurer()
    val type = MaterialTheme.typography
    val small = type.labelMedium
    fun color(m: WaveMeasure) = if (m == aid.solve || (aid.solve == WaveMeasure.PEP && m == WaveMeasure.RMS)) c.accent else c.textSecondary
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val w = size.width * 0.62f
        val mid = size.height / 2
        val amp = size.height * 0.4f
        val path = Path()
        for (i in 0..100) {
            val t = i / 100f
            val y = mid - amp * sin(t * 2 * PI).toFloat()
            if (i == 0) path.moveTo(t * w, y) else path.lineTo(t * w, y)
        }
        drawLine(c.separator, Offset(0f, mid), Offset(w, mid), 1.dp.toPx())
        drawPath(path, c.textPrimary, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        val rmsY = mid - amp * 0.707f
        dashed(color(WaveMeasure.PEAK), Offset(0f, mid - amp), Offset(size.width * 0.7f, mid - amp))
        dashed(color(WaveMeasure.RMS), Offset(0f, rmsY), Offset(size.width * 0.7f, rmsY))
        val bx = w + 10.dp.toPx()
        drawLine(color(WaveMeasure.PP), Offset(bx, mid - amp), Offset(bx, mid + amp), 2.dp.toPx())
        val tx = size.width * 0.72f
        label(measurer, "Peak ${formatNumber(aid.peak)} V", Offset(tx, mid - amp - 9.dp.toPx()), small, color(WaveMeasure.PEAK))
        label(measurer, "RMS ${formatNumber(aid.rms)} V", Offset(tx, rmsY - 1.dp.toPx()), small, color(WaveMeasure.RMS))
        label(measurer, "Peak to peak", Offset(bx + 8.dp.toPx(), mid + 4.dp.toPx()), small, color(WaveMeasure.PP))
        label(measurer, "${formatNumber(aid.peak * 2)} V", Offset(bx + 8.dp.toPx(), mid + 20.dp.toPx()), small, color(WaveMeasure.PP))
    }
    Text("RMS = 0.707 × peak. Peak to peak = 2 × peak.", style = type.bodySmall, color = c.textSecondary)
    when (aid.solve) {
        WaveMeasure.PEP -> WorkedLine("(${formatNumber(aid.rms)} V)² / ${formatNumber(aid.ohms!!)} Ω", "${formatNumber(aid.result)} W")
        else -> WorkedLine(aid.solve.label, "${formatNumber(aid.result)} V")
    }
}

@Composable
internal fun SwrView(aid: SwrAid) {
    val c = HamTheme.colors
    val measurer = rememberTextMeasurer()
    val small = MaterialTheme.typography.labelMedium
    // Reflection coefficient maps SWR from 1 to infinity onto 0 to 1, so the gauge stays readable.
    fun gamma(s: Double) = ((s - 1) / (s + 1)).toFloat()
    Canvas(Modifier.fillMaxWidth().height(116.dp)) {
        val r = minOf(size.width / 2.8f, size.height - 44.dp.toPx())
        val center = Offset(size.width / 2, size.height - 14.dp.toPx())
        val sw = 12.dp.toPx()
        val zones = listOf(1.0 to 1.5 to c.success, 1.5 to 3.0 to c.warning, 3.0 to 1000.0 to c.danger)
        zones.forEach { (range, color) ->
            val start = 180f + gamma(range.first) * 180f
            val end = 180f + gamma(range.second) * 180f
            drawArc(color.copy(alpha = 0.85f), start + 1f, end - start - 2f, false, Offset(center.x - r, center.y - r), Size(r * 2, r * 2), style = Stroke(sw))
        }
        listOf(1.0, 1.5, 2.0, 3.0, 5.0, 10.0).forEach { s ->
            val a = Math.toRadians(180.0 + gamma(s) * 180.0)
            val p = Offset(center.x + (r + 16.dp.toPx()) * kotlin.math.cos(a).toFloat(), center.y + (r + 16.dp.toPx()) * kotlin.math.sin(a).toFloat())
            label(measurer, formatNumber(s), p, small, c.textTertiary, center = true)
        }
        val a = Math.toRadians(180.0 + gamma(aid.swr) * 180.0)
        val tip = Offset(center.x + (r - 4.dp.toPx()) * kotlin.math.cos(a).toFloat(), center.y + (r - 4.dp.toPx()) * kotlin.math.sin(a).toFloat())
        drawLine(c.textPrimary, center, tip, 3.dp.toPx(), StrokeCap.Round)
        drawCircle(c.textPrimary, 6.dp.toPx(), center)
    }
    val verdict = when {
        aid.swr <= 1.05 -> "Perfect match"
        aid.swr <= 1.5 -> "Good match"
        aid.swr <= 3 -> "High: rigs start cutting power"
        else -> "Bad mismatch"
    }
    Text("${formatNumber(aid.swr)} : 1   $verdict", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
    aid.impedances?.let { (a, b) ->
        WorkedLine("${formatNumber(maxOf(a, b))} Ω / ${formatNumber(minOf(a, b))} Ω", "${formatNumber(aid.swr)} : 1")
    }
}

@Composable
internal fun FactView(aid: FactAid) {
    val c = HamTheme.colors
    val type = MaterialTheme.typography
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).background(c.accentSoft).padding(Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(aid.label, style = type.labelMedium, color = c.textSecondary, textAlign = TextAlign.Center)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(aid.value, style = type.displayMedium, color = c.accent)
            aid.unit?.let {
                Text(it, style = type.titleMedium, color = c.textSecondary, modifier = Modifier.padding(start = Space.sm, bottom = 6.dp))
            }
        }
    }
}

@Composable
internal fun CallSignView(aid: CallSignAid) {
    val c = HamTheme.colors
    val type = MaterialTheme.typography
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.Top) {
        CallPart(aid.prefix, "prefix", c.viz.slot1)
        CallPart(aid.digit, "region", c.viz.slot2)
        CallPart(aid.suffix, "suffix", c.viz.slot3)
    }
    Text(
        "${aid.format} format: ${aid.prefix.length} letter prefix, ${aid.suffix.length} letter suffix",
        style = type.labelMedium, color = c.textPrimary, modifier = Modifier.padding(top = Space.xs),
    )
}

@Composable
private fun CallPart(text: String, caption: String, color: Color) {
    val c = HamTheme.colors
    val shape = RoundedCornerShape(Radius.sm)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.clip(shape).background(color.copy(alpha = 0.14f)).border(1.5.dp, color, shape).padding(horizontal = Space.md, vertical = Space.sm),
        ) {
            Text(text, style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
        }
        Text(caption, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhoneticView(aid: PhoneticAid) {
    val c = HamTheme.colors
    val type = MaterialTheme.typography
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        aid.words.forEach { (ch, word) ->
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).background(c.viz.track).padding(horizontal = Space.sm, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$ch", style = type.titleMedium, color = c.accent)
                Text(word, style = type.bodyMedium, color = c.textPrimary, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CodesView(aid: CodesAid) {
    val c = HamTheme.colors
    val type = MaterialTheme.typography
    val meaning = aid.set.codes.first { it.first == aid.highlight }.second
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).background(c.accentSoft).padding(Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(aid.highlight, style = type.headlineMedium, color = c.accent, modifier = Modifier.widthIn(min = 64.dp))
        Text(meaning, style = type.titleSmall, color = c.textPrimary, modifier = Modifier.padding(start = Space.md))
    }
    if (aid.set == CodeSet.RST) {
        aid.set.codes.filter { it.first != aid.highlight }.forEach { (code, text) ->
            Text("$code  $text", style = type.bodySmall, color = c.textSecondary)
        }
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        aid.set.codes.forEach { (code, _) ->
            val hot = code == aid.highlight
            Text(
                code,
                style = type.labelMedium,
                color = if (hot) c.onAccent else c.textSecondary,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (hot) c.accent else c.viz.track)
                    .padding(horizontal = Space.sm, vertical = 4.dp),
            )
        }
    }
}

