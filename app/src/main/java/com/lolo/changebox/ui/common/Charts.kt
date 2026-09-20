package com.lolo.changebox.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lolo.changebox.domain.DisplayCurrency
import com.lolo.changebox.domain.MonthBucket
import com.lolo.changebox.domain.fmtMinor
import com.lolo.changebox.domain.fmtRate
import com.lolo.changebox.ui.theme.ChangeboxColors

// Gráficos sin librerías, ports de monthly-bars.tsx, rate-sparkline.tsx y
// rate-line-chart.tsx: barras mensuales, sparkline por par y gráfico lineal
// interactivo con crosshair + tooltip.

@Composable
fun MonthlyBars(series: List<MonthBucket>, currency: DisplayCurrency) {
    val ext = ChangeboxColors.extended
    val max = maxOf(1L, series.maxOfOrNull { maxOf(it.incomeMinor, it.expenseMinor) } ?: 1L)
    fun height(value: Long): Float =
        if (value <= 0) 0f else maxOf(0.04f, value.toFloat() / max)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(144.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            series.forEach { m ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.height(112.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            Modifier
                                .width(12.dp)
                                .fillMaxHeight(height(m.incomeMinor))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(ext.ok)
                        )
                        Box(
                            Modifier
                                .width(12.dp)
                                .fillMaxHeight(height(m.expenseMinor))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(MaterialTheme.colorScheme.error)
                        )
                    }
                    Text(
                        m.label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendDot(ext.ok, "Ingresos")
            LegendDot(MaterialTheme.colorScheme.error, "Gastos")
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Sparkline de tendencia reciente de una tasa (tarjetas de /tasas). */
@Composable
fun RateSparkline(values: List<Long>, modifier: Modifier = Modifier) {
    // Con menos de dos puntos no hay tendencia que dibujar.
    if (values.size < 2) return
    val lineColor = MaterialTheme.colorScheme.secondary
    val dotColor = MaterialTheme.colorScheme.primary
    val ringColor = MaterialTheme.colorScheme.surface

    Canvas(modifier = modifier.size(width = 96.dp, height = 30.dp)) {
        val pad = 5.dp.toPx()
        val min = values.min()
        val max = values.max()
        val span = (max - min).toFloat()
        val stepX = (size.width - pad * 2) / (values.size - 1)
        val points = values.mapIndexed { i, value ->
            val norm = if (span == 0f) 0.5f else (value - min) / span
            Offset(pad + i * stepX, pad + (1 - norm) * (size.height - pad * 2))
        }
        val path = Path().apply {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        }
        drawPath(
            path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        val last = points.last()
        drawCircle(ringColor, radius = 6.dp.toPx() / 2 + 2.dp.toPx() / 2, center = last)
        drawCircle(dotColor, radius = 4.dp.toPx(), center = last)
    }
}

data class RatePoint(val t: Long, val rateScaled: Long)

/** Paso "bonito" (1/2/5×10^n) para los ticks del eje Y, en unidades escaladas. */
private fun niceStep(raw: Double): Long {
    val magnitude = Math.pow(10.0, Math.floor(Math.log10(maxOf(raw, 1.0))))
    val norm = raw / magnitude
    val factor = if (norm <= 1) 1.0 else if (norm <= 2) 2.0 else if (norm <= 5) 5.0 else 10.0
    return maxOf(1.0, factor * magnitude).toLong()
}

/**
 * Gráfico lineal del histórico de una tasa: línea 2dp con lavado de área,
 * rejilla recesiva, etiqueta directa solo en el último punto y capa táctil
 * con crosshair + tooltip (port de rate-line-chart.tsx).
 */
@Composable
fun RateLineChart(points: List<RatePoint>, fromCode: String, toCode: String) {
    if (points.isEmpty()) return
    var hoverIndex by remember { mutableStateOf<Int?>(null) }
    var chartWidthPx by remember { mutableStateOf(0f) }

    val brand = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current

    val last = points.last()
    val active = hoverIndex?.let { points.getOrNull(it) }

    Box(Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .pointerInput(points) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull()
                            if (change == null) continue
                            when {
                                event.changes.all { it.changedToUpIgnoreConsumed() } ->
                                    hoverIndex = null
                                else -> {
                                    val px = change.position.x
                                    chartWidthPx = size.width.toFloat()
                                    val geometry = chartGeometry(
                                        points, size.width.toFloat(), size.height.toFloat(), density.density,
                                    )
                                    var nearest = 0
                                    var best = Float.MAX_VALUE
                                    geometry.coords.forEachIndexed { i, c ->
                                        val d = kotlin.math.abs(c.x - px)
                                        if (d < best) { best = d; nearest = i }
                                    }
                                    hoverIndex = nearest
                                }
                            }
                        }
                    }
                },
        ) {
            chartWidthPx = size.width
            val g = chartGeometry(points, size.width, size.height, density.density)

            // Rejilla recesiva por tick Y
            g.ticks.forEach { tick ->
                val y = g.yOf(tick)
                drawLine(
                    gridColor,
                    start = Offset(g.marginLeft, y),
                    end = Offset(size.width - g.marginRight, y),
                    strokeWidth = 1f,
                )
            }

            // Lavado de área
            if (g.coords.size > 1) {
                val area = Path().apply {
                    g.coords.forEachIndexed { i, c -> if (i == 0) moveTo(c.x, c.y) else lineTo(c.x, c.y) }
                    lineTo(g.coords.last().x, g.baselineY)
                    lineTo(g.coords.first().x, g.baselineY)
                    close()
                }
                drawPath(area, brand.copy(alpha = 0.1f))

                val line = Path().apply {
                    g.coords.forEachIndexed { i, c -> if (i == 0) moveTo(c.x, c.y) else lineTo(c.x, c.y) }
                }
                drawPath(
                    line,
                    brand,
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            // Crosshair + punto activo
            val activeIndex = hoverIndex
            if (activeIndex != null && activeIndex in g.coords.indices) {
                val c = g.coords[activeIndex]
                drawLine(
                    mutedColor,
                    start = Offset(c.x, g.marginTop),
                    end = Offset(c.x, g.baselineY),
                    strokeWidth = 1f,
                )
                drawCircle(surface, radius = 7.dp.toPx() / 2 + 1.dp.toPx(), center = c)
                drawCircle(brand, radius = 5.dp.toPx() / 2 + 1.dp.toPx(), center = c)
            } else {
                // Punto final con anillo de superficie
                val c = g.coords.last()
                drawCircle(surface, radius = 6.5.dp.toPx() / 2 + 1.dp.toPx(), center = c)
                drawCircle(brand, radius = 4.5.dp.toPx(), center = c)
            }
        }

        // Etiquetas HTML-like superpuestas: ticks Y, fechas X y tooltip
        val g = if (chartWidthPx > 0) {
            chartGeometry(
                points,
                chartWidthPx,
                with(density) { 220.dp.toPx() },
                density.density,
            )
        } else {
            null
        }
        if (g != null) {
            g.ticks.forEach { tick ->
                Text(
                    fmtRate(tick),
                    fontSize = 10.sp,
                    color = mutedColor,
                    modifier = Modifier.offset {
                        androidx.compose.ui.unit.IntOffset(
                            g.marginLeft.toInt(),
                            (g.yOf(tick) - with(density) { 16.dp.toPx() }).toInt(),
                        )
                    },
                )
            }
            Text(
                fmtDate(points.first().t),
                fontSize = 10.sp,
                color = mutedColor,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 2.dp),
            )
            if (points.size > 1) {
                Text(
                    fmtDate(last.t),
                    fontSize = 10.sp,
                    color = mutedColor,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 2.dp),
                )
            }
            if (hoverIndex == null) {
                Text(
                    fmtRate(last.rateScaled),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.offset {
                        val c = g.coords.last()
                        androidx.compose.ui.unit.IntOffset(
                            (c.x - with(density) { 64.dp.toPx() }).toInt().coerceAtLeast(0),
                            (c.y - with(density) { 26.dp.toPx() }).toInt().coerceAtLeast(0),
                        )
                    },
                )
            }
            val activeIdx = hoverIndex
            if (active != null && activeIdx != null && activeIdx in g.coords.indices) {
                val c = g.coords[activeIdx]
                Column(
                    modifier = Modifier
                        .offset {
                            val half = with(density) { 70.dp.toPx() }
                            androidx.compose.ui.unit.IntOffset(
                                (c.x - half).coerceIn(0f, maxOf(0f, chartWidthPx - half * 2)).toInt(),
                                maxOf(0f, c.y - with(density) { 54.dp.toPx() }).toInt(),
                            )
                        }
                        .clip(RoundedCornerShape(10.dp))
                        .background(surface)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(fmtDate(active.t), fontSize = 10.sp, color = mutedColor)
                    Text(
                        "1 $fromCode = ${fmtRate(active.rateScaled)} $toCode",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private class ChartGeometry(
    val coords: List<Offset>,
    val ticks: List<Long>,
    val baselineY: Float,
    val marginLeft: Float,
    val marginRight: Float,
    val marginTop: Float,
    private val yMin: Long,
    private val yMax: Long,
    private val innerH: Float,
) {
    fun yOf(value: Long): Float =
        marginTop + (1 - (value - yMin).toFloat() / (yMax - yMin).toFloat()) * innerH
}

private fun chartGeometry(
    points: List<RatePoint>,
    width: Float,
    height: Float,
    density: Float,
): ChartGeometry {
    val marginTop = 14 * density
    val marginRight = 18 * density
    val marginBottom = 26 * density
    val marginLeft = 8 * density

    val values = points.map { it.rateScaled }
    val rawMin = values.min()
    val rawMax = values.max()
    // Acolchado vertical del 8% (mínimo 1 unidad escalada).
    val pad = maxOf(1L, Math.round((rawMax - rawMin) * 0.08))
    val yMin = maxOf(0L, rawMin - pad)
    val yMax = rawMax + pad

    val t0 = points.first().t
    val t1 = points.last().t
    val tSpan = (t1 - t0).toFloat()

    val innerW = maxOf(1f, width - marginLeft - marginRight)
    val innerH = height - marginTop - marginBottom

    fun x(t: Long): Float =
        if (tSpan == 0f) marginLeft + innerW / 2 else marginLeft + ((t - t0) / tSpan) * innerW

    fun y(value: Long): Float =
        marginTop + (1 - (value - yMin).toFloat() / (yMax - yMin).toFloat()) * innerH

    val coords = points.map { Offset(x(it.t), y(it.rateScaled)) }

    val step = niceStep((yMax - yMin) / 3.0)
    val ticks = mutableListOf<Long>()
    var v = Math.ceil(yMin.toDouble() / step).toLong() * step
    while (v <= yMax) {
        ticks.add(v)
        v += step
    }

    return ChartGeometry(
        coords, ticks, marginTop + innerH, marginLeft, marginRight, marginTop, yMin, yMax, innerH,
    )
}

