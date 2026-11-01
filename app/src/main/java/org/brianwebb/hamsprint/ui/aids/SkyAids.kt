package org.brianwebb.hamsprint.ui.aids

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import org.brianwebb.hamsprint.domain.AntennaAid
import org.brianwebb.hamsprint.domain.AntennaPart
import org.brianwebb.hamsprint.domain.AntennaType
import org.brianwebb.hamsprint.domain.IonosphereAid
import org.brianwebb.hamsprint.domain.Layer
import org.brianwebb.hamsprint.ui.theme.HamTheme
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

private val SKY_STOPS_KM = floatArrayOf(0f, 60f, 90f, 150f, 220f, 440f)
private val SKY_STOPS_AT = floatArrayOf(0f, 0.14f, 0.32f, 0.52f, 0.7f, 1f)

@Composable
internal fun IonosphereView(aid: IonosphereAid) {
    val c = HamTheme.colors
    val measurer = rememberTextMeasurer()
    val type = MaterialTheme.typography
    val small = type.labelMedium.copy(color = c.textSecondary)
    val hot = type.labelMedium.copy(color = c.accent, fontWeight = FontWeight.Bold)
    // At night the F layers merge, so a question about F2 after dark points at the F layer.
    val target = if (aid.night && aid.layer == Layer.F2) Layer.F else aid.layer
    val present = aid.layers
    val faded = if (aid.night) listOf(Layer.D, Layer.E, Layer.F1) else emptyList()

    Canvas(Modifier.fillMaxWidth().height(190.dp).clipToBounds()) {
        val groundY = size.height - 16.dp.toPx()
        val r = size.width * 4f
        val center = Offset(size.width / 2, groundY + r)
        val sky = groundY - 12.dp.toPx()
        // Piecewise height scale: each layer gets a readable band instead of true proportions.
        fun alt(km: Float): Float {
            val i = SKY_STOPS_KM.indexOfLast { it <= km }.coerceIn(0, SKY_STOPS_KM.size - 2)
            val t = (km - SKY_STOPS_KM[i]) / (SKY_STOPS_KM[i + 1] - SKY_STOPS_KM[i])
            return (SKY_STOPS_AT[i] + t * (SKY_STOPS_AT[i + 1] - SKY_STOPS_AT[i])) * sky
        }
        fun halfSweepDeg(radius: Float) = Math.toDegrees(asin((size.width * 0.62f / radius).coerceAtMost(1f).toDouble())).toFloat()
        fun pointAt(radius: Float, deg: Float): Offset {
            val a = Math.toRadians((deg - 90).toDouble())
            return Offset(center.x + radius * cos(a).toFloat(), center.y + radius * sin(a).toFloat())
        }

        // Earth
        drawCircle(c.viz.slot3.copy(alpha = 0.22f), r, center)
        drawCircle(c.viz.slot3, r, center, style = Stroke(2.dp.toPx()))

        (present + faded).distinct().forEach { layer ->
            val r1 = r + alt(layer.lowKm.toFloat())
            val r2 = r + alt(layer.highKm.toFloat())
            val mid = (r1 + r2) / 2
            val half = halfSweepDeg(mid)
            val isTarget = layer == target
            val gone = layer in faded
            val color = when {
                isTarget -> c.accent.copy(alpha = if (gone) 0.5f else 0.32f)
                else -> c.textTertiary.copy(alpha = if (gone) 0.25f else 0.16f)
            }
            drawArc(
                color, 270 - half, half * 2, useCenter = false,
                topLeft = Offset(center.x - mid, center.y - mid), size = Size(mid * 2, mid * 2),
                style = Stroke(
                    width = if (gone) 2.dp.toPx() else r2 - r1,
                    pathEffect = if (gone) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())) else null,
                ),
            )
            if (!gone) {
                val edgeHalf = halfSweepDeg(r1)
                drawArc(
                    c.surface, 270 - edgeHalf, edgeHalf * 2, useCenter = false,
                    topLeft = Offset(center.x - r1, center.y - r1), size = Size(r1 * 2, r1 * 2), style = Stroke(1.5.dp.toPx()),
                )
            }
            val y = center.y - mid
            val name = layer.label.removeSuffix(" layer") + if (gone) "  gone" else ""
            val style = if (isTarget) hot else small
            val layout = measurer.measure(name, style)
            label(measurer, name, Offset(8.dp.toPx(), y - layout.size.height / 2f), style, style.color)
            val km = "${layer.lowKm} to ${layer.highKm} km"
            val kmLayout = measurer.measure(km, small)
            if (!gone) label(measurer, km, Offset(size.width - kmLayout.size.width - 8.dp.toPx(), y - kmLayout.size.height / 2f), small, c.textTertiary)
        }

        // Signal path: absorbed in the D layer by day, otherwise refracted back by the target layer.
        val start = pointAt(r, -5f)
        if (target == Layer.D && !aid.night) {
            val stop = pointAt(r + alt((Layer.D.lowKm + Layer.D.highKm) / 2f), -2f)
            drawLine(c.viz.slot2, start, stop, 2.5.dp.toPx(), StrokeCap.Round)
            drawCircle(c.viz.slot2, 4.dp.toPx(), stop)
        } else {
            val reflect = (if (target in present) target else Layer.F).let { it.lowKm + (it.highKm - it.lowKm) * 0.4f }
            val apex = pointAt(r + alt(reflect), 0f)
            val end = pointAt(r, 5f)
            drawLine(c.viz.slot2, start, apex, 2.5.dp.toPx(), StrokeCap.Round)
            drawLine(c.viz.slot2, apex, end, 2.5.dp.toPx(), StrokeCap.Round)
            drawCircle(c.viz.slot2, 4.dp.toPx(), start)
            drawCircle(c.viz.slot2, 4.dp.toPx(), end)
        }
    }
    Text("${target.label}: ${target.role}", style = type.labelMedium, color = c.textPrimary)
}

@Composable
internal fun AntennaView(aid: AntennaAid) {
    val c = HamTheme.colors
    val measurer = rememberTextMeasurer()
    val type = MaterialTheme.typography
    val small = type.labelMedium.copy(color = c.textSecondary)
    val hotStyle = type.labelMedium.copy(color = c.accent, fontWeight = FontWeight.Bold)
    val wire = c.textPrimary
    fun partColor(part: AntennaPart) = if (aid.highlight == part) c.accent else wire

    Canvas(Modifier.fillMaxWidth().height(if (aid.type == AntennaType.YAGI) 150.dp else 132.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 3.dp.toPx()
        when (aid.type) {
            AntennaType.DIPOLE -> {
                val y = h * 0.42f
                val x0 = w * 0.08f
                val x1 = w * 0.92f
                // Broadside pattern: the dipole radiates best at right angles to the wire.
                drawOval(c.accent.copy(alpha = 0.10f), Offset(w / 2 - 30.dp.toPx(), y - 58.dp.toPx()), Size(60.dp.toPx(), 56.dp.toPx()))
                drawOval(c.accent.copy(alpha = 0.10f), Offset(w / 2 - 30.dp.toPx(), y + 2.dp.toPx()), Size(60.dp.toPx(), 56.dp.toPx()))
                drawLine(wire, Offset(x0, y), Offset(w / 2 - 5.dp.toPx(), y), stroke, StrokeCap.Round)
                drawLine(wire, Offset(w / 2 + 5.dp.toPx(), y), Offset(x1, y), stroke, StrokeCap.Round)
                drawCircle(c.viz.slot2, 5.dp.toPx(), Offset(w / 2, y))
                drawLine(c.viz.slot2, Offset(w / 2, y + 5.dp.toPx()), Offset(w / 2, h - 30.dp.toPx()), 2.dp.toPx())
                label(measurer, "feed", Offset(w / 2 + 6.dp.toPx(), h - 44.dp.toPx()), small, c.textTertiary)
                label(measurer, "¼ λ", Offset((x0 + w / 2) / 2, y - 12.dp.toPx()), small, small.color, center = true)
                label(measurer, "¼ λ", Offset((x1 + w / 2) / 2, y - 12.dp.toPx()), small, small.color, center = true)
                val by = h - 12.dp.toPx()
                drawLine(c.textSecondary, Offset(x0, by), Offset(x1, by), 1.5.dp.toPx())
                drawLine(c.textSecondary, Offset(x0, by - 5.dp.toPx()), Offset(x0, by + 5.dp.toPx()), 1.5.dp.toPx())
                drawLine(c.textSecondary, Offset(x1, by - 5.dp.toPx()), Offset(x1, by + 5.dp.toPx()), 1.5.dp.toPx())
                val text = "½ λ" + (aid.lengthFeet?.let { " ≈ ${formatNumber(it)} ft" } ?: "")
                val layout = measurer.measure(text, hotStyle)
                drawRect(c.surface, Offset(w / 2 - layout.size.width / 2f - 6.dp.toPx(), by - layout.size.height / 2f), Size(layout.size.width + 12.dp.toPx(), layout.size.height.toFloat()))
                label(measurer, text, Offset(w / 2, by), hotStyle, c.accent, center = true)
            }
            AntennaType.VERTICAL -> {
                val base = Offset(w * 0.42f, h * 0.72f)
                val top = Offset(base.x, 10.dp.toPx())
                drawLine(wire, base, top, stroke, StrokeCap.Round)
                val radial = partColor(AntennaPart.RADIALS)
                listOf(-1f, 1f).forEach { s ->
                    drawLine(radial, base, Offset(base.x + s * w * 0.28f, base.y + 22.dp.toPx()), 2.5.dp.toPx(), StrokeCap.Round)
                    drawLine(radial, base, Offset(base.x + s * w * 0.14f, base.y + 30.dp.toPx()), 2.5.dp.toPx(), StrokeCap.Round)
                }
                drawCircle(c.viz.slot2, 5.dp.toPx(), base)
                val text = "¼ λ" + (aid.lengthFeet?.let { " ≈ ${formatNumber(it)} ft (${formatNumber(it * 12)} in)" } ?: "")
                label(measurer, text, Offset(base.x + 12.dp.toPx(), (base.y + top.y) / 2 - 8.dp.toPx()), hotStyle, c.accent)
                label(
                    measurer, "radials, the ground plane", Offset(base.x + w * 0.1f, base.y + 30.dp.toPx()),
                    if (aid.highlight == AntennaPart.RADIALS) hotStyle else small, if (aid.highlight == AntennaPart.RADIALS) c.accent else c.textTertiary,
                )
            }
            AntennaType.YAGI -> {
                val boomY = h * 0.42f
                val xs = listOf(0.12f, 0.36f, 0.6f, 0.84f).map { it * w }
                val lengths = listOf(0.86f, 0.74f, 0.62f, 0.54f).map { it * h * 0.8f }
                val parts = listOf(AntennaPart.REFLECTOR, AntennaPart.DRIVEN, AntennaPart.DIRECTOR, AntennaPart.DIRECTOR)
                drawLine(c.textTertiary, Offset(xs.first(), boomY), Offset(xs.last(), boomY), 2.dp.toPx())
                xs.forEachIndexed { i, x ->
                    val color = partColor(parts[i])
                    drawLine(color, Offset(x, boomY - lengths[i] / 2), Offset(x, boomY + lengths[i] / 2), stroke, StrokeCap.Round)
                }
                drawCircle(c.viz.slot2, 5.dp.toPx(), Offset(xs[1], boomY))
                val names = listOf("Reflector", "Driven", "Director", "Director")
                xs.forEachIndexed { i, x ->
                    val hot = aid.highlight == parts[i]
                    label(measurer, names[i], Offset(x, h - 10.dp.toPx()), if (hot) hotStyle else small, if (hot) c.accent else c.textSecondary, center = true)
                }
                val ay = 10.dp.toPx()
                drawLine(c.accent, Offset(xs[2], ay), Offset(w - 4.dp.toPx(), ay), 2.dp.toPx(), StrokeCap.Round)
                drawLine(c.accent, Offset(w - 4.dp.toPx(), ay), Offset(w - 12.dp.toPx(), ay - 5.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                drawLine(c.accent, Offset(w - 4.dp.toPx(), ay), Offset(w - 12.dp.toPx(), ay + 5.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                val gain = measurer.measure("most gain", small)
                label(measurer, "most gain", Offset(xs[2] - gain.size.width - 8.dp.toPx(), ay - gain.size.height / 2f), small, c.textSecondary)
            }
            AntennaType.LOOP -> {
                val side = h * 0.7f
                val tl = Offset(w / 2 - side / 2, 8.dp.toPx())
                drawRect(wire, tl, Size(side, side), style = Stroke(stroke))
                drawCircle(c.viz.slot2, 5.dp.toPx(), Offset(w / 2, tl.y + side))
                label(measurer, "small loop: far under 1 λ around", Offset(w / 2, tl.y + side + 16.dp.toPx()), hotStyle, c.accent, center = true)
            }
        }
    }
    if (aid.type == AntennaType.DIPOLE) {
        Text("Strongest broadside, weakest off the ends.", style = type.bodySmall, color = c.textTertiary)
    }
}

