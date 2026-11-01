package org.brianwebb.hamsprint.ui.aids

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import org.brianwebb.hamsprint.domain.ComponentSymbol
import kotlin.math.min

/**
 * Schematic symbols drawn left to right inside [r], with leads running to its
 * left and right edges at mid height. One stroke width and round caps keep
 * every symbol in the same visual family.
 */
internal fun DrawScope.drawSymbol(symbol: ComponentSymbol, r: Rect, color: Color) {
    val sw = 2.5.dp.toPx()
    val stroke = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val cy = r.center.y
    val cx = r.center.x
    val u = min(r.height / 2, r.width / 5) // symbol half size
    fun line(a: Offset, b: Offset, c: Color = color) = drawLine(c, a, b, sw, StrokeCap.Round)
    fun leads(gapHalf: Float) {
        line(Offset(r.left, cy), Offset(cx - gapHalf, cy))
        line(Offset(cx + gapHalf, cy), Offset(r.right, cy))
    }
    fun arrowHead(tip: Offset, from: Offset, c: Color = color) {
        val dx = tip.x - from.x
        val dy = tip.y - from.y
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        val ux = dx / len
        val uy = dy / len
        val s = 7.dp.toPx()
        val p = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(tip.x - ux * s - uy * s * 0.6f, tip.y - uy * s + ux * s * 0.6f)
            lineTo(tip.x - ux * s + uy * s * 0.6f, tip.y - uy * s - ux * s * 0.6f)
            close()
        }
        drawPath(p, c)
    }
    fun zigzag(halfW: Float) {
        val p = Path().apply {
            moveTo(cx - halfW, cy)
            val n = 6
            for (i in 0 until n) {
                val x = cx - halfW + (i + 0.5f) * (2 * halfW / n)
                lineTo(x, cy + if (i % 2 == 0) -u * 0.5f else u * 0.5f)
            }
            lineTo(cx + halfW, cy)
        }
        drawPath(p, color, style = stroke)
    }
    fun coil(halfW: Float) {
        val n = 4
        val step = 2 * halfW / n
        for (i in 0 until n) {
            val left = cx - halfW + i * step
            drawArc(color, 180f, 180f, false, Offset(left, cy - step / 2), Size(step, step), style = stroke)
        }
    }
    fun diode(bar: (top: Offset, bottom: Offset) -> Unit) {
        val h = u * 0.8f
        leads(h * 0.7f)
        val p = Path().apply {
            moveTo(cx - h * 0.7f, cy - h)
            lineTo(cx - h * 0.7f, cy + h)
            lineTo(cx + h * 0.7f, cy)
            close()
        }
        drawPath(p, color, style = stroke)
        bar(Offset(cx + h * 0.7f, cy - h), Offset(cx + h * 0.7f, cy + h))
    }
    fun transistorBody(): Triple<Offset, Offset, Offset> {
        drawCircle(color, u * 1.1f, Offset(cx, cy), style = stroke)
        val barX = cx - u * 0.35f
        line(Offset(barX, cy - u * 0.6f), Offset(barX, cy + u * 0.6f))
        line(Offset(r.left, cy), Offset(barX, cy))
        val collector = Offset(cx + u * 0.5f, cy - u * 1.6f)
        val emitter = Offset(cx + u * 0.5f, cy + u * 1.6f)
        return Triple(Offset(barX, cy), collector, emitter)
    }

    when (symbol) {
        ComponentSymbol.RESISTOR -> { leads(u * 1.6f); zigzag(u * 1.6f) }
        ComponentSymbol.POTENTIOMETER -> {
            leads(u * 1.6f); zigzag(u * 1.6f)
            line(Offset(cx, r.top), Offset(cx, cy - u * 0.75f))
            arrowHead(Offset(cx, cy - u * 0.55f), Offset(cx, r.top))
        }
        ComponentSymbol.CAPACITOR, ComponentSymbol.VARIABLE_CAPACITOR -> {
            val g = 5.dp.toPx()
            leads(g)
            line(Offset(cx - g, cy - u * 0.8f), Offset(cx - g, cy + u * 0.8f))
            line(Offset(cx + g, cy - u * 0.8f), Offset(cx + g, cy + u * 0.8f))
            if (symbol == ComponentSymbol.VARIABLE_CAPACITOR) {
                val a = Offset(cx - u, cy + u)
                val b = Offset(cx + u, cy - u)
                line(a, b)
                arrowHead(b, a)
            }
        }
        ComponentSymbol.INDUCTOR -> { leads(u * 1.6f); coil(u * 1.6f) }
        ComponentSymbol.TRANSFORMER -> {
            val step = u * 0.5f
            listOf(-1f, 1f).forEach { side ->
                val x = cx + side * u * 0.55f
                for (i in 0 until 4) {
                    val top = cy - u + i * step
                    drawArc(color, if (side < 0) 270f else 90f, 180f, false, Offset(x - step / 2, top), Size(step, step), style = stroke)
                }
                val outer = cx + side * u * 1.6f
                line(Offset(outer, cy - u), Offset(x, cy - u))
                line(Offset(outer, cy + u), Offset(x, cy + u))
            }
            line(Offset(cx - 2.dp.toPx(), cy - u), Offset(cx - 2.dp.toPx(), cy + u))
            line(Offset(cx + 2.dp.toPx(), cy - u), Offset(cx + 2.dp.toPx(), cy + u))
        }
        ComponentSymbol.DIODE -> diode { a, b -> line(a, b) }
        ComponentSymbol.ZENER -> diode { a, b ->
            line(a, b)
            line(a, Offset(a.x - 5.dp.toPx(), a.y - 4.dp.toPx()))
            line(b, Offset(b.x + 5.dp.toPx(), b.y + 4.dp.toPx()))
        }
        ComponentSymbol.LED -> {
            diode { a, b -> line(a, b) }
            listOf(0f, 9.dp.toPx()).forEach { dx ->
                val from = Offset(cx + dx - 2.dp.toPx(), cy - u * 0.9f)
                val to = Offset(cx + dx + 10.dp.toPx(), cy - u * 1.6f)
                line(from, to)
                arrowHead(to, from)
            }
        }
        ComponentSymbol.NPN, ComponentSymbol.PNP -> {
            val (base, collector, emitter) = transistorBody()
            val c0 = Offset(base.x, cy - u * 0.3f)
            val e0 = Offset(base.x, cy + u * 0.3f)
            val cTurn = Offset(cx + u * 0.5f, cy - u * 0.9f)
            val eTurn = Offset(cx + u * 0.5f, cy + u * 0.9f)
            line(c0, cTurn); line(cTurn, collector)
            line(e0, eTurn); line(eTurn, emitter)
            if (symbol == ComponentSymbol.NPN) arrowHead(eTurn, e0) else arrowHead(e0, eTurn)
        }
        ComponentSymbol.JFET, ComponentSymbol.MOSFET -> {
            drawCircle(color, u * 1.1f, Offset(cx, cy), style = stroke)
            val ch = cx + u * 0.1f
            line(Offset(ch, cy - u * 0.65f), Offset(ch, cy + u * 0.65f))
            val gateX = if (symbol == ComponentSymbol.MOSFET) ch - 6.dp.toPx() else ch
            if (symbol == ComponentSymbol.MOSFET) line(Offset(gateX, cy - u * 0.5f), Offset(gateX, cy + u * 0.5f))
            line(Offset(r.left, cy + if (symbol == ComponentSymbol.MOSFET) 0f else u * 0.4f), Offset(gateX, cy + if (symbol == ComponentSymbol.MOSFET) 0f else u * 0.4f))
            if (symbol == ComponentSymbol.JFET) arrowHead(Offset(ch, cy + u * 0.4f), Offset(r.left, cy + u * 0.4f))
            val right = cx + u * 0.7f
            line(Offset(ch, cy - u * 0.5f), Offset(right, cy - u * 0.5f)); line(Offset(right, cy - u * 0.5f), Offset(right, cy - u * 1.6f))
            line(Offset(ch, cy + u * 0.5f), Offset(right, cy + u * 0.5f)); line(Offset(right, cy + u * 0.5f), Offset(right, cy + u * 1.6f))
        }
        ComponentSymbol.BATTERY -> {
            val g = 6.dp.toPx()
            leads(g * 3)
            listOf(-3, -1, 1, 3).forEachIndexed { i, k ->
                val x = cx + k * g / 1f
                val tall = i % 2 == 0
                drawLine(color, Offset(x, cy - if (tall) u else u * 0.5f), Offset(x, cy + if (tall) u else u * 0.5f), if (tall) sw else sw * 1.8f, StrokeCap.Round)
            }
        }
        ComponentSymbol.FUSE -> {
            leads(u * 1.4f)
            drawRoundRect(color, Offset(cx - u * 1.4f, cy - u * 0.4f), Size(u * 2.8f, u * 0.8f), style = stroke)
            line(Offset(cx - u * 1.4f, cy), Offset(cx + u * 1.4f, cy))
        }
        ComponentSymbol.SWITCH -> {
            val a = Offset(cx - u, cy)
            val b = Offset(cx + u, cy)
            line(Offset(r.left, cy), a); line(b, Offset(r.right, cy))
            line(a, Offset(cx + u * 0.8f, cy - u * 0.9f))
            drawCircle(color, 3.dp.toPx(), a); drawCircle(color, 3.dp.toPx(), b)
        }
        ComponentSymbol.RELAY -> {
            drawRect(color, Offset(cx - u * 1.8f, cy - u * 0.2f), Size(u * 1.2f, u * 1.2f), style = stroke)
            val a = Offset(cx + u * 0.3f, cy)
            val b = Offset(cx + u * 1.7f, cy)
            line(Offset(r.left, cy + u * 0.4f), Offset(cx - u * 1.8f, cy + u * 0.4f))
            line(a, Offset(cx + u * 1.5f, cy - u * 0.8f))
            line(b, Offset(r.right, cy))
            drawCircle(color, 3.dp.toPx(), a); drawCircle(color, 3.dp.toPx(), b)
            drawLine(color, Offset(cx - u * 0.6f, cy + u * 0.4f), Offset(cx + u * 0.9f, cy - u * 0.4f), sw * 0.6f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
        }
        ComponentSymbol.LAMP -> {
            leads(u)
            drawCircle(color, u, Offset(cx, cy), style = stroke)
            val d = u * 0.7f
            line(Offset(cx - d, cy - d), Offset(cx + d, cy + d)); line(Offset(cx - d, cy + d), Offset(cx + d, cy - d))
        }
        ComponentSymbol.ANTENNA -> {
            val bottom = Offset(cx, r.bottom)
            val top = Offset(cx, r.top + u * 0.2f)
            line(bottom, Offset(cx, cy - u * 0.2f))
            val p = Path().apply {
                moveTo(cx - u, top.y); lineTo(cx, cy - u * 0.2f); lineTo(cx + u, top.y)
            }
            drawPath(p, color, style = stroke)
            line(Offset(cx - u, top.y), Offset(cx + u, top.y))
        }
        ComponentSymbol.GROUND -> {
            line(Offset(cx, r.top), Offset(cx, cy))
            listOf(1f, 0.62f, 0.25f).forEachIndexed { i, f ->
                val y = cy + i * 7.dp.toPx()
                line(Offset(cx - u * f, y), Offset(cx + u * f, y))
            }
        }
        ComponentSymbol.CRYSTAL -> {
            val g = 9.dp.toPx()
            leads(g)
            line(Offset(cx - g, cy - u * 0.7f), Offset(cx - g, cy + u * 0.7f))
            line(Offset(cx + g, cy - u * 0.7f), Offset(cx + g, cy + u * 0.7f))
            drawRect(color, Offset(cx - g * 0.55f, cy - u * 0.9f), Size(g * 1.1f, u * 1.8f), style = stroke)
        }
        ComponentSymbol.SPEAKER -> {
            line(Offset(r.left, cy - u * 0.3f), Offset(cx - u * 0.6f, cy - u * 0.3f))
            line(Offset(r.left, cy + u * 0.3f), Offset(cx - u * 0.6f, cy + u * 0.3f))
            drawRect(color, Offset(cx - u * 0.6f, cy - u * 0.5f), Size(u * 0.6f, u), style = stroke)
            val p = Path().apply {
                moveTo(cx, cy - u * 0.5f); lineTo(cx + u, cy - u * 1.2f); lineTo(cx + u, cy + u * 1.2f); lineTo(cx, cy + u * 0.5f)
            }
            drawPath(p, color, style = stroke)
        }
    }
}
