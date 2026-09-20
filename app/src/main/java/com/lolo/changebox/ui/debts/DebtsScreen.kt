package com.lolo.changebox.ui.debts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.lolo.changebox.data.local.dao.DebtWithMeta
import com.lolo.changebox.data.local.dao.PlanWithMeta
import com.lolo.changebox.data.toLocalDate
import com.lolo.changebox.di.AppContainer
import com.lolo.changebox.di.appViewModel
import com.lolo.changebox.domain.DebtStatus
import com.lolo.changebox.domain.DisplayCurrencyOf
import com.lolo.changebox.domain.Frequency
import com.lolo.changebox.domain.PlanKind
import com.lolo.changebox.domain.daysUntil
import com.lolo.changebox.domain.dueLabel
import com.lolo.changebox.domain.fmtMinor
import com.lolo.changebox.ui.Routes
import com.lolo.changebox.ui.common.BadgeVariant
import com.lolo.changebox.ui.common.ChangeboxBadge
import com.lolo.changebox.ui.common.ChangeboxCard
import com.lolo.changebox.ui.common.EmptyState
import com.lolo.changebox.ui.common.IconChip
import com.lolo.changebox.ui.common.OutlineButton
import com.lolo.changebox.ui.common.PrimaryButton
import com.lolo.changebox.ui.common.ScreenHeader
import com.lolo.changebox.ui.common.SectionTitle
import com.lolo.changebox.ui.common.contentWidth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

// Deudas y cobros: tabs por cobrar/por pagar, deudas abiertas con la próxima
// cuota, mensualidades standalone activas e historial de saldadas/canceladas
// — port de deudas/page.tsx.

data class DebtsState(
    val loaded: Boolean = false,
    val debts: List<DebtWithMeta> = emptyList(),
    val nextDueByDebt: Map<String, Long> = emptyMap(),
    val standalonePlans: List<PlanWithMeta> = emptyList(),
)

class DebtsViewModel(container: AppContainer) : ViewModel() {

    val direction = MutableStateFlow("RECEIVABLE")

    @OptIn(ExperimentalCoroutinesApi::class)
    val state = direction.flatMapLatest { dir ->
        combine(
            container.debts.debtsWithMetaFlow(dir),
            container.debts.nextDueByDebtFlow(),
            container.plans.standalonePlansFlow(),
        ) { debts, nextDue, plans ->
            DebtsState(
                loaded = true,
                debts = debts,
                nextDueByDebt = nextDue.associate { it.debtId to it.dueAt },
                standalonePlans = plans,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebtsState())
}

@Composable
fun DebtsScreen(navController: NavHostController, initialDir: String) {
    val vm = appViewModel { DebtsViewModel(it) }
    var selectedDir by rememberSaveable {
        mutableStateOf(if (initialDir == "pagar") "PAYABLE" else "RECEIVABLE")
    }
    vm.direction.value = selectedDir
    val state by vm.state.collectAsStateWithLifecycle()

    val openDebts = state.debts.filter { it.debt.status == "OPEN" }
    val closedDebts = state.debts.filter { it.debt.status != "OPEN" }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(title = "Deudas y cobros")

        Column(
            Modifier
                .contentWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Dos filas: en pantallas estrechas chips y botones juntos desbordan
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DirChip("Por cobrar", selectedDir == "RECEIVABLE") { selectedDir = "RECEIVABLE" }
                    DirChip("Por pagar", selectedDir == "PAYABLE") { selectedDir = "PAYABLE" }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    PrimaryButton("Deuda", onClick = { navController.navigate(Routes.NEW_DEBT) })
                    OutlineButton("Mensualidad", onClick = { navController.navigate(Routes.NEW_PLAN) })
                }
            }

            if (state.loaded && openDebts.isEmpty()) {
                EmptyState(
                    icon = Lucide.HandCoins,
                    title = if (selectedDir == "RECEIVABLE") "Nada por cobrar" else "Nada por pagar",
                    description = "Registra una deuda para llevar el control de abonos y vencimientos.",
                    ctaLabel = "Nueva deuda",
                    onCta = { navController.navigate(Routes.NEW_DEBT) },
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    openDebts.forEach { item ->
                        OpenDebtCard(
                            item = item,
                            nextDueAt = state.nextDueByDebt[item.debt.id],
                        ) { navController.navigate(Routes.debtDetail(item.debt.id)) }
                    }
                }
            }

            if (state.standalonePlans.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Mensualidades activas")
                    state.standalonePlans.forEach { item ->
                        StandalonePlanCard(item) {
                            navController.navigate(Routes.planDetail(item.plan.id))
                        }
                    }
                }
            }

            if (closedDebts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

@Composable
private fun DirChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else com.lolo.changebox.ui.theme.ChangeboxColors.extended.chip
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
private fun OpenDebtCard(item: DebtWithMeta, nextDueAt: Long?, onClick: () -> Unit) {
    val remaining = item.debt.totalMinor - item.paidMinor
    val display = DisplayCurrencyOf(item.currencyCode, item.currencyDecimals)
    val days = nextDueAt?.let { daysUntil(it.toLocalDate()) }
    ChangeboxCard(onClick = onClick) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    item.contactName,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
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
                if (nextDueAt != null && days != null) {
                    ChangeboxBadge(
                        dueLabel(nextDueAt.toLocalDate()),
                        when {
                            days < 0 -> BadgeVariant.DANGER
                            days <= 1 -> BadgeVariant.WARN
                            else -> BadgeVariant.NEUTRAL
                        },
                        icon = Lucide.CalendarClock,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    fmtMinor(remaining, display),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "pendiente",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Lucide.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun StandalonePlanCard(item: PlanWithMeta, onClick: () -> Unit) {
    val display = DisplayCurrencyOf(item.currencyCode, item.currencyDecimals)
    val days = item.nextPendingDueAt?.let { daysUntil(it.toLocalDate()) }
    ChangeboxCard(corner = 16, onClick = onClick) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconChip(Lucide.CalendarClock, size = 36, corner = 12)
            Column(Modifier.weight(1f)) {
                Text(
                    item.plan.description,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(
                            runCatching { PlanKind.valueOf(item.plan.kind).labelEs }
                                .getOrDefault(item.plan.kind)
                        )
                        append(" · ")
                        append(
                            runCatching { Frequency.valueOf(item.plan.frequency).labelEs }
                                .getOrDefault(item.plan.frequency)
                        )
                        item.contactName?.let { append(" · $it") }
                    },
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    fmtMinor(item.plan.amountMinor, display),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (item.nextPendingDueAt != null && days != null) {
                    ChangeboxBadge(
                        dueLabel(item.nextPendingDueAt.toLocalDate()),
                        when {
                            days < 0 -> BadgeVariant.DANGER
                            days <= 1 -> BadgeVariant.WARN
                            else -> BadgeVariant.NEUTRAL
                        },
                    )
                }
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
            Icon(
                Lucide.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

