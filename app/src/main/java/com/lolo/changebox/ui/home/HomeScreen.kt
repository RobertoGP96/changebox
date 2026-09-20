package com.lolo.changebox.ui.home

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.composables.icons.lucide.ArrowDownLeft
import com.composables.icons.lucide.ArrowRightLeft
import com.composables.icons.lucide.ArrowUpRight
import com.composables.icons.lucide.Banknote
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Calculator
import com.composables.icons.lucide.HandCoins
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Wallet
import com.lolo.changebox.data.atEndOfDayMillis
import com.lolo.changebox.data.local.dao.UpcomingInstallmentRow
import com.lolo.changebox.data.local.entity.CurrencyEntity
import com.lolo.changebox.data.repo.AccountWithBalance
import com.lolo.changebox.data.repo.DashboardMetrics
import com.lolo.changebox.data.toLocalDate
import com.lolo.changebox.di.AppContainer
import com.lolo.changebox.di.appViewModel
import com.lolo.changebox.domain.AccountType
import com.lolo.changebox.domain.DisplayCurrencyOf
import com.lolo.changebox.domain.MinorCurrencyOf
import com.lolo.changebox.domain.PlanKind
import com.lolo.changebox.domain.convertMinor
import com.lolo.changebox.domain.daysUntil
import com.lolo.changebox.domain.deltaPct
import com.lolo.changebox.domain.dueLabel
import com.lolo.changebox.domain.fmtMinor
import com.lolo.changebox.ui.Routes
import com.lolo.changebox.ui.common.BadgeVariant
import com.lolo.changebox.ui.common.ChangeboxBadge
import com.lolo.changebox.ui.common.ChangeboxCard
import com.lolo.changebox.ui.common.EmptyState
import com.lolo.changebox.ui.common.contentWidth
import com.lolo.changebox.ui.common.GradientBar
import com.lolo.changebox.ui.common.IconChip
import com.lolo.changebox.ui.common.MonthlyBars
import com.lolo.changebox.ui.common.ScreenHeader
import com.lolo.changebox.ui.common.SectionTitle
import com.lolo.changebox.ui.theme.ChangeboxColors
import com.lolo.changebox.ui.theme.Gold
import com.lolo.changebox.ui.theme.getAccountIcon
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// Inicio: total consolidado, accesos rápidos, métricas del mes, gráfico de
// barras, top de gastos, próximos vencimientos y cuentas — port del
// dashboard (app)/page.tsx.

data class HomeState(
    val loaded: Boolean = false,
    val base: CurrencyEntity? = null,
    val accounts: List<AccountWithBalance> = emptyList(),
    val consolidatedMinor: Long = 0,
    val missingRates: Set<String> = emptySet(),
    val metrics: DashboardMetrics? = null,
    val upcoming: List<UpcomingInstallmentRow> = emptyList(),
)

class HomeViewModel(container: AppContainer) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state = combine(
        container.db.catalogDao().baseCurrencyFlow(),
        container.accounts.accountsWithBalancesFlow(),
        container.rates.latestRatesByCurrencyFlow(),
        container.plans.upcomingInstallmentsFlow(
            LocalDate.now().plusDays(7).atEndOfDayMillis()
        ),
    ) { base, accounts, rates, upcoming ->
        Quad(base, accounts, rates, upcoming)
    }.flatMapLatest { (base, accounts, rates, upcoming) ->
        val metricsFlow = if (base == null) {
            flowOf(null)
        } else {
            container.metrics.dashboardMetricsFlow(base.id, base.decimalPlaces)
        }
        metricsFlow.map { metrics ->
            var consolidated = 0L
            val missing = (metrics?.missingRates ?: emptySet()).toMutableSet()
            if (base != null) {
                for (account in accounts) {
                    if (account.currency.id == base.id) {
                        consolidated += account.balanceMinor
                    } else {
                        val rate = rates[account.currency.id]
                        if (rate != null) {
                            consolidated += convertMinor(
                                account.balanceMinor,
                                MinorCurrencyOf(account.currency.decimalPlaces),
                                MinorCurrencyOf(base.decimalPlaces),
                                rate.rateScaled,
                            )
                        } else if (account.balanceMinor != 0L) {
                            missing.add(account.currency.code)
                        }
                    }
                }
            }
            HomeState(
                loaded = true,
                base = base,
                accounts = accounts,
                consolidatedMinor = consolidated,
                missingRates = missing,
                metrics = metrics,
                upcoming = upcoming,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private data class Quad(
        val base: CurrencyEntity?,
        val accounts: List<AccountWithBalance>,
        val rates: Map<String, com.lolo.changebox.data.repo.PairRatePoint>,
        val upcoming: List<UpcomingInstallmentRow>,
    )
}

private data class QuickAction(val route: String, val icon: ImageVector, val label: String)

@Composable
fun HomeScreen(navController: NavHostController) {
    val vm = appViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    val quickActions = listOf(
        QuickAction(Routes.register("gasto"), Lucide.ArrowUpRight, "Gasto"),
        QuickAction(Routes.register("ingreso"), Lucide.ArrowDownLeft, "Ingreso"),
        QuickAction(Routes.register("transferencia"), Lucide.ArrowRightLeft, "Transferir"),
        QuickAction(Routes.COUNTING, Lucide.Banknote, "Arqueo"),
        QuickAction(Routes.CALCULATOR, Lucide.Calculator, "Calcular"),
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(
            title = "Changebox",
            actions = { NotificationsBell(state.upcoming, navController) },
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                "TOTAL CONSOLIDADO",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp,
            )
            Text(
                state.base?.let { fmtMinor(state.consolidatedMinor, it.toDisplayLocal()) } ?: "—",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            )
            if (state.missingRates.isNotEmpty()) {
                Text(
                    "Sin tasa para ${state.missingRates.joinToString(", ")} · registrar tasa",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.5.sp,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable { navController.navigate(Routes.RATES) },
                )
            }
        }

        Column(
            Modifier
                .contentWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Accesos rápidos
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                quickActions.forEach { action ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { navController.navigate(action.route) }
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        IconChip(action.icon, size = 36, corner = 12)
                        Text(
                            action.label,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Métricas del mes
            val base = state.base
            val metrics = state.metrics
            if (base != null && metrics != null && metrics.series.isNotEmpty()) {
                val current = metrics.series.last()
                val previous = metrics.series.getOrNull(metrics.series.size - 2)
                val incomeDelta = previous?.let { deltaPct(current.incomeMinor, it.incomeMinor) }
                val expenseDelta = previous?.let { deltaPct(current.expenseMinor, it.expenseMinor) }
                val display = base.toDisplayLocal()
                val ext = ChangeboxColors.extended

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(
                            label = "INGRESOS DEL MES",
                            value = fmtMinor(current.incomeMinor, display),
                            valueColor = ext.ok,
                            delta = incomeDelta,
                            deltaGoodWhenPositive = true,
                            deltaVs = previous?.label,
                            modifier = Modifier.weight(1f),
                        )
                        StatCard(
                            label = "GASTOS DEL MES",
                            value = fmtMinor(current.expenseMinor, display),
                            valueColor = MaterialTheme.colorScheme.error,
                            delta = expenseDelta,
                            deltaGoodWhenPositive = false,
                            deltaVs = previous?.label,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(
                            label = "POR COBRAR",
                            value = fmtMinor(metrics.receivableMinor, display),
                            valueColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { navController.navigate(Routes.debts()) },
                        )
                        StatCard(
                            label = "POR PAGAR",
                            value = fmtMinor(metrics.payableMinor, display),
                            valueColor = ext.warn,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { navController.navigate(Routes.debts("pagar")) },
                        )
                    }
                }

                // Ingresos vs gastos · últimos 6 meses
                ChangeboxCard {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Ingresos vs gastos · últimos 6 meses",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(12.dp))
                        val hasData = metrics.series.any { it.incomeMinor > 0 || it.expenseMinor > 0 }
                        if (hasData) {
                            MonthlyBars(metrics.series, display)
                        } else {
                            Text(
                                "Registra movimientos para ver la evolución mensual.",
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }

                // Top gastos del mes
                if (metrics.topCategories.isNotEmpty()) {
                    val maxCategory = metrics.topCategories.first().totalMinor
                    ChangeboxCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Top gastos del mes",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "Ver todo",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.clickable {
                                        navController.navigate(Routes.MOVEMENTS)
                                    },
                                )
                            }
                            metrics.topCategories.forEach { category ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row {
                                        Text(
                                            category.name,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            fmtMinor(category.totalMinor, display),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    GradientBar(
                                        category.totalMinor.toFloat() / maxOf(1L, maxCategory)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Próximos vencimientos
            if (state.upcoming.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle(
                        "Próximos vencimientos",
                        actionLabel = "Ver deudas",
                        onAction = { navController.navigate(Routes.debts()) },
                    )
                    state.upcoming.forEach { row ->
                        UpcomingRow(row) {
                            navController.navigate(
                                if (row.debtId != null) Routes.debtDetail(row.debtId)
                                else Routes.planDetail(row.planId)
                            )
                        }
                    }
                }
            }

            // Cuentas
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(
                    "Cuentas",
                    actionLabel = "+ Nueva cuenta",
                    onAction = { navController.navigate(Routes.NEW_ACCOUNT) },
                )
                if (state.loaded && state.accounts.isEmpty()) {
                    EmptyState(
                        icon = Lucide.Wallet,
                        title = "Sin cuentas todavía",
                        description = "Crea tu primera cuenta o caja para empezar a registrar movimientos.",
                        ctaLabel = "Crear cuenta",
                        onCta = { navController.navigate(Routes.NEW_ACCOUNT) },
                    )
                } else {
                    state.accounts.forEach { account ->
                        AccountCard(account) {
                            navController.navigate(Routes.accountDetail(account.id))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

fun CurrencyEntity.toDisplayLocal() = DisplayCurrencyOf(code, decimalPlaces)

@Composable
private fun StatCard(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    delta: Int? = null,
    deltaGoodWhenPositive: Boolean = true,
    deltaVs: String? = null,
) {
    val ext = ChangeboxColors.extended
    ChangeboxCard(modifier = modifier, corner = 16) {
        Column(Modifier.padding(14.dp)) {
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (delta != null && deltaVs != null) {
                val good = if (deltaGoodWhenPositive) delta >= 0 else delta <= 0
                Text(
                    "${if (delta >= 0) "+" else ""}$delta% vs $deltaVs",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (good) ext.ok else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
fun AccountCard(account: AccountWithBalance, onClick: () -> Unit) {
    val negative = account.balanceMinor < 0
    val type = runCatching { AccountType.valueOf(account.type) }.getOrDefault(AccountType.CASH)
    ChangeboxCard(onClick = onClick) {
        Row(
            Modifier.padding(16.dp),
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
                )
                Text(
                    account.currency.code,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                fmtMinor(
                    account.balanceMinor,
                    DisplayCurrencyOf(account.currency.code, account.currency.decimalPlaces),
                ),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (negative) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun UpcomingRow(row: UpcomingInstallmentRow, onClick: () -> Unit) {
    val days = daysUntil(row.dueAt.toLocalDate())
    val overdue = days < 0
    ChangeboxCard(corner = 16, onClick = onClick) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (overdue) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        else ChangeboxColors.extended.chip
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Lucide.HandCoins,
                    contentDescription = null,
                    tint = if (overdue) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    row.description,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(
                            runCatching { PlanKind.valueOf(row.planKind).labelEs }
                                .getOrDefault(row.planKind)
                        )
                        row.contactName?.let { append(" · $it") }
                    },
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    fmtMinor(
                        row.amountMinor,
                        DisplayCurrencyOf(row.currencyCode, row.currencyDecimals),
                    ),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                ChangeboxBadge(
                    dueLabel(row.dueAt.toLocalDate()),
                    when {
                        overdue -> BadgeVariant.DANGER
                        days <= 1 -> BadgeVariant.WARN
                        else -> BadgeVariant.NEUTRAL
                    },
                )
            }
        }
    }
}

/** Campana de notificaciones con las mensualidades por vencer o vencidas. */
@Composable
private fun NotificationsBell(
    items: List<UpcomingInstallmentRow>,
    navController: NavHostController,
) {
    var open by remember { mutableStateOf(false) }
    val overdueCount = items.count { daysUntil(it.dueAt.toLocalDate()) < 0 }
    val shown = items.take(8)

    Box {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Lucide.Bell,
                contentDescription = if (items.isEmpty()) "Notificaciones"
                else "Notificaciones: ${items.size} mensualidades por vencer",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        if (items.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(
                        if (overdueCount > 0) MaterialTheme.colorScheme.error else Gold
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (items.size > 9) "9+" else items.size.toString(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier
                .width(300.dp)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Text(
                "Mensualidades por vencer",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (shown.isEmpty()) {
                Text(
                    "Sin vencimientos en los próximos días.",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
                )
            } else {
                shown.forEach { item ->
                    val overdue = daysUntil(item.dueAt.toLocalDate()) < 0
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    item.description,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    buildString {
                                        append(
                                            runCatching { PlanKind.valueOf(item.planKind).labelEs }
                                                .getOrDefault(item.planKind)
                                        )
                                        item.contactName?.let { append(" · $it") }
                                    },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${fmtMinor(item.amountMinor, DisplayCurrencyOf(item.currencyCode, item.currencyDecimals))} · ${dueLabel(item.dueAt.toLocalDate())}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (overdue) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            open = false
                            navController.navigate(
                                if (item.debtId != null) Routes.debtDetail(item.debtId)
                                else Routes.planDetail(item.planId)
                            )
                        },
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DropdownMenuItem(
                text = {
                    Text(
                        "Ver todas las deudas",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                },
                onClick = {
                    open = false
                    navController.navigate(Routes.debts())
                },
            )
        }
    }
}


