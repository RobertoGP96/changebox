package com.lolo.changebox.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Banknote
import com.composables.icons.lucide.ChartLine
import com.composables.icons.lucide.Coins
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PencilLine
import com.composables.icons.lucide.Plus
import com.lolo.changebox.data.local.dao.TxJoinRow
import com.lolo.changebox.data.repo.AccountCurrency
import com.lolo.changebox.data.repo.AccountDenominationStock
import com.lolo.changebox.data.repo.AccountWithBalance
import com.lolo.changebox.data.repo.IncomeCardData
import com.lolo.changebox.data.repo.PairRatePoint
import com.lolo.changebox.data.repo.toTxRow
import com.lolo.changebox.domain.AccountType
import com.lolo.changebox.domain.DashboardWidget
import com.lolo.changebox.domain.DenominationKind
import com.lolo.changebox.domain.DisplayCurrencyOf
import com.lolo.changebox.domain.WidgetSize
import com.lolo.changebox.domain.fmtMinor
import com.lolo.changebox.domain.fmtRate
import com.lolo.changebox.domain.invertRateScaled
import com.lolo.changebox.domain.pairKey
import com.lolo.changebox.domain.CurrencyTotal
import com.lolo.changebox.domain.totalsByCurrency
import com.lolo.changebox.ui.common.ChangeboxCard
import com.lolo.changebox.ui.common.IconChip
import com.lolo.changebox.ui.common.RateSparkline
import com.lolo.changebox.ui.common.TxList
import com.lolo.changebox.ui.common.fmtShortDateTime
import com.lolo.changebox.ui.theme.ChangeboxColors
import com.lolo.changebox.ui.theme.getAccountIcon

// Gadgets del dashboard y su panel bento, port de dashboard-widgets.tsx y
// bento-panel.tsx: tarjeta de una cuenta, totales por divisa, tasa de un par
// y «Resumen de ingresos». Se instancian desde las preferencias
// (domain/DashboardPrefs.kt). En móvil el panel es una rejilla de 2 columnas:
// sm ocupa media fila, md/lg la fila entera.

/** Clave del «Resumen de ingresos» sin filtro de cuenta (todas, moneda base). */
const val INCOME_ALL_ACCOUNTS = ""

/** Datos que consumen los gadgets, cargados solo si algún gadget los usa. */
data class WidgetData(
    /** Serie histórica por par (clave pairKey), solo si hay gadgets ratePair. */
    val pairSeries: Map<String, List<PairRatePoint>> = emptyMap(),
    /** Por accountId del gadget (o INCOME_ALL_ACCOUNTS); null = no disponible. */
    val incomeCards: Map<String, IncomeCardData?> = emptyMap(),
    /** Últimos movimientos (desc) por cuenta, para accountCard con movimientos. */
    val accountRows: Map<String, List<TxJoinRow>> = emptyMap(),
    /** Stock derivado por cuenta, para accountCard con denominaciones. */
    val stocks: Map<String, AccountDenominationStock> = emptyMap(),
)

/** Suma de saldos por moneda sin conversión (totalsByCurrency de balances-core). */
typealias CurrencyTotalUi = CurrencyTotal<AccountCurrency>

fun homeTotalsByCurrency(accounts: List<AccountWithBalance>): List<CurrencyTotalUi> =
    totalsByCurrency(accounts.map { it.currency to it.balanceMinor }) { it.id }

/** Serie del par; si no hay serie directa se invierte cada punto de la inversa. */
fun pairValues(
    series: Map<String, List<PairRatePoint>>,
    fromId: String,
    toId: String,
): List<Long> {
    series[pairKey(fromId, toId)]?.let { direct -> return direct.map { it.rateScaled } }
    val inverse = series[pairKey(toId, fromId)] ?: return emptyList()
    return inverse.mapNotNull { point ->
        runCatching { invertRateScaled(point.rateScaled) }.getOrNull()
    }
}

/**
 * Panel bento: título «Panel» y rejilla en el orden del array `widgets`. El
 * reordenado por arrastre de la web se hace aquí con las flechas del sheet
 * «Personalizar Inicio» (igual que la web en móvil).
 */
@Composable
fun BentoPanel(
    widgets: List<DashboardWidget>,
    item: @Composable (widget: DashboardWidget, half: Boolean, modifier: Modifier) -> Unit,
) {
    // Colocación automática de CSS grid sin "dense": dos sm consecutivos
    // comparten fila; un sm seguido de uno ancho queda solo en su fila.
    val rows = mutableListOf<List<DashboardWidget>>()
    var pending: DashboardWidget? = null
    for (widget in widgets) {
        if (widget.size == WidgetSize.SM) {
            val waiting = pending
            if (waiting != null) {
                rows.add(listOf(waiting, widget))
                pending = null
            } else {
                pending = widget
            }
        } else {
            pending?.let { rows.add(listOf(it)) }
            pending = null
            rows.add(listOf(widget))
        }
    }
    pending?.let { rows.add(listOf(it)) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Panel",
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        rows.forEach { row ->
            val first = row.first()
            if (row.size == 1 && first.size != WidgetSize.SM) {
                item(first, false, Modifier.fillMaxWidth())
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { widget ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            item(widget, true, Modifier.fillMaxHeight())
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Aviso con borde discontinuo (gadget sin datos o mal configurado). */
@Composable
fun WidgetPlaceholder(
    message: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                drawRoundRect(
                    color = lineColor,
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 4.dp.toPx()),
                        ),
                    ),
                )
            }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            message,
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Tarjeta de UNA cuenta (de caja o no): saldo, accesos y extras opcionales. */
@Composable
fun AccountCardWidget(
    widget: DashboardWidget,
    account: AccountWithBalance?,
    rows: List<TxJoinRow>,
    stock: AccountDenominationStock?,
    half: Boolean,
    modifier: Modifier = Modifier,
    onOpenAccount: (String) -> Unit,
    onRegisterHere: (String) -> Unit,
    onOpenMovement: (String) -> Unit,
    onUpdateCount: (String) -> Unit,
) {
    if (account == null) {
        WidgetPlaceholder(
            "La cuenta de este gadget ya no existe o está archivada. Edítalo desde «Personalizar Inicio».",
            modifier,
        )
        return
    }

    val type = runCatching { AccountType.valueOf(account.type) }.getOrDefault(AccountType.CASH)
    val negative = account.balanceMinor < 0
    val display = DisplayCurrencyOf(account.currency.code, account.currency.decimalPlaces)
    val balanceText: @Composable () -> Unit = {
        Text(
            fmtMinor(account.balanceMinor, display),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = if (negative) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }

    val showDenominations = widget.showDenominations == true &&
        account.type == "CASH_BOX" && stock != null

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // En media columna la tarjeta se estira hasta el alto de la fila.
        ChangeboxCard(
            modifier = if (half && !showDenominations) Modifier.weight(1f) else Modifier,
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IconChip(getAccountIcon(account.icon, type))
                    Column(Modifier.weight(1f)) {
                        Text(
                            account.name,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { onOpenAccount(account.id) },
                        )
                        Text(
                            "${type.labelEs} · ${account.currency.code}",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!half) balanceText()
                }
                // En media columna el saldo baja bajo el nombre para no cortarse.
                if (half) {
                    Box(Modifier.padding(top = 10.dp)) { balanceText() }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 10.dp),
                )
                val registerAction: @Composable () -> Unit = {
                    CardAction(
                        icon = Lucide.Plus,
                        label = "Registrar aquí",
                        highlighted = true,
                        onClick = { onRegisterHere(account.id) },
                    )
                }
                val openAction: @Composable () -> Unit = {
                    CardAction(
                        icon = Lucide.PencilLine,
                        label = "Ver cuenta",
                        highlighted = false,
                        onClick = { onOpenAccount(account.id) },
                    )
                }
                if (half) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        registerAction()
                        openAction()
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        registerAction()
                        Spacer(Modifier.weight(1f))
                        openAction()
                    }
                }

                if (widget.showMovements == true) {
                    Box(Modifier.padding(top = 10.dp)) {
                        if (rows.isNotEmpty()) {
                            // Últimos movimientos en orden cronológico (el más
                            // reciente al final).
                            TxList(
                                rows = rows.map { toTxRow(it, account.id, display) }.reversed(),
                                onOpen = onOpenMovement,
                            )
                        } else {
                            Text(
                                "Sin movimientos todavía.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
        if (showDenominations && stock != null) {
            DenominationAvailability(stock, display) { onUpdateCount(account.id) }
        }
    }
}

@Composable
private fun CardAction(
    icon: ImageVector,
    label: String,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val fg = if (highlighted) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (highlighted) ChangeboxColors.extended.chip else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
    }
}

/**
 * Disponibilidad de denominaciones de una caja: derivada del ÚLTIMO arqueo más
 * los desgloses posteriores (port de denomination-availability.tsx).
 */
@Composable
private fun DenominationAvailability(
    stock: AccountDenominationStock,
    currency: DisplayCurrencyOf,
    onUpdateCount: () -> Unit,
) {
    val lines = stock.lines.filter { it.quantity != 0 }
    val totalMinor = stock.lines.sumOf { it.valueMinor * it.quantity }
    val hasData = stock.countedAt != null || stock.movements > 0

    ChangeboxCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Denominaciones en caja",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Actualizar conteo",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.clickable(onClick = onUpdateCount),
                )
            }

            if (!hasData) {
                Text(
                    "Todavía no hay arqueos ni movimientos con desglose. Haz el primer conteo para registrar qué billetes y monedas hay en la caja.",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                )
            } else {
                Text(
                    buildString {
                        append(
                            stock.countedAt?.let { "Según el arqueo del ${fmtShortDateTime(it)}" }
                                ?: "Sin arqueo base"
                        )
                        if (stock.movements > 0) {
                            append(
                                " · ${stock.movements} " + if (stock.movements == 1) {
                                    "movimiento posterior"
                                } else {
                                    "movimientos posteriores"
                                }
                            )
                        }
                    },
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (lines.isEmpty()) {
                    Text(
                        "La caja está vacía según el último conteo.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    )
                } else {
                    lines.forEach { line ->
                        val negative = line.quantity < 0
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (negative) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                        else ChangeboxColors.extended.chip
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Lucide.Banknote,
                                    contentDescription = null,
                                    tint = if (negative) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    fmtMinor(line.valueMinor, currency),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    buildString {
                                        append(
                                            runCatching {
                                                DenominationKind.valueOf(line.kind).labelEs
                                            }.getOrDefault(line.kind)
                                        )
                                        append(" · × ${line.quantity}")
                                        if (negative) append(" · revisa los desgloses")
                                    },
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                fmtMinor(line.valueMinor * line.quantity, currency),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (negative) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Total en denominaciones",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            fmtMinor(totalMinor, currency),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** Suma de saldos por divisa, sin conversión (complementa las métricas). */
@Composable
fun CurrencyTotalsWidget(
    accounts: List<AccountWithBalance>,
    half: Boolean,
    modifier: Modifier = Modifier,
) {
    val totals = homeTotalsByCurrency(accounts)
    if (totals.isEmpty()) {
        WidgetPlaceholder(
            "Sin cuentas todavía: los totales por moneda aparecerán aquí.",
            modifier,
        )
        return
    }
    ChangeboxCard(modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Lucide.Coins,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    "Totales por moneda",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            val columns = if (half) 1 else 2
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                totals.chunked(columns).forEach { chunk ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        chunk.forEach { total ->
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    total.currency.code.uppercase(),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.6.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    fmtMinor(
                                        total.totalMinor,
                                        DisplayCurrencyOf(
                                            total.currency.code,
                                            total.currency.decimalPlaces,
                                        ),
                                    ),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (total.totalMinor < 0) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        repeat(columns - chunk.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** «Resumen de ingresos»: la tarjeta o el aviso si no hay datos que mostrar. */
@Composable
fun IncomeCardWidget(
    widget: DashboardWidget,
    data: IncomeCardData?,
    half: Boolean,
    modifier: Modifier = Modifier,
    onOpenCurrencies: () -> Unit,
) {
    if (data == null) {
        if (widget.accountId != null) {
            WidgetPlaceholder(
                "La cuenta de este gadget ya no existe. Edítalo desde «Personalizar Inicio».",
                modifier,
            )
        } else {
            WidgetPlaceholder(
                "Define una moneda base en /monedas para ver este gadget.",
                modifier,
                onClick = onOpenCurrencies,
            )
        }
        return
    }
    IncomeCard(widget = widget, data = data, half = half, modifier = modifier)
}

/** Última tasa de un par con su tendencia (misma fuente que /tasas). */
@Composable
fun RatePairWidget(
    fromCode: String,
    toCode: String,
    values: List<Long>,
    half: Boolean,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    val latest = values.lastOrNull()
    ChangeboxCard(modifier = modifier) {
        Column(
            Modifier
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconChip(Lucide.ChartLine, size = 40, corner = 12)
                Column(Modifier.weight(1f)) {
                    Text(
                        "$fromCode → $toCode",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.clickable(onClick = onOpen),
                    )
                    Text(
                        if (latest != null) "1 $fromCode = ${fmtRate(latest)} $toCode"
                        else "Sin tasas registradas para este par",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!half) RateSparkline(values)
            }
            // En media columna la tendencia va debajo (no cabe al lado).
            if (half && values.size > 1) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    RateSparkline(values)
                }
            }
        }
    }
}
