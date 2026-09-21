package com.lolo.changebox.ui.debts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.HandCoins
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Repeat
import com.lolo.changebox.data.local.dao.DebtWithMeta
import com.lolo.changebox.data.toLocalDate
import com.lolo.changebox.di.AppContainer
import com.lolo.changebox.di.appViewModel
import com.lolo.changebox.domain.DebtStatus
import com.lolo.changebox.domain.DisplayCurrencyOf
import com.lolo.changebox.domain.DueTone
import com.lolo.changebox.domain.daysUntil
import com.lolo.changebox.domain.dueLabel
import com.lolo.changebox.domain.dueTone
import com.lolo.changebox.domain.fmtMinor
import com.lolo.changebox.ui.Routes
import com.lolo.changebox.ui.common.BadgeVariant
import com.lolo.changebox.ui.common.ChangeboxBadge
import com.lolo.changebox.ui.common.ChangeboxCard
import com.lolo.changebox.ui.common.EmptyState
import com.lolo.changebox.ui.common.ScreenHeader
import com.lolo.changebox.ui.common.SectionTitle
import com.lolo.changebox.ui.common.contentWidth
import com.lolo.changebox.ui.theme.BrandMid
import com.lolo.changebox.ui.theme.ChangeboxColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

// Deudas: TODAS por defecto (chips Todas / Por cobrar / Por pagar), cabecera
// con el pendiente por moneda en «Me deben» / «Debo», tarjetas con avatar de
// iniciales, progreso del abonado y badge de la próxima cuota, e «Historial»
// para saldadas/canceladas — port de deudas/page.tsx. Las mensualidades
// viven en su propia vista (MonthlyPlansScreen), enlazada desde aquí.

data class DebtsState(
    val loaded: Boolean = false,
    val debts: List<DebtWithMeta> = emptyList(),
    val nextDueByDebt: Map<String, Long> = emptyMap(),
)

class DebtsViewModel(container: AppContainer) : ViewModel() {

    /** RECEIVABLE | PAYABLE | null (todas). */
    val direction = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state = direction.flatMapLatest { dir ->
        combine(
            container.debts.debtsWithMetaFlow(dir),
            container.debts.nextDueByDebtFlow(),
        ) { debts, nextDue ->
            DebtsState(
                loaded = true,
                debts = debts,
                nextDueByDebt = nextDue.associate { it.debtId to it.dueAt },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebtsState())
}

/** Filtros de la web: valor del ?dir= y su etiqueta ("" = Todas). */
private val DEBT_FILTERS = listOf(
    "" to "Todas",
    "cobrar" to "Por cobrar",
    "pagar" to "Por pagar",
)

/** Iniciales del contacto para el avatar de la tarjeta. */
internal fun initials(name: String): String =
    name.trim()
        .split(Regex("\\s+"))
        .take(2)
        .joinToString("") { it.take(1).uppercase() }
        .ifEmpty { "?" }

/**
 * Porcentaje abonado: `Math.min(100, Math.round(paid / max(1, total) * 100))`
 * de la web, en enteros (Math.round de JS = floor(x + ½)).
 */
internal fun paidPercent(paidMinor: Long, totalMinor: Long): Int {
    val total = maxOf(1L, totalMinor)
    val pct = Math.floorDiv(200L * paidMinor + total, 2L * total)
    return minOf(100L, pct).toInt()
}

/** Mapea el tono de vencimiento del dominio al badge de la UI. */
internal fun DueTone.toBadge(): BadgeVariant = when (this) {
    DueTone.DANGER -> BadgeVariant.DANGER
    DueTone.WARN -> BadgeVariant.WARN
    DueTone.NEUTRAL -> BadgeVariant.NEUTRAL
}

@Composable
fun DebtsScreen(navController: NavHostController, initialDir: String) {
    val vm = appViewModel { DebtsViewModel(it) }
    var selectedDir by rememberSaveable {
        mutableStateOf(if (initialDir == "pagar" || initialDir == "cobrar") initialDir else "")
    }
    LaunchedEffect(selectedDir) {
        vm.direction.value = when (selectedDir) {
            "pagar" -> "PAYABLE"
            "cobrar" -> "RECEIVABLE"
            else -> null
        }
    }
    val state by vm.state.collectAsStateWithLifecycle()

    val openDebts = state.debts.filter { it.debt.status == "OPEN" }
    val closedDebts = state.debts.filter { it.debt.status != "OPEN" }

    // Totales del pendiente por moneda, separados por dirección.
    fun totalsText(wanted: String): String {
        val totals = LinkedHashMap<String, Pair<Long, DisplayCurrencyOf>>()
        for (item in openDebts) {
            if (item.debt.direction != wanted) continue
            val remaining = item.debt.totalMinor - item.paidMinor
            val entry = totals[item.debt.currencyId]
            totals[item.debt.currencyId] = if (entry != null) {
                (entry.first + remaining) to entry.second
            } else {
                remaining to DisplayCurrencyOf(item.currencyCode, item.currencyDecimals)
            }
        }
        return totals.values
            .sortedByDescending { it.first }
            .joinToString(" · ") { fmtMinor(it.first, it.second) }
    }
    val receivableText = totalsText("RECEIVABLE")
    val payableText = totalsText("PAYABLE")

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(
            title = "Deudas",
            actions = { HeaderNewButton { navController.navigate(Routes.NEW_DEBT) } },
        ) {
            HeaderTotals(
                listOf("Me deben" to receivableText, "Debo" to payableText),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HeaderChip("${openDebts.size} abierta${if (openDebts.size == 1) "" else "s"}")
            }
        }

        Column(
            Modifier
                .contentWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            FilterBar(
                filters = DEBT_FILTERS,
                selected = selectedDir,
                onSelect = { selectedDir = it },
                linkLabel = "Mensualidades",
                linkIcon = Lucide.Repeat,
                onLink = { navController.navigate(Routes.monthlyPlans()) },
            )

            if (state.loaded && openDebts.isEmpty() && closedDebts.isEmpty()) {
                EmptyState(
                    icon = Lucide.HandCoins,
                    title = "Sin deudas registradas",
                    description = "Anota lo que te deben o lo que debes para llevar el control de abonos y vencimientos.",
                    ctaLabel = "Nueva deuda",
                    onCta = { navController.navigate(Routes.NEW_DEBT) },
                )
            } else if (openDebts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    openDebts.forEach { item ->
                        OpenDebtCard(
                            item = item,
                            nextDueAt = state.nextDueByDebt[item.debt.id],
                        ) { navController.navigate(Routes.debtDetail(item.debt.id)) }
                    }
                }
            }

            if (closedDebts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Historial")
                    closedDebts.forEach { item ->
                        ClosedDebtCard(item) {
                            navController.navigate(Routes.debtDetail(item.debt.id))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Piezas compartidas con MonthlyPlansScreen ───────────────────────────────

/** Botón "Nueva" translúcido del lado derecho de la cabecera. */
@Composable
internal fun HeaderNewButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Lucide.Plus, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Text("Nueva", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Bloques etiqueta/cifra de la cabecera; se omiten los de texto vacío. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HeaderTotals(items: List<Pair<String, String>>) {
    val visible = items.filter { it.second.isNotEmpty() }
    if (visible.isEmpty()) return
    Spacer(Modifier.height(12.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        visible.forEach { (label, text) ->
            Column {
                Text(
                    label.uppercase(),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                )
                Text(
                    text,
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                )
            }
        }
    }
}

/** Chip de la cabecera ("N abiertas", "N con cuota vencida"). */
@Composable
internal fun HeaderChip(text: String, danger: Boolean = false) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (danger) MaterialTheme.colorScheme.errorContainer
                else Color.White.copy(alpha = 0.15f)
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (danger) MaterialTheme.colorScheme.error else Color.White,
        )
    }
}

/** Chips de filtro a la izquierda + enlace a la vista hermana a la derecha. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilterBar(
    filters: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    linkLabel: String,
    linkIcon: ImageVector,
    onLink: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            filters.forEach { (value, label) ->
                FilterChip(label, selected == value) { onSelect(value) }
            }
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    RoundedCornerShape(9.dp),
                )
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onLink)
                .padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                linkIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Text(
                linkLabel,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else ChangeboxColors.extended.chip
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun ChevronIcon() {
    Icon(
        Lucide.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.size(16.dp),
    )
}

// ── Tarjetas de deuda ───────────────────────────────────────────────────────

@Composable
private fun OpenDebtCard(item: DebtWithMeta, nextDueAt: Long?, onClick: () -> Unit) {
    val debt = item.debt
    val paid = item.paidMinor
    val remaining = debt.totalMinor - paid
    val pct = paidPercent(paid, debt.totalMinor)
    val display = DisplayCurrencyOf(item.currencyCode, item.currencyDecimals)
    val days = nextDueAt?.let { daysUntil(it.toLocalDate()) }
    val receivable = debt.direction == "RECEIVABLE"
    val ext = ChangeboxColors.extended
    val tone = if (receivable) ext.ok else ext.warn

    ChangeboxCard(onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(tone.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        initials(item.contactName),
                        color = tone,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            item.contactName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            fmtMinor(remaining, display),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            debt.description,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (receivable) "por cobrar" else "por pagar",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                Box(Modifier.padding(top = 4.dp)) { ChevronIcon() }
            }

            if (paid > 0) {
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(pct / 100f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (receivable) ext.ok else BrandMid)
                        )
                    }
                    Text(
                        "Abonado ${fmtMinor(paid, display)} de ${fmtMinor(debt.totalMinor, display)} ($pct%)",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (nextDueAt != null && days != null) {
                ChangeboxBadge(
                    "Próxima cuota · ${dueLabel(nextDueAt.toLocalDate())}",
                    dueTone(days).toBadge(),
                    icon = Lucide.CalendarClock,
                )
            }
        }
    }
}

@Composable
private fun ClosedDebtCard(item: DebtWithMeta, onClick: () -> Unit) {
    val display = DisplayCurrencyOf(item.currencyCode, item.currencyDecimals)
    ChangeboxCard(corner = 16, onClick = onClick) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.contactName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.debt.description,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    fmtMinor(item.debt.totalMinor, display),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                ChangeboxBadge(
                    runCatching { DebtStatus.valueOf(item.debt.status).labelEs }
                        .getOrDefault(item.debt.status),
                    if (item.debt.status == "PAID") BadgeVariant.OK else BadgeVariant.NEUTRAL,
                )
            }
            ChevronIcon()
        }
    }
}
