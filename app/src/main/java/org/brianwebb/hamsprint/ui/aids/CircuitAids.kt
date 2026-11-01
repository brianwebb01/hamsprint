package org.brianwebb.hamsprint.ui.aids

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.brianwebb.hamsprint.domain.CombineAid
import org.brianwebb.hamsprint.domain.ComponentAid
import org.brianwebb.hamsprint.domain.ComponentSymbol
import org.brianwebb.hamsprint.domain.FilterAid
import org.brianwebb.hamsprint.domain.FilterType
import org.brianwebb.hamsprint.domain.Gate
import org.brianwebb.hamsprint.domain.LogicAid
import org.brianwebb.hamsprint.domain.MeterAid
import org.brianwebb.hamsprint.domain.OhmsAid
import org.brianwebb.hamsprint.domain.PartKind
import org.brianwebb.hamsprint.domain.PowerAid
import org.brianwebb.hamsprint.domain.Quantity
import org.brianwebb.hamsprint.domain.ReactanceAid
import org.brianwebb.hamsprint.domain.ReactanceSolve
import org.brianwebb.hamsprint.domain.TimeConstantAid
import org.brianwebb.hamsprint.domain.TransformerAid
import org.brianwebb.hamsprint.ui.theme.HamTheme
import org.brianwebb.hamsprint.ui.theme.Radius
import org.brianwebb.hamsprint.ui.theme.Space

private fun Quantity.format(value: Double) = formatSi(value, unit)

/** A formula triangle: [top] over [left] and [right]. Covering the unknown shows the formula. */
@Composable
private fun FormulaTriangle(top: Quantity, left: Quantity, right: Quantity, highlight: Quantity, modifier: Modifier = Modifier) {
    val c = HamTheme.colors
    val measurer = rememberTextMeasurer()
    val letter = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
    Canvas(modifier.size(112.dp, 100.dp)) {
        val w = size.width
        val h = size.height
        val split = 0.55f
        val tri = Path().apply { moveTo(w / 2, 0f); lineTo(w, h); lineTo(0f, h); close() }
        drawPath(tri, c.viz.track)
        drawPath(tri, c.textTertiary, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
        drawLine(c.textTertiary, Offset(w / 2 * (1 - split), h * split), Offset(w / 2 * (1 + split), h * split), 2.dp.toPx())
        drawLine(c.textTertiary, Offset(w / 2, h * split), Offset(w / 2, h), 2.dp.toPx())
        listOf(top to Offset(w / 2, h * 0.38f), left to Offset(w * 0.34f, h * 0.79f), right to Offset(w * 0.66f, h * 0.79f)).forEach { (q, at) ->
            if (q == highlight) drawCircle(c.accent, 17.dp.toPx(), at)
            label(measurer, q.symbol, at, letter, if (q == highlight) c.onAccent else c.textPrimary, center = true)
        }
    }
}

@Composable
internal fun OhmsView(aid: OhmsAid) {
    val c = HamTheme.colors
    val type = MaterialTheme.typography
    val formula = when (aid.solve) {
        Quantity.E -> "E = I × R"
        Quantity.I -> "I = E / R"
        else -> "R = E / I"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        FormulaTriangle(Quantity.E, Quantity.I, Quantity.R, aid.solve)
        Column(Modifier.padding(start = Space.lg)) {
            FormulaLine(formula)
            Text("Cover the letter you want; the rest is the formula.", style = type.bodySmall, color = c.textSecondary)
            Text("E volts, I amps, R ohms", style = type.bodySmall, color = c.textTertiary)
        }
    }
    aid.result?.let { result ->
        val g = aid.given
        val inputs = when (aid.solve) {
            Quantity.E -> "${Quantity.I.format(g.getValue(Quantity.I))} × ${Quantity.R.format(g.getValue(Quantity.R))}"
            Quantity.I -> "${Quantity.E.format(g.getValue(Quantity.E))} / ${Quantity.R.format(g.getValue(Quantity.R))}"
            else -> "${Quantity.E.format(g.getValue(Quantity.E))} / ${Quantity.I.format(g.getValue(Quantity.I))}"
        }
        WorkedLine(inputs, aid.solve.format(result))
    }
}

@Composable
internal fun PowerView(aid: PowerAid) {
    val c = HamTheme.colors
    val type = MaterialTheme.typography
    val keys = aid.given.keys
    val (formula, inputs) = powerFormula(aid.solve, keys)
    if (aid.solve != Quantity.R && (keys.isEmpty() || Quantity.R !in keys)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FormulaTriangle(Quantity.P, Quantity.E, Quantity.I, aid.solve)
            Column(Modifier.padding(start = Space.lg)) {
                FormulaLine(formula)
                Text("P watts, E volts, I amps", style = type.bodySmall, color = c.textTertiary)
            }
        }
    } else {
        FormulaLine(formula)
        Text("P = E × I = I² × R = E² / R", style = type.bodySmall, color = c.textSecondary)
    }
    val solved = aid.solved ?: return
    val text = inputs.fold(formula.substringAfter("= ")) { acc, q -> acc.replace(q.symbol, q.format(solved.getValue(q))) }
    WorkedLine(text, aid.solve.format(solved.getValue(aid.solve)))
}

/** The formula for [solve] that uses exactly the [given] quantities, and which quantities it reads. */
private fun powerFormula(solve: Quantity, given: Set<Quantity>): Pair<String, List<Quantity>> {
    val P = Quantity.P; val E = Quantity.E; val I = Quantity.I; val R = Quantity.R
    return when (solve) {
        Quantity.P -> when {
            given == setOf(I, R) -> "P = I² × R" to listOf(I, R)
            given == setOf(E, R) -> "P = E² / R" to listOf(E, R)
            else -> "P = E × I" to listOf(E, I)
        }
        Quantity.E -> when {
            given == setOf(P, R) -> "E = √(P × R)" to listOf(P, R)
            given == setOf(I, R) -> "E = I × R" to listOf(I, R)
            else -> "E = P / I" to listOf(P, I)
        }
        Quantity.I -> when {
            given == setOf(P, R) -> "I = √(P / R)" to listOf(P, R)
            given == setOf(E, R) -> "I = E / R" to listOf(E, R)
            else -> "I = P / E" to listOf(P, E)
        }
        Quantity.R -> when {
            given == setOf(P, I) -> "R = P / I²" to listOf(P, I)
            given == setOf(P, E) -> "R = E² / P" to listOf(P, E)
            else -> "R = E / I" to listOf(E, I)
        }
    }
}

private fun PartKind.symbol() = when (this) {
    PartKind.RESISTOR -> ComponentSymbol.RESISTOR
    PartKind.CAPACITOR -> ComponentSymbol.CAPACITOR
    PartKind.INDUCTOR -> ComponentSymbol.INDUCTOR
}

@Composable
internal fun CombineView(aid: CombineAid) {
    val c = HamTheme.colors
    val measurer = rememberTextMeasurer()
    val small = MaterialTheme.typography.labelMedium.copy(color = c.textSecondary)
    val n = aid.values.size
    Canvas(Modifier.fillMaxWidth().height(if (aid.parallel) (n * 34 + 8).dp else 72.dp)) {
        val w = size.width
        val wire = c.textPrimary
        val sw = 2.5.dp.toPx()
        if (!aid.parallel) {
            val y = size.height * 0.4f
            val seg = w / n
            aid.values.forEachIndexed { i, v ->
                val rect = Rect(i * seg, y - 14.dp.toPx(), (i + 1) * seg, y + 14.dp.toPx())
                drawSymbol(aid.part.symbol(), rect, wire)
                label(measurer, formatSi(v, aid.part.unit), Offset(rect.center.x, size.height - 12.dp.toPx()), small, small.color, center = true)
            }
        } else {
            val x0 = w * 0.12f
            val x1 = w * 0.62f
            val rowH = 34.dp.toPx()
            val top = rowH / 2 + 4.dp.toPx()
            drawLine(wire, Offset(x0, top), Offset(x0, top + (n - 1) * rowH), sw, StrokeCap.Round)
            drawLine(wire, Offset(x1, top), Offset(x1, top + (n - 1) * rowH), sw, StrokeCap.Round)
            aid.values.forEachIndexed { i, v ->
                val y = top + i * rowH
                drawSymbol(aid.part.symbol(), Rect(x0, y - 12.dp.toPx(), x1, y + 12.dp.toPx()), wire)
                label(measurer, formatSi(v, aid.part.unit), Offset(x1 + 12.dp.toPx(), y - 8.dp.toPx()), small, small.color)
            }
        }
    }
    val adds = (aid.part == PartKind.CAPACITOR) == aid.parallel
    val letter = when (aid.part) { PartKind.RESISTOR -> "R"; PartKind.CAPACITOR -> "C"; PartKind.INDUCTOR -> "L" }
    Text(
        if (adds) "In ${if (aid.parallel) "parallel" else "series"} they add: $letter = ${letter}1 + ${letter}2 + ..."
        else "In ${if (aid.parallel) "parallel" else "series"} they combine like 1/$letter = 1/${letter}1 + 1/${letter}2 + ...",
        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
    )
    WorkedLine("Total", formatSi(aid.total, aid.part.unit))
}

@Composable
internal fun ComponentView(aid: ComponentAid) {
    val c = HamTheme.colors
    val type = MaterialTheme.typography
    val measurer = rememberTextMeasurer()
    val small = type.labelMedium.copy(color = c.textTertiary)
    val s = aid.symbol
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(
            Modifier.size(128.dp, 96.dp).clip(RoundedCornerShape(Radius.sm)).background(c.viz.track),
        ) {
            val r = Rect(14.dp.toPx(), 18.dp.toPx(), size.width - 14.dp.toPx(), size.height - 18.dp.toPx())
            drawSymbol(s, r, c.textPrimary)
            val terminals = when (s) {
                ComponentSymbol.NPN, ComponentSymbol.PNP -> listOf("B" to Offset(r.left + 4.dp.toPx(), r.center.y - 16.dp.toPx()), "C" to Offset(r.right - 12.dp.toPx(), r.top - 14.dp.toPx()), "E" to Offset(r.right - 12.dp.toPx(), r.bottom))
                ComponentSymbol.JFET, ComponentSymbol.MOSFET -> listOf("G" to Offset(r.left + 4.dp.toPx(), r.center.y - 18.dp.toPx()), "D" to Offset(r.right - 10.dp.toPx(), r.top - 14.dp.toPx()), "S" to Offset(r.right - 10.dp.toPx(), r.bottom))
                ComponentSymbol.DIODE, ComponentSymbol.ZENER, ComponentSymbol.LED -> listOf("anode" to Offset(r.left, r.bottom - 2.dp.toPx()), "cathode" to Offset(r.right - 40.dp.toPx(), r.bottom - 2.dp.toPx()))
                ComponentSymbol.BATTERY -> listOf("+" to Offset(r.center.x - 30.dp.toPx(), r.top - 6.dp.toPx()))
                else -> emptyList()
            }
            terminals.forEach { (t, at) -> label(measurer, t, at, small, small.color) }
        }
        Column(Modifier.padding(start = Space.lg), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(s.label, style = type.titleMedium, color = c.textPrimary)
            Text(s.role, style = type.bodySmall, color = c.textSecondary)
            s.unit?.let { Text("Measured in ${it}s", style = type.labelMedium, color = c.accent) }
        }
    }
}

@Composable
internal fun MeterView(aid: MeterAid) {
    val c = HamTheme.colors
    val measurer = rememberTextMeasurer()
    val type = MaterialTheme.typography
    val letter = type.titleMedium.copy(fontWeight = FontWeight.Bold)
    val small = type.labelMedium.copy(color = c.textSecondary)
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val wire = c.textPrimary
        val sw = 2.5.dp.toPx()
        val l = size.width * 0.14f
        val rgt = size.width * 0.66f
        val t = 18.dp.toPx()
        val b = size.height - 18.dp.toPx()
        // Loop: battery cell on the left, load resistor on the right.
        drawLine(wire, Offset(l, t), Offset(l, (t + b) / 2 - 8.dp.toPx()), sw)
        drawLine(wire, Offset(l - 12.dp.toPx(), (t + b) / 2 - 6.dp.toPx()), Offset(l + 12.dp.toPx(), (t + b) / 2 - 6.dp.toPx()), sw)
        drawLine(wire, Offset(l - 6.dp.toPx(), (t + b) / 2 + 2.dp.toPx()), Offset(l + 6.dp.toPx(), (t + b) / 2 + 2.dp.toPx()), sw * 1.6f)
        drawLine(wire, Offset(l, (t + b) / 2 + 4.dp.toPx()), Offset(l, b), sw)
        drawLine(wire, Offset(l, b), Offset(rgt, b), sw)
        drawLine(wire, Offset(rgt, b), Offset(rgt, (t + b) / 2 + 22.dp.toPx()), sw)
        val load = Rect(rgt - 12.dp.toPx(), (t + b) / 2 - 22.dp.toPx(), rgt + 12.dp.toPx(), (t + b) / 2 + 22.dp.toPx())
        val zig = Path().apply {
            moveTo(rgt, load.bottom)
            for (i in 0 until 6) lineTo(rgt + if (i % 2 == 0) 9.dp.toPx() else -9.dp.toPx(), load.bottom - (i + 0.5f) * load.height / 6)
            lineTo(rgt, load.top)
        }
        drawPath(zig, wire, style = Stroke(sw, join = StrokeJoin.Round))
        drawLine(wire, Offset(rgt, load.top), Offset(rgt, t), sw)
        val meterR = 15.dp.toPx()
        if (aid.ammeter) {
            val mc = Offset((l + rgt) / 2, t)
            drawLine(wire, Offset(l, t), Offset(mc.x - meterR, t), sw)
            drawLine(wire, Offset(mc.x + meterR, t), Offset(rgt, t), sw)
            drawCircle(c.accent, meterR, mc)
            label(measurer, "A", mc, letter, c.onAccent, center = true)
        } else {
            drawLine(wire, Offset(l, t), Offset(rgt, t), sw)
            val vx = size.width * 0.88f
            val mc = Offset(vx, (t + b) / 2)
            drawLine(c.accent, Offset(rgt, t + 10.dp.toPx()), Offset(vx, t + 10.dp.toPx()), sw)
            drawLine(c.accent, Offset(vx, t + 10.dp.toPx()), Offset(vx, mc.y - meterR), sw)
            drawLine(c.accent, Offset(rgt, b - 10.dp.toPx()), Offset(vx, b - 10.dp.toPx()), sw)
            drawLine(c.accent, Offset(vx, b - 10.dp.toPx()), Offset(vx, mc.y + meterR), sw)
            drawCircle(c.accent, 3.dp.toPx(), Offset(rgt, t + 10.dp.toPx()))
            drawCircle(c.accent, 3.dp.toPx(), Offset(rgt, b - 10.dp.toPx()))
            drawCircle(c.accent, meterR, mc)
            label(measurer, "V", mc, letter, c.onAccent, center = true)
        }
    }
    Text(
        if (aid.ammeter) "Ammeter: break the circuit and put it in line (series)." else "Voltmeter: connect it across the part (parallel).",
        style = type.labelMedium, color = c.textPrimary,
    )
}

@Composable
internal fun FilterView(aid: FilterAid) {
    val c = HamTheme.colors
    val measurer = rememberTextMeasurer()
    val small = MaterialTheme.typography.labelMedium.copy(color = c.textTertiary)
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val left = 4.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        val top = 8.dp.toPx()
        val w = size.width - left
        val area = Path()
        val curve = Path()
        val steps = 100
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val x = left + t * w
            val y = bottom - aid.response(t.toDouble()).toFloat() * (bottom - top)
            if (i == 0) { curve.moveTo(x, y); area.moveTo(x, bottom); area.lineTo(x, y) } else { curve.lineTo(x, y); area.lineTo(x, y) }
        }
        area.lineTo(left + w, bottom); area.close()
        drawPath(area, c.accent.copy(alpha = 0.16f))
        drawPath(curve, c.accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        drawLine(c.separator, Offset(left, bottom), Offset(size.width, bottom), 1.dp.toPx())
        label(measurer, "low", Offset(left, bottom + 4.dp.toPx()), small, small.color)
        val hi = measurer.measure("high frequency", small)
        label(measurer, "high frequency", Offset(size.width - hi.size.width, bottom + 4.dp.toPx()), small, small.color)
    }
    Text(
        when (aid.type) {
            FilterType.LOWPASS -> "Passes frequencies below the cutoff, blocks those above."
            FilterType.HIGHPASS -> "Passes frequencies above the cutoff, blocks those below."
            FilterType.BANDPASS -> "Passes one band of frequencies, blocks both sides."
            FilterType.NOTCH -> "Blocks one narrow band, passes everything else."
        },
        style = MaterialTheme.typography.labelMedium, color = c.textPrimary,
    )
}

@Composable
internal fun LogicView(aid: LogicAid) {
    val c = HamTheme.colors
    val type = MaterialTheme.typography
    val gate = aid.gate
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(120.dp, 80.dp)) {
            val sw = 2.5.dp.toPx()
            val st = Stroke(sw, join = StrokeJoin.Round, cap = StrokeCap.Round)
            val ink = c.textPrimary
            val l = 22.dp.toPx()
            val r = size.width - 22.dp.toPx()
            val t = 10.dp.toPx()
            val b = size.height - 10.dp.toPx()
            val cy = size.height / 2
            val inverted = gate in setOf(Gate.NAND, Gate.NOR, Gate.XNOR, Gate.NOT)
            val bubble = 5.dp.toPx()
            val outX = if (inverted) r - bubble * 2 else r
            val body = Path()
            when (gate) {
                Gate.AND, Gate.NAND -> {
                    val mid = (l + outX) / 2
                    body.moveTo(l, t); body.lineTo(mid, t)
                    body.arcTo(Rect(mid - (outX - mid), t, outX, b), -90f, 180f, false)
                    body.lineTo(l, b); body.close()
                }
                Gate.NOT -> { body.moveTo(l, t); body.lineTo(outX, cy); body.lineTo(l, b); body.close() }
                else -> {
                    body.moveTo(l, t)
                    body.quadraticTo((l + outX) / 2, t, outX, cy)
                    body.quadraticTo((l + outX) / 2, b, l, b)
                    body.quadraticTo(l + 14.dp.toPx(), cy, l, t)
                    if (gate == Gate.XOR || gate == Gate.XNOR) {
                        val back = Path().apply { moveTo(l - 7.dp.toPx(), t); quadraticTo(l + 7.dp.toPx(), cy, l - 7.dp.toPx(), b) }
                        drawPath(back, ink, style = st)
                    }
                }
            }
            drawPath(body, c.accent.copy(alpha = 0.12f))
            drawPath(body, ink, style = st)
            if (inverted) drawCircle(ink, bubble, Offset(outX + bubble, cy), style = st)
            drawLine(ink, Offset(r, cy), Offset(size.width, cy), sw)
            val ins = if (gate == Gate.NOT) listOf(cy) else listOf(t + (b - t) * 0.28f, t + (b - t) * 0.72f)
            ins.forEach { y -> drawLine(ink, Offset(0f, y), Offset(l + 4.dp.toPx(), y), sw) }
        }
        Column(Modifier.padding(start = Space.lg)) {
            val inputs = if (gate == Gate.NOT) listOf("A") else listOf("A", "B")
            Row { (inputs + "Out").forEach { TableCell(it, header = true) } }
            aid.truthTable.forEach { (ins, out) ->
                Row {
                    ins.forEach { TableCell(if (it) "1" else "0") }
                    TableCell(if (out) "1" else "0", hot = out)
                }
            }
        }
    }
}

@Composable
private fun TableCell(text: String, header: Boolean = false, hot: Boolean = false) {
    val c = HamTheme.colors
    Box(
        Modifier.width(36.dp).padding(1.dp).clip(RoundedCornerShape(4.dp))
            .background(if (hot) c.accent else if (header) Color.Transparent else c.viz.track)
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            color = if (hot) c.onAccent else if (header) c.textTertiary else c.textPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun TransformerView(aid: TransformerAid) {
    val c = HamTheme.colors
    val measurer = rememberTextMeasurer()
    val small = MaterialTheme.typography.labelMedium.copy(color = c.textSecondary)
    Canvas(Modifier.fillMaxWidth().height(84.dp)) {
        val r = Rect(size.width / 2 - 60.dp.toPx(), 10.dp.toPx(), size.width / 2 + 60.dp.toPx(), size.height - 10.dp.toPx())
        drawSymbol(ComponentSymbol.TRANSFORMER, r, c.textPrimary)
        fun turns(n: Double) = "${formatNumber(n)} ${if (n == 1.0) "turn" else "turns"}"
        listOf("Primary" to aid.primaryTurns, "Secondary" to aid.secondaryTurns).forEachIndexed { i, (name, n) ->
            val text = "$name\n${turns(n)}"
            val layout = measurer.measure(text, small)
            val x = if (i == 0) r.left - 16.dp.toPx() - layout.size.width else r.right + 16.dp.toPx()
            label(measurer, text, Offset(x, r.center.y - layout.size.height / 2f), small, small.color)
        }
    }
    FormulaLine("Vs = Vp × Ns / Np")
    aid.secondaryVolts?.let {
        WorkedLine("${formatNumber(aid.primaryVolts!!)} V × ${formatNumber(aid.secondaryTurns)} / ${formatNumber(aid.primaryTurns)}", "${formatNumber(it)} V")
    } ?: WorkedLine("Turns ratio", "${formatNumber(aid.primaryTurns)} : ${formatNumber(aid.secondaryTurns)}")
}

@Composable
internal fun TimeConstantView(aid: TimeConstantAid) {
    val c = HamTheme.colors
    val measurer = rememberTextMeasurer()
    val type = MaterialTheme.typography
    val small = type.labelMedium.copy(color = c.textTertiary)
    val hot = type.labelMedium.copy(color = c.accent, fontWeight = FontWeight.Bold)
    val focus = aid.tau ?: 1
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val bottom = size.height - 20.dp.toPx()
        val top = 10.dp.toPx()
        val w = size.width
        fun x(n: Double) = (n / 5.0 * w).toFloat()
        fun y(level: Double) = bottom - level.toFloat() * (bottom - top)
        val path = Path()
        for (i in 0..100) {
            val n = i / 20.0
            if (i == 0) path.moveTo(x(n), y(aid.level(n))) else path.lineTo(x(n), y(aid.level(n)))
        }
        for (n in 1..5) {
            val px = x(n.toDouble())
            dashed(c.separator, Offset(px, top), Offset(px, bottom))
            label(measurer, "${n}τ", Offset(px - 10.dp.toPx(), bottom + 4.dp.toPx()), if (n == focus) hot else small, if (n == focus) c.accent else c.textTertiary)
        }
        drawLine(c.separator, Offset(0f, bottom), Offset(w, bottom), 1.dp.toPx())
        drawPath(path, c.accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        val level = aid.level(focus.toDouble())
        val p = Offset(x(focus.toDouble()), y(level))
        drawCircle(c.accent, 5.dp.toPx(), p)
        label(measurer, "${formatNumber(level * 100, 3)}%", Offset(p.x + 10.dp.toPx(), p.y + (if (aid.discharge) -22 else 4).dp.toPx()), hot, c.accent)
    }
    Text(
        if (aid.discharge) "Each time constant drops the voltage to 36.8% of what was left."
        else "Each time constant charges 63.2% of the remaining way.",
        style = type.bodySmall, color = c.textSecondary,
    )
    aid.seconds?.let { WorkedLine("τ = R × C = ${formatSi(aid.ohms!!, "Ω")} × ${formatSi(aid.farads!!, "F")}", formatSi(it, "s")) }
}

@Composable
internal fun ReactanceView(aid: ReactanceAid) {
    val c = HamTheme.colors
    val measurer = rememberTextMeasurer()
    val type = MaterialTheme.typography
    val small = type.labelMedium
    FormulaLine(
        when (aid.solve) {
            ReactanceSolve.XL -> "XL = 2πfL"
            ReactanceSolve.XC -> "XC = 1 / (2πfC)"
            ReactanceSolve.F0 -> "f = 1 / (2π√(LC))"
        },
    )
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        val bottom = size.height - 16.dp.toPx()
        val w = size.width
        val xl = Path().apply { moveTo(0f, bottom); lineTo(w, 6.dp.toPx()) }
        val xc = Path()
        // XC = k / f, drawn from where it enters the plot.
        for (i in 0..100) {
            val t = 0.08f + 0.92f * i / 100f
            val y = bottom - (bottom - 6.dp.toPx()) * (0.08f / t)
            if (i == 0) xc.moveTo(t * w, y) else xc.lineTo(t * w, y)
        }
        val xlHot = aid.solve != ReactanceSolve.XC
        val xcHot = aid.solve != ReactanceSolve.XL
        drawPath(xl, if (xlHot) c.viz.slot1 else c.textTertiary.copy(alpha = 0.4f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        drawPath(xc, if (xcHot) c.viz.slot2 else c.textTertiary.copy(alpha = 0.4f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        drawLine(c.separator, Offset(0f, bottom), Offset(w, bottom), 1.dp.toPx())
        label(measurer, "XL rises", Offset(w * 0.72f, bottom - (bottom - 6.dp.toPx()) * 0.72f + 6.dp.toPx()), small, c.textSecondary)
        label(measurer, "XC falls", Offset(w * 0.12f + 8.dp.toPx(), 6.dp.toPx()), small, c.textSecondary)
        if (aid.solve == ReactanceSolve.F0) {
            // XL = XC where the curves cross: resonance.
            val t = kotlin.math.sqrt(0.08f * 1f)
            val cross = Offset(t * w, bottom - (bottom - 6.dp.toPx()) * t)
            drawCircle(c.accent, 5.dp.toPx(), cross)
            label(measurer, "resonance, XL = XC", Offset(cross.x + 8.dp.toPx(), cross.y - 24.dp.toPx()), small, c.accent)
        }
        label(measurer, "frequency", Offset(w - 64.dp.toPx(), bottom + 2.dp.toPx()), small, c.textTertiary)
    }
    aid.result?.let { result ->
        val inputs = when (aid.solve) {
            ReactanceSolve.XL -> "2π × ${formatSi(aid.hertz!!, "Hz")} × ${formatSi(aid.henries!!, "H")}"
            ReactanceSolve.XC -> "1 / (2π × ${formatSi(aid.hertz!!, "Hz")} × ${formatSi(aid.farads!!, "F")})"
            ReactanceSolve.F0 -> "${formatSi(aid.henries!!, "H")}, ${formatSi(aid.farads!!, "F")}"
        }
        WorkedLine(inputs, formatSi(result, if (aid.solve == ReactanceSolve.F0) "Hz" else "Ω"))
    }
}

