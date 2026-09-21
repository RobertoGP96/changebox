package com.lolo.changebox.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lolo.changebox.data.repo.IncomeCardData
import com.lolo.changebox.domain.DashboardWidget
import com.lolo.changebox.domain.INCOME_CARD_PERIODS
import com.lolo.changebox.domain.IncomeCardMetric
import com.lolo.changebox.domain.IncomeCardPeriod
import com.lolo.changebox.domain.IncomeCardVariant
import com.lolo.changebox.domain.PeriodBucket
import com.lolo.changebox.domain.deltaPct
import com.lolo.changebox.domain.fmtMinor
import com.lolo.changebox.ui.theme.BrandLight
import com.lolo.changebox.ui.theme.BrandSoft
import com.lolo.changebox.ui.theme.ChangeboxColors
import com.lolo.changebox.ui.theme.Danger
import com.lolo.changebox.ui.theme.Gold
import com.lolo.changebox.ui.theme.Navy
import kotlin.math.roundToInt

// Gadget «Resumen de ingresos»: tarjeta con gráfico de línea suave (Canvas)
// y tabs Día/Semana/Mes funcionales. Port de income-card.tsx (variantes soft
// y dark). Recibe las tres series ya calculadas; el cambio de periodo es solo
// estado local.

private val WINDOW_LABELS: Map<IncomeCardPeriod, String> = mapOf(
    IncomeCardPeriod.DAY to "últimos 14 días",
    IncomeCardPeriod.WEEK to "últimas 12 semanas",
    IncomeCardPeriod.MONTH to "últimos 12 meses",
)

// Tonos claros de la variante dark (se leen sobre el navy).
private val DarkExpense = Color(0xFFE79AA7)
private val DarkNet = Color(0xFFE6BD66)

private fun lineColor(variant: IncomeCardVariant, metric: IncomeCardMetric): Color =
    when (variant) {
        IncomeCardVariant.SOFT -> when (metric) {
            IncomeCardMetric.INCOME -> BrandLight
            IncomeCardMetric.EXPENSE -> Danger
            IncomeCardMetric.NET -> Gold
        }
        IncomeCardVariant.DARK -> when (metric) {
            IncomeCardMetric.INCOME -> BrandSoft
            IncomeCardMetric.EXPENSE -> DarkExpense
            IncomeCardMetric.NET -> DarkNet
        }
    }

private val CHART_H = 170.dp
private val PAD_TOP = 24.dp
private val PAD_BOTTOM = 30.dp

/** Puntos de la serie en px: x repartido, y normalizado (serie plana → centro). */
private fun chartPoints(vals: List<Long>, w: Float, h: Float, top: Float, bottom: Float): List<Offset> {
    val min = vals.min()
    val max = vals.max()
    val span = (max - min).toFloat()
    val stepX = if (vals.size > 1) w / (vals.size - 1) else 0f
    return vals.mapIndexed { i, v ->
        val norm = if (span == 0f) 0.5f else (v - min) / span
        Offset(i * stepX, top + (1 - norm) * (h - top - bottom))
    }
}

/** Línea suave Catmull-Rom → Bézier, igual que smoothPath de la web. */
private fun smoothPath(pts: List<Offset>): Path = Path().apply {
    moveTo(pts[0].x, pts[0].y)
    for (i in 0 until pts.size - 1) {
        val p0 = pts.getOrElse(i - 1) { pts[i] }
        val p1 = pts[i]
        val p2 = pts[i + 1]
        val p3 = pts.getOrElse(i + 2) { p2 }
        cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y,
        )
    }
}

private fun metricOf(metric: IncomeCardMetric, bucket: PeriodBucket): Long = when (metric) {
    IncomeCardMetric.INCOME -> bucket.incomeMinor
    IncomeCardMetric.EXPENSE -> bucket.expenseMinor
    IncomeCardMetric.NET -> bucket.incomeMinor - bucket.expenseMinor
}

private data class FooterStat(val label: String, val value: Long, val color: Color)

@Composable
fun IncomeCard(
    widget: DashboardWidget,
    data: IncomeCardData,
    half: Boolean,
    modifier: Modifier = Modifier,
) {
    val variant = widget.variant ?: IncomeCardVariant.SOFT
    val metric = widget.metric ?: IncomeCardMetric.INCOME
    val showTabs = widget.showTabs != false
    val showDelta = widget.showDelta != false
    val heading = widget.title?.takeIf { it.isNotEmpty() }
        ?: data.accountName
        ?: "Resumen de ingresos"

    var periodCode by rememberSaveable(widget.id, widget.defaultPeriod) {
        mutableStateOf((widget.defaultPeriod ?: IncomeCardPeriod.MONTH).code)
    }
    val period = INCOME_CARD_PERIODS.firstOrNull { it.code == periodCode } ?: IncomeCardPeriod.MONTH

    val dark = variant == IncomeCardVariant.DARK
    val line = lineColor(variant, metric)
    val ext = ChangeboxColors.extended
    val danger = MaterialTheme.colorScheme.error

    val buckets = when (period) {
        IncomeCardPeriod.DAY -> data.series.day
        IncomeCardPeriod.WEEK -> data.series.week
        IncomeCardPeriod.MONTH -> data.series.month
    }
    val vals = buckets.map { metricOf(metric, it) }
    val empty = buckets.all { it.incomeMinor == 0L && it.expenseMinor == 0L }

    val totalIncome = buckets.sumOf { it.incomeMinor }
    val totalExpense = buckets.sumOf { it.expenseMinor }
    val totalNet = totalIncome - totalExpense
    val totalMetric = when (metric) {
        IncomeCardMetric.INCOME -> totalIncome
        IncomeCardMetric.EXPENSE -> totalExpense
        IncomeCardMetric.NET -> totalNet
    }

    // Variación del bucket actual vs el anterior (subir gastos es "malo").
    val last = vals.lastOrNull() ?: 0L
    val prev = vals.getOrNull(vals.size - 2) ?: 0L
    val delta = deltaPct(last, prev)
    val deltaGood = delta != null &&
        (if (metric == IncomeCardMetric.EXPENSE) delta <= 0 else delta >= 0)

    val mutedColor = if (dark) Color.White.copy(alpha = 0.55f)
    else MaterialTheme.colorScheme.onSurfaceVariant
    val strongColor = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
    val dividerColor = if (dark) Color.White.copy(alpha = 0.10f)
    else MaterialTheme.colorScheme.outlineVariant

    val stats = buildList {
        if (widget.showIncome != false) {
            add(FooterStat("Ingresos", totalIncome, if (dark) BrandSoft else ext.ok))
        }
        if (widget.showExpense != false) {
            add(FooterStat("Gastos", totalExpense, if (dark) DarkExpense else danger))
        }
        if (widget.showNet != false) {
            add(
                FooterStat(
                    "Neto",
                    totalNet,
                    when {
                        totalNet < 0 -> if (dark) DarkExpense else danger
                        else -> strongColor
                    },
                )
            )
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (dark) Navy else MaterialTheme.colorScheme.surface,
        border = if (dark) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                val title: @Composable () -> Unit = {
                    Text(
                        heading,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = strongColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val tabs: @Composable () -> Unit = {
                    PeriodTabs(period, dark) { periodCode = it.code }
                }
                // En media columna los tabs bajan a su propia línea (el
                // flex-wrap de la web hace lo mismo cuando no caben).
                if (half || !showTabs) {
                    title()
                    if (showTabs) {
                        Box(Modifier.padding(top = 8.dp)) { tabs() }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { title() }
                        tabs()
                    }
                }

                Row(
                    Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        fmtMinor(totalMetric, data.currency),
                        fontSize = if (half) 20.sp else 26.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        color = if (totalMetric < 0 && !dark) danger else strongColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (showDelta && delta != null) {
                        val (bg, fg) = when {
                            deltaGood && dark -> BrandSoft.copy(alpha = 0.20f) to BrandSoft
                            deltaGood -> ext.ok.copy(alpha = 0.14f) to ext.ok
                            dark -> DarkExpense.copy(alpha = 0.20f) to DarkExpense
                            else -> danger.copy(alpha = 0.13f) to danger
                        }
                        Text(
                            "${if (delta >= 0) "+" else ""}$delta%",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = fg,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(bg)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                Text(
                    "${metric.labelEs} · ${WINDOW_LABELS.getValue(period)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = mutedColor,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Box(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .height(CHART_H)
            ) {
                if (empty) {
                    Text(
                        "Sin movimientos en este periodo.",
                        fontSize = 12.5.sp,
                        color = mutedColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    IncomeChart(
                        vals = vals,
                        line = line,
                        dark = dark,
                        tipLabel = buckets.last().label,
                        tipValue = fmtMinor(last, data.currency),
                        mutedColor = mutedColor,
                        strongColor = strongColor,
                    )
                }
            }

            if (stats.isNotEmpty()) {
                HorizontalDivider(color = dividerColor)
                Row(Modifier.fillMaxWidth()) {
                    stats.forEachIndexed { i, stat ->
                        if (i > 0) {
                            VerticalDivider(
                                color = dividerColor,
                                modifier = Modifier.height(58.dp),
                            )
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                fmtMinor(stat.value, data.currency),
                                fontSize = if (half) 12.5.sp else 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = stat.color,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                stat.label,
                                fontSize = 11.5.sp,
                                color = mutedColor,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }

            if (data.missingRates.isNotEmpty()) {
                Text(
                    "Sin tasa para ${data.missingRates.joinToString(", ")}: esos montos no se incluyen.",
                    fontSize = 11.sp,
                    color = mutedColor,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun PeriodTabs(
    selected: IncomeCardPeriod,
    dark: Boolean,
    onSelect: (IncomeCardPeriod) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (dark) Color.White.copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        INCOME_CARD_PERIODS.forEach { period ->
            val active = period == selected
            val bg = when {
                active && dark -> Color.White.copy(alpha = 0.20f)
                active -> MaterialTheme.colorScheme.surface
                else -> Color.Transparent
            }
            val fg = when {
                active && dark -> Color.White
                active -> MaterialTheme.colorScheme.onSurface
                dark -> Color.White.copy(alpha = 0.50f)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                period.labelEs,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = fg,
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(bg)
                    .clickable { onSelect(period) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** Gráfico + punto final + tooltip con el último bucket. */
@Composable
private fun IncomeChart(
    vals: List<Long>,
    line: Color,
    dark: Boolean,
    tipLabel: String,
    tipValue: String,
    mutedColor: Color,
    strongColor: Color,
) {
    var widthPx by remember { mutableIntStateOf(0) }
    val haloColor = if (dark) Color.White.copy(alpha = 0.14f) else BrandLight.copy(alpha = 0.22f)
    val tipBg = if (dark) Color.White.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface

    // Posición del punto final (px) según el mismo cálculo que el Canvas.
    fun Density.lastPoint(): Offset {
        val pts = chartPoints(
            vals,
            widthPx.toFloat(),
            CHART_H.toPx(),
            PAD_TOP.toPx(),
            PAD_BOTTOM.toPx(),
        )
        return pts.last()
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { widthPx = it.width }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pts = chartPoints(
                vals, size.width, size.height, PAD_TOP.toPx(), PAD_BOTTOM.toPx(),
            )
            val path = smoothPath(pts)
            val area = smoothPath(pts).apply {
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(
                area,
                brush = Brush.verticalGradient(
                    0f to line.copy(alpha = if (dark) 0.28f else 0.16f),
                    1f to line.copy(alpha = 0f),
                ),
            )
            drawPath(
                path,
                color = line,
                style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round),
            )
            val dot = pts.last()
            drawLine(
                color = line.copy(alpha = 0.5f),
                start = dot,
                end = Offset(dot.x, size.height),
                strokeWidth = 1.6.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(5.dp.toPx(), 6.dp.toPx()),
                ),
            )
            // El punto se dibuja dentro del lienzo (la web lo centra en el
            // borde derecho con translate -50 %; aquí se recorta el radio).
            val dotCenter = Offset(dot.x - 6.dp.toPx(), dot.y)
            drawCircle(haloColor, radius = 11.dp.toPx(), center = dotCenter)
            drawCircle(line, radius = 6.dp.toPx(), center = dotCenter)
        }

        if (widthPx > 0) {
            // left = clamp(dotLeft% + 2, 4, 62) %, top = max(dotY - 64, 8).
            Column(
                Modifier
                    .offset {
                        val dot = lastPoint()
                        val dotLeftPct = if (widthPx > 0) dot.x / widthPx * 100f else 0f
                        val tipLeftPct = (dotLeftPct + 2f).coerceIn(4f, 62f)
                        IntOffset(
                            (widthPx * tipLeftPct / 100f).roundToInt(),
                            maxOf(dot.y - 64.dp.toPx(), 8.dp.toPx()).roundToInt(),
                        )
                    }
                    .widthIn(min = 100.dp)
                    .then(
                        if (dark) Modifier
                        else Modifier.shadow(12.dp, RoundedCornerShape(12.dp), clip = false)
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .background(tipBg)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(tipLabel, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = mutedColor)
                Text(
                    tipValue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = strongColor,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                )
            }
        }
    }
}
