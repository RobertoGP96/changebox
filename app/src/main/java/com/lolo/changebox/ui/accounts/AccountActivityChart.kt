package com.lolo.changebox.ui.accounts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lolo.changebox.domain.ActivityBucket
import com.lolo.changebox.domain.ActivityDelta
import com.lolo.changebox.domain.ActivityGranularity
import com.lolo.changebox.domain.DisplayCurrency
import com.lolo.changebox.domain.addDays
import com.lolo.changebox.domain.addMonths
import com.lolo.changebox.domain.aggregateActivity
import com.lolo.changebox.domain.bucketRange
import com.lolo.changebox.domain.fmtMinor
import com.lolo.changebox.domain.fmtSignedMinor
import com.lolo.changebox.domain.pow10
import com.lolo.changebox.domain.startOfDay
import com.lolo.changebox.domain.startOfMonth
import com.lolo.changebox.domain.startOfWeek
import com.lolo.changebox.ui.common.ChangeboxCard
import com.lolo.changebox.ui.theme.ChangeboxColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Gráfico de barras con Canvas (sin librería) para la actividad de una cuenta
// — port de account-activity-chart.tsx: barras espejadas por período
// (entradas hacia arriba en ok, salidas hacia abajo en danger) con extremos
// redondeados anclados a la línea cero, rejilla recesiva simétrica, selección
// por columna con tooltip y chips de período resueltos en el cliente. La
// lógica pura (deltas, buckets, calendario) vive en domain/AccountActivity.kt.
// Los montos son Long; Float/Double solo para escalar píxeles.

private enum class ActivityPeriod(
    val label: String,
    val granularity: ActivityGranularity,
    val months: Int?,
    val days: Int?,
) {
    D7("7 días", ActivityGranularity.DAY, null, 6),
    M1("1 mes", ActivityGranularity.DAY, null, 29),
    M3("3 meses", ActivityGranularity.WEEK, null, 7 * 12),
    Y1("1 año", ActivityGranularity.MONTH, 11, null),
    ALL("Todo", ActivityGranularity.MONTH, null, null),
}

private const val CHART_HEIGHT_DP = 240
private const val MARGIN_TOP_DP = 14f
private const val MARGIN_RIGHT_DP = 10f
private const val MARGIN_BOTTOM_DP = 24f
private const val MARGIN_LEFT_DP = 10f

private val ES: Locale = Locale.forLanguageTag("es")
private val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", ES)
private val DAY_FULL: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", ES)
private val MONTH_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", ES)
private val MONTH_FULL: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ES)

private fun localDate(t: Long): LocalDate =
    Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate()

/** Formatea sin el punto de abreviatura de Java ("sept." → "sept", como Intl). */
private fun fmtDay(t: Long, formatter: DateTimeFormatter): String =
    localDate(t).format(formatter).replace(".", "")

private data class ActivityView(
    val granularity: ActivityGranularity,
    val buckets: List<ActivityBucket>,
    val totalIn: Long,
    val totalOut: Long,
)

private fun buildView(deltas: List<ActivityDelta>, period: ActivityPeriod, now: Long): ActivityView? {
    if (deltas.isEmpty()) return null
    val fromT = when {
        period == ActivityPeriod.ALL -> startOfMonth(deltas[0].t)
        period.granularity == ActivityGranularity.MONTH ->
            addMonths(startOfMonth(now), -(period.months ?: 0))
        else -> addDays(
            if (period.granularity == ActivityGranularity.WEEK) startOfWeek(now) else startOfDay(now),
            -(period.days ?: 0),
        )
    }
    val buckets = aggregateActivity(deltas, bucketRange(fromT, now, period.granularity))
    var totalIn = 0L
    var totalOut = 0L
    for (bucket in buckets) {
        totalIn += bucket.inMinor
        totalOut += bucket.outMinor
    }
    return ActivityView(period.granularity, buckets, totalIn, totalOut)
}

/**
 * Paso "bonito" (1/2/5×10^n) para la rejilla, nunca menor que `minStep` (1
 * unidad mayor). Copia de `niceStep` de ui/common/Charts.kt con el mínimo
 * explícito de la web.
 */
private fun niceStep(raw: Double, minStep: Long): Long {
    val magnitude = Math.pow(10.0, Math.floor(Math.log10(maxOf(raw, 1.0))))
    val norm = raw / magnitude
    val factor = if (norm <= 1) 1.0 else if (norm <= 2) 2.0 else if (norm <= 5) 5.0 else 10.0
    return maxOf(minStep, (factor * magnitude).toLong())
}

/** Monto menor → etiqueta compacta de rejilla en unidades mayores ("4 750"). */
private fun tickLabel(minor: Long, decimalPlaces: Int): String {
    val base = pow10(decimalPlaces)
    val absMinor = if (minor < 0) -minor else minor
    val major = (absMinor + base / 2) / base
    val digits = major.toString()
    val sb = StringBuilder()
    val offset = digits.length % 3
    for (i in digits.indices) {
        if (i != 0 && (i - offset) % 3 == 0) sb.append(' ')
        sb.append(digits[i])
    }
    return "${if (minor < 0) "-" else ""}$sb"
}

private fun bucketTick(startT: Long, granularity: ActivityGranularity): String {
    if (granularity == ActivityGranularity.MONTH) {
        val label = fmtDay(startT, MONTH_LABEL)
        val date = localDate(startT)
        return if (date.monthValue == 1) {
            "$label ${(date.year % 100).toString().padStart(2, '0')}"
        } else {
            label
        }
    }
    return fmtDay(startT, DAY_LABEL)
}

private fun bucketFullLabel(startT: Long, endT: Long, granularity: ActivityGranularity): String =
    when (granularity) {
        ActivityGranularity.DAY -> fmtDay(startT, DAY_FULL)
        ActivityGranularity.WEEK ->
            "${fmtDay(startT, DAY_LABEL)} – ${fmtDay(addDays(endT, -1), DAY_LABEL)}"
        ActivityGranularity.MONTH -> fmtDay(startT, MONTH_FULL)
    }

/** Geometría en píxeles del gráfico (compartida por el dibujo y el toque). */
private class ActivityGeometry(
    val left: Float,
    val top: Float,
    val innerH: Float,
    val baseY: Float,
    val scale: Float,
    val slotW: Float,
    val barW: Float,
    val ticks: List<Long>,
)

private fun activityGeometry(
    buckets: List<ActivityBucket>,
    width: Float,
    height: Float,
    density: Float,
    decimalPlaces: Int,
): ActivityGeometry {
    val left = MARGIN_LEFT_DP * density
    val top = MARGIN_TOP_DP * density
    val innerW = max(1f, width - left - MARGIN_RIGHT_DP * density)
    val innerH = height - top - MARGIN_BOTTOM_DP * density
    val baseY = top + innerH / 2

    var maxAbs = 1L
    for (bucket in buckets) maxAbs = maxOf(maxAbs, bucket.inMinor, bucket.outMinor)
    // Acolchado del 8% para que la barra más alta no toque el borde.
    val yMax = maxAbs * 1.08
    val scale = (innerH / 2 / yMax).toFloat()

    val slotW = innerW / max(1, buckets.size)
    val barW = min(26f * density, max(2f * density, slotW - 3f * density))

    // Rejilla simétrica en pasos redondos de la unidad mayor.
    val step = niceStep(yMax / 2, pow10(decimalPlaces))
    val ticks = mutableListOf<Long>()
    var v = step
    while (v.toDouble() <= yMax) {
        ticks.add(v)
        v += step
    }
    return ActivityGeometry(left, top, innerH, baseY, scale, slotW, barW, ticks)
}

/** Columna bajo la coordenada x (acotada a los buckets existentes). */
private fun slotIndexAt(x: Float, width: Float, density: Float, count: Int): Int {
    val left = MARGIN_LEFT_DP * density
    val innerW = max(1f, width - left - MARGIN_RIGHT_DP * density)
    val slotW = innerW / max(1, count)
    return floor((x - left) / slotW).toInt().coerceIn(0, max(0, count - 1))
}

/** Barra vertical con el extremo de datos redondeado, anclada a la línea cero. */
private fun DrawScope.drawBar(color: Color, x: Float, w: Float, baseY: Float, valueY: Float) {
    val h = abs(baseY - valueY)
    if (h <= 0f || w <= 0f) return
    val r = CornerRadius(min(4.5.dp.toPx(), min(w / 2, h)))
    val rect = if (valueY < baseY) {
        // Hacia arriba (entradas)
        RoundRect(
            left = x, top = valueY, right = x + w, bottom = baseY,
            topLeftCornerRadius = r, topRightCornerRadius = r,
        )
    } else {
        // Hacia abajo (salidas)
        RoundRect(
            left = x, top = baseY, right = x + w, bottom = valueY,
            bottomRightCornerRadius = r, bottomLeftCornerRadius = r,
        )
    }
    drawPath(Path().apply { addRoundRect(rect) }, color)
}

/**
 * Actividad de la cuenta. `deltas` en orden cronológico ascendente (ver
 * `activityDeltas`); no se dibuja nada si la cuenta no tiene movimientos.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountActivityChart(deltas: List<ActivityDelta>, currency: DisplayCurrency) {
    if (deltas.isEmpty()) return

    var period by remember { mutableStateOf(ActivityPeriod.M1) }
    var hoverIndex by remember { mutableStateOf<Int?>(null) }
    var widthPx by remember { mutableStateOf(0) }
    // "Ahora" fijo mientras la pantalla vive, como el `now` montado de la web.
    val now = remember { System.currentTimeMillis() }
    val view = remember(deltas, period, now) { buildView(deltas, period, now) } ?: return

    val ext = ChangeboxColors.extended
    val okColor = ext.ok
    val dangerColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val zeroColor = MaterialTheme.colorScheme.outlineVariant
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val washColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val screenDensity = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val decimalPlaces = currency.decimalPlaces
    val bucketCount = view.buckets.size

    ChangeboxCard(corner = 16) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Actividad",
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ActivityPeriod.entries.forEach { p ->
                    val selected = p == period
                    Text(
                        p.label,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary else ext.chip
                            )
                            .clickable {
                                period = p
                                hoverIndex = null
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            Box(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(CHART_HEIGHT_DP.dp)
                    .onSizeChanged { widthPx = it.width },
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CHART_HEIGHT_DP.dp)
                        .semantics {
                            contentDescription =
                                "Actividad de la cuenta: entradas ${fmtMinor(view.totalIn, currency)} " +
                                    "y salidas ${fmtMinor(view.totalOut, currency)} en el período"
                        }
                        // Toque: selecciona la columna (otro toque en ella la suelta).
                        .pointerInput(bucketCount) {
                            detectTapGestures { offset ->
                                val i = slotIndexAt(offset.x, size.width.toFloat(), screenDensity.density, bucketCount)
                                hoverIndex = if (hoverIndex == i) null else i
                            }
                        }
                        // Arrastre horizontal: recorre las columnas.
                        .pointerInput(bucketCount) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    hoverIndex = slotIndexAt(
                                        offset.x, size.width.toFloat(), screenDensity.density, bucketCount,
                                    )
                                },
                            ) { change, _ ->
                                hoverIndex = slotIndexAt(
                                    change.position.x, size.width.toFloat(), screenDensity.density, bucketCount,
                                )
                            }
                        },
                ) {
                    val g = activityGeometry(
                        view.buckets, size.width, size.height, screenDensity.density, decimalPlaces,
                    )
                    val right = size.width - MARGIN_RIGHT_DP * screenDensity.density
                    val tickStyle = TextStyle(fontSize = 9.5.sp, color = mutedColor)

                    // Rejilla recesiva simétrica con etiquetas en unidades mayores
                    g.ticks.forEach { tick ->
                        for (sign in intArrayOf(1, -1)) {
                            val y = g.baseY - sign * tick * g.scale
                            drawLine(gridColor, Offset(g.left, y), Offset(right, y), strokeWidth = 1f)
                            val label = textMeasurer.measure(tickLabel(sign * tick, decimalPlaces), tickStyle)
                            drawText(
                                label,
                                topLeft = Offset(g.left, y - 3.dp.toPx() - label.firstBaseline),
                            )
                        }
                    }

                    // Lavado de la columna activa
                    val active = hoverIndex
                    if (active != null && active in view.buckets.indices) {
                        drawRect(
                            washColor,
                            topLeft = Offset(g.left + active * g.slotW, g.top),
                            size = Size(g.slotW, g.innerH),
                        )
                    }

                    // Línea cero
                    drawLine(
                        zeroColor,
                        Offset(g.left, g.baseY),
                        Offset(right, g.baseY),
                        strokeWidth = 1.dp.toPx(),
                    )

                    // Barras: entradas arriba, salidas abajo
                    view.buckets.forEachIndexed { i, bucket ->
                        val barX = g.left + i * g.slotW + (g.slotW - g.barW) / 2
                        if (bucket.inMinor > 0) {
                            drawBar(okColor, barX, g.barW, g.baseY, g.baseY - bucket.inMinor * g.scale)
                        }
                        if (bucket.outMinor > 0) {
                            drawBar(dangerColor, barX, g.barW, g.baseY, g.baseY + bucket.outMinor * g.scale)
                        }
                    }

                    // Eje X: etiquetas espaciadas para no chocar entre sí
                    val labelEvery = max(1, ceil(bucketCount / 6.0).toInt())
                    val axisStyle = TextStyle(fontSize = 10.sp, color = mutedColor)
                    view.buckets.forEachIndexed { i, bucket ->
                        if (i % labelEvery != 0) return@forEachIndexed
                        val label = textMeasurer.measure(bucketTick(bucket.startT, view.granularity), axisStyle)
                        val centerX = g.left + i * g.slotW + g.slotW / 2
                        val x = (centerX - label.size.width / 2f)
                            .coerceIn(0f, max(0f, size.width - label.size.width))
                        drawText(
                            label,
                            topLeft = Offset(x, size.height - 7.dp.toPx() - label.firstBaseline),
                        )
                    }

                    // Sin actividad en la ventana: el eje queda, el mensaje lo dice
                    if (view.totalIn == 0L && view.totalOut == 0L) {
                        val label = textMeasurer.measure(
                            "Sin operaciones en este período",
                            TextStyle(fontSize = 11.sp, color = mutedColor),
                        )
                        drawText(
                            label,
                            topLeft = Offset(
                                (size.width - label.size.width) / 2f,
                                g.baseY - 12.dp.toPx() - label.firstBaseline,
                            ),
                        )
                    }
                }

                // Tooltip anclado a la columna activa
                val activeIndex = hoverIndex
                val activeBucket = activeIndex?.let { view.buckets.getOrNull(it) }
                if (activeIndex != null && activeBucket != null && widthPx > 0) {
                    val d = screenDensity.density
                    val left = MARGIN_LEFT_DP * d
                    val slotW = max(1f, widthPx - left - MARGIN_RIGHT_DP * d) / max(1, bucketCount)
                    val centerX = left + activeIndex * slotW + slotW / 2
                    val lo = 90f * d
                    val hi = widthPx - 90f * d
                    val anchorX = if (hi < lo) widthPx / 2f else centerX.coerceIn(lo, hi)
                    Column(
                        Modifier
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(
                                    constraints.copy(minWidth = 0, minHeight = 0)
                                )
                                layout(placeable.width, placeable.height) {
                                    placeable.place(
                                        (anchorX - placeable.width / 2f).roundToInt(),
                                        2.dp.roundToPx(),
                                    )
                                }
                            }
                            .clip(RoundedCornerShape(10.dp))
                            .background(surfaceColor)
                            .border(1.dp, zeroColor, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            bucketFullLabel(activeBucket.startT, activeBucket.endT, view.granularity),
                            fontSize = 10.sp,
                            color = mutedColor,
                            maxLines = 1,
                        )
                        TooltipLine(fmtSignedMinor(activeBucket.inMinor, currency), "entradas", okColor)
                        TooltipLine(fmtMinor(-activeBucket.outMinor, currency), "salidas", dangerColor)
                    }
                }
            }

            // Totales del período (hacen también de leyenda de color)
            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LegendTotal(
                    label = "Entradas",
                    dot = okColor,
                    value = fmtSignedMinor(view.totalIn, currency),
                    valueColor = okColor,
                    modifier = Modifier.weight(1f),
                )
                LegendTotal(
                    label = "Salidas",
                    dot = dangerColor,
                    value = fmtMinor(-view.totalOut, currency),
                    valueColor = dangerColor,
                    modifier = Modifier.weight(1f),
                )
                val net = view.totalIn - view.totalOut
                LegendTotal(
                    label = "Neto",
                    dot = null,
                    value = fmtSignedMinor(net, currency),
                    valueColor = if (net < 0) dangerColor else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TooltipLine(amount: String, label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(amount, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun LegendTotal(
    label: String,
    dot: Color?,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (dot != null) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dot)
                )
            }
            Text(
                label,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            value,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
