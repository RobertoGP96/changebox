package com.lolo.changebox.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.Wallet
import com.lolo.changebox.data.ActionResult
import com.lolo.changebox.data.atEndOfDayMillis
import com.lolo.changebox.data.guarded
import com.lolo.changebox.data.local.dao.UpcomingInstallmentRow
import com.lolo.changebox.data.local.entity.CurrencyEntity
import com.lolo.changebox.data.repo.AccountWithBalance
import com.lolo.changebox.data.repo.DashboardMetrics
import com.lolo.changebox.data.repo.PairRatePoint
import com.lolo.changebox.data.toLocalDate
import com.lolo.changebox.di.AppContainer
import com.lolo.changebox.di.appViewModel
import com.lolo.changebox.domain.AccountType
import com.lolo.changebox.domain.DashboardPrefs
import com.lolo.changebox.domain.DashboardWidget
import com.lolo.changebox.domain.DisplayCurrencyOf
import com.lolo.changebox.domain.PlanKind
import com.lolo.changebox.domain.WidgetType
import com.lolo.changebox.domain.daysUntil
import com.lolo.changebox.domain.defaultDashboardPrefs
import com.lolo.changebox.domain.deltaPct
import com.lolo.changebox.domain.dueLabel
import com.lolo.changebox.domain.fmtMinor
import com.lolo.changebox.ui.Routes
import com.lolo.changebox.ui.common.BadgeVariant
import com.lolo.changebox.ui.common.ChangeboxBadge
import com.lolo.changebox.ui.common.ChangeboxCard
import com.lolo.changebox.ui.common.EmptyState
import com.lolo.changebox.ui.common.GradientBar
import com.lolo.changebox.ui.common.HeaderIconButton
import com.lolo.changebox.ui.common.IconChip
import com.lolo.changebox.ui.common.LocalToast
import com.lolo.changebox.ui.common.MonthlyBars
import com.lolo.changebox.ui.common.ScreenHeader
import com.lolo.changebox.ui.common.SectionTitle
import com.lolo.changebox.ui.common.contentWidth
import com.lolo.changebox.ui.theme.BrandSoft
import com.lolo.changebox.ui.theme.ChangeboxColors
import com.lolo.changebox.ui.theme.Gold
import com.lolo.changebox.ui.theme.getAccountIcon
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// Inicio personalizable: chips del mes en la cabecera y secciones en el orden
// y visibilidad de las preferencias (accesos rápidos, panel de gadgets,
// gráfico, top de gastos, próximos vencimientos y cuentas). Port del
// dashboard (app)/page.tsx; las secciones sin datos se omiten sin hueco.

data class HomeState(
    val loaded: Boolean = false,
    val base: CurrencyEntity? = null,
    /** Cuentas activas (no archivadas) con saldo derivado. */
    val accounts: List<AccountWithBalance> = emptyList(),
    /** Monedas activas, base primero (selector de pares de los gadgets). */
    val currencies: List<CurrencyEntity> = emptyList(),
    val metrics: DashboardMetrics? = null,
    val upcoming: List<UpcomingInstallmentRow> = emptyList(),
    val prefs: DashboardPrefs = defaultDashboardPrefs(),
    val widgetData: WidgetData = WidgetData(),
)

/**
 * Combina un flujo por clave en un mapa clave → valor. Con cero claves emite
 * un mapa vacío (el combine de una lista vacía no emitiría nunca).
 */
private fun <V> combineKeyed(
    keys: List<String>,
    source: (String) -> Flow<V>,
): Flow<Map<String, V>> {
    var acc: Flow<Map<String, V>> = flowOf(emptyMap())
    for (key in keys) {
        acc = acc.combine(source(key)) { map, value -> map + (key to value) }
    }
    return acc
}

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private data class Core(
        val base: CurrencyEntity?,
        val accounts: List<AccountWithBalance>,
        val currencies: List<CurrencyEntity>,
        val upcoming: List<UpcomingInstallmentRow>,
        val prefs: DashboardPrefs,
    )

    private val prefsFlow: Flow<DashboardPrefs> = container.prefs.dashboardPrefsFlow

    private val coreFlow: Flow<Core> = combine(
        container.db.catalogDao().baseCurrencyFlow(),
        container.accounts.accountsWithBalancesFlow(),
        container.db.catalogDao().activeCurrenciesFlow(),
        container.plans.upcomingInstallmentsFlow(
            LocalDate.now().plusDays(7).atEndOfDayMillis()
        ),
        prefsFlow,
    ) { base, accounts, currencies, upcoming, prefs ->
        Core(base, accounts, currencies, upcoming, prefs)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val metricsFlow: Flow<DashboardMetrics?> =
        container.db.catalogDao().baseCurrencyFlow().flatMapLatest { base ->
            if (base == null) {
                flowOf(null)
            } else {
                container.metrics.dashboardMetricsFlow(base.id, base.decimalPlaces)
            }
        }

    /** Datos de los gadgets: solo se consulta lo que algún gadget usa. */
    private fun widgetDataFlow(widgets: List<DashboardWidget>): Flow<WidgetData> {
        val incomeKeys = widgets
            .filter { it.type == WidgetType.INCOME_CARD }
            .map { it.accountId ?: INCOME_ALL_ACCOUNTS }
            .distinct()
        val movementAccounts = widgets
            .filter { it.type == WidgetType.ACCOUNT_CARD && it.showMovements == true }
            .mapNotNull { it.accountId }
            .distinct()
        val stockAccounts = widgets
            .filter { it.type == WidgetType.ACCOUNT_CARD && it.showDenominations == true }
            .mapNotNull { it.accountId }
            .distinct()
        val pairFlow: Flow<Map<String, List<PairRatePoint>>> =
            if (widgets.any { it.type == WidgetType.RATE_PAIR }) {
                container.rates.pairSeriesFlow()
            } else {
                flowOf(emptyMap())
            }

        return combine(
            pairFlow,
            combineKeyed(incomeKeys) { key ->
                container.metrics.incomeCardDataFlow(
                    if (key == INCOME_ALL_ACCOUNTS) null else key
                )
            },
            combineKeyed(movementAccounts) { accountId ->
                container.ledger.accountRowsFlow(accountId, 3)
            },
            combineKeyed(stockAccounts) { accountId ->
                container.accounts.denominationStockFlow(accountId)
            },
        ) { pairs, incomeCards, rows, stocks ->
            WidgetData(
                pairSeries = pairs,
                incomeCards = incomeCards,
                accountRows = rows,
                stocks = stocks,
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val widgetsFlow: Flow<WidgetData> = prefsFlow
        .map { it.widgets }
        .distinctUntilChanged()
        .flatMapLatest { widgetDataFlow(it) }

    val state: StateFlow<HomeState> = combine(coreFlow, metricsFlow, widgetsFlow) { core, metrics, widgetData ->
        HomeState(
            loaded = true,
            base = core.base,
            accounts = core.accounts,
            currencies = core.currencies,
            metrics = metrics,
            upcoming = core.upcoming,
            prefs = core.prefs,
            widgetData = widgetData,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    /**
     * Guarda las preferencias (saveDashboardPrefs): las cuentas y monedas se
     * validan contra las que existen (incluidas archivadas/inactivas).
     */
    suspend fun saveDashboardPrefs(prefs: DashboardPrefs): ActionResult<Unit> =
        guarded("No se pudieron guardar las preferencias") {
            val accountIds = container.accounts
                .accountsWithBalancesFlow(includeArchived = true)
                .first()
                .map { it.id }
                .toSet()
            val currencyIds = container.db.catalogDao().currenciesFlow()
                .first()
                .map { it.id }
                .toSet()
            container.prefs.saveDashboardPrefs(prefs, accountIds, currencyIds)
        }
}

private data class QuickAction(val route: String, val icon: ImageVector, val label: String)

@Composable
fun HomeScreen(navController: NavHostController) {
    val vm = appViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    var customizerOpen by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(
            title = "Changebox",
            actions = {
                HeaderIconButton(
                    icon = Lucide.SlidersHorizontal,
                    contentDescription = "Personalizar Inicio",
                    onClick = { customizerOpen = true },
                )
                NotificationsBell(state.upcoming, navController)
            },
        ) {
            HeaderSummary(state) { navController.navigate(Routes.RATES) }
        }

        Column(
            Modifier
                .contentWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (state.loaded) {
                // Orden y visibilidad de las preferencias; una sección sin
                // datos no emite nada, así que no deja hueco.
                state.prefs.sections.forEach { section ->
                    if (section.visible) {
                        HomeSection(section.key, state, navController)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (customizerOpen) {
        DashboardCustomizerSheet(
            prefs = state.prefs,
            accounts = state.accounts,
            currencies = state.currencies,
            onDismiss = { customizerOpen = false },
            onSave = { prefs -> vm.saveDashboardPrefs(prefs) },
            onSaved = {
                customizerOpen = false
                toast("Inicio actualizado")
            },
        )
    }
}

/** Aviso de monedas sin tasa + chips del mes (Ingresos, Gastos, Neto…). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderSummary(state: HomeState, onOpenRates: () -> Unit) {
    val base = state.base
    val metrics = state.metrics
    // Monedas sin tasa: avisa si sus movimientos quedan fuera de las métricas.
    val missingRates = metrics?.missingRates ?: emptySet()

    if (missingRates.isNotEmpty()) {
        Text(
            buildAnnotatedString {
                append("Sin tasa para ${missingRates.joinToString(", ")} · ")
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                    append("registrar tasa")
                }
            },
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.5.sp,
            modifier = Modifier
                .padding(top = 16.dp)
                .clickable(onClick = onOpenRates),
        )
    }

    val current = metrics?.series?.lastOrNull()
    if (base == null || metrics == null || current == null) return
    val previous = metrics.series.getOrNull(metrics.series.size - 2)
    val incomeDelta = previous?.let { deltaPct(current.incomeMinor, it.incomeMinor) }
    val expenseDelta = previous?.let { deltaPct(current.expenseMinor, it.expenseMinor) }
    val display = base.toDisplayLocal()

    val chips = buildList {
        add(HeaderChip("Ingresos mes", fmtMinor(current.incomeMinor, display), incomeDelta,
            incomeDelta != null && incomeDelta >= 0))
        add(HeaderChip("Gastos mes", fmtMinor(current.expenseMinor, display), expenseDelta,
            expenseDelta != null && expenseDelta <= 0))
        add(HeaderChip("Neto mes",
            fmtMinor(current.incomeMinor - current.expenseMinor, display), null, true))
        if (metrics.receivableMinor > 0) {
            add(HeaderChip("Por cobrar", fmtMinor(metrics.receivableMinor, display), null, true))
        }
        if (metrics.payableMinor > 0) {
            add(HeaderChip("Por pagar", fmtMinor(metrics.payableMinor, display), null, true))
        }
    }

    FlowRow(
        modifier = Modifier.padding(top = if (missingRates.isEmpty()) 14.dp else 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    chip.label.uppercase(),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        chip.value,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    if (chip.delta != null) {
                        Text(
                            "${if (chip.delta >= 0) "+" else ""}${chip.delta}%",
                            color = if (chip.deltaGood) BrandSoft else Color(0xFFF2A9B4),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

private data class HeaderChip(
    val label: String,
    val value: String,
    val delta: Int?,
    val deltaGood: Boolean,
)

/** Una sección personalizable; no emite nada si no tiene datos. */
@Composable
private fun HomeSection(key: String, state: HomeState, navController: NavHostController) {
    when (key) {
        "quickActions" -> QuickActionsSection(navController)
        "widgetPanel" -> WidgetPanelSection(state, navController)
        "monthlyChart" -> MonthlyChartSection(state)
        "topCategories" -> TopCategoriesSection(state, navController)
        "upcomingInstallments" -> UpcomingSection(state.upcoming, navController)
        "accounts" -> AccountsSection(state, navController)
        else -> Unit
    }
}

@Composable
private fun QuickActionsSection(navController: NavHostController) {
    val quickActions = listOf(
        QuickAction(Routes.register("gasto"), Lucide.ArrowUpRight, "Gasto"),
        QuickAction(Routes.register("ingreso"), Lucide.ArrowDownLeft, "Ingreso"),
        QuickAction(Routes.register("transferencia"), Lucide.ArrowRightLeft, "Transferir"),
        QuickAction(Routes.COUNTING, Lucide.Banknote, "Arqueo"),
        QuickAction(Routes.CALCULATOR, Lucide.Calculator, "Calcular"),
    )
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
}

@Composable
private fun WidgetPanelSection(state: HomeState, navController: NavHostController) {
    val widgets = state.prefs.widgets
    if (widgets.isEmpty()) return
    val data = state.widgetData
    val accountById = state.accounts.associateBy { it.id }
    val currencyById = state.currencies.associateBy { it.id }

    BentoPanel(widgets) { widget, half, modifier ->
        when (widget.type) {
            WidgetType.ACCOUNT_CARD -> {
                val accountId = widget.accountId
                AccountCardWidget(
                    widget = widget,
                    account = accountId?.let { accountById[it] },
                    rows = accountId?.let { data.accountRows[it] } ?: emptyList(),
                    stock = accountId?.let { data.stocks[it] },
                    half = half,
                    modifier = modifier,
                    onOpenAccount = { navController.navigate(Routes.accountDetail(it)) },
                    onRegisterHere = { navController.navigate(Routes.register(cuenta = it)) },
                    onOpenMovement = { navController.navigate(Routes.movementDetail(it)) },
                    onUpdateCount = { navController.navigate(Routes.cashCount(it)) },
                )
            }

            WidgetType.CURRENCY_TOTALS -> CurrencyTotalsWidget(state.accounts, half, modifier)

            WidgetType.INCOME_CARD -> {
                val key = widget.accountId ?: INCOME_ALL_ACCOUNTS
                // Recién añadido y aún sin datos cargados: hueco vacío en vez
                // de un aviso engañoso durante un instante.
                if (!data.incomeCards.containsKey(key)) {
                    Box(modifier)
                } else {
                    IncomeCardWidget(
                        widget = widget,
                        data = data.incomeCards[key],
                        half = half,
                        modifier = modifier,
                        onOpenCurrencies = { navController.navigate(Routes.CURRENCIES) },
                    )
                }
            }

            WidgetType.RATE_PAIR -> {
                val from = widget.fromCurrencyId?.let { currencyById[it] }
                val to = widget.toCurrencyId?.let { currencyById[it] }
                if (from == null || to == null) {
                    // Moneda desactivada o borrada: la celda queda vacía (web).
                    Box(modifier)
                } else {
                    RatePairWidget(
                        fromCode = from.code,
                        toCode = to.code,
                        values = pairValues(data.pairSeries, from.id, to.id),
                        half = half,
                        modifier = modifier,
                        onOpen = { navController.navigate(Routes.ratePair(from.code, to.code)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthlyChartSection(state: HomeState) {
    val base = state.base ?: return
    val metrics = state.metrics ?: return
    val display = base.toDisplayLocal()
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
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun TopCategoriesSection(state: HomeState, navController: NavHostController) {
    val base = state.base ?: return
    val metrics = state.metrics ?: return
    if (metrics.topCategories.isEmpty()) return
    val display = base.toDisplayLocal()
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

/** Próximos vencimientos (≤ 7 días), enlazados a mensualidades como la web. */
@Composable
private fun UpcomingSection(
    upcoming: List<UpcomingInstallmentRow>,
    navController: NavHostController,
) {
    if (upcoming.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(
            "Próximos vencimientos",
            actionLabel = "Ver mensualidades",
            onAction = { navController.navigate(Routes.monthlyPlans()) },
        )
        upcoming.forEach { row ->
            UpcomingRow(row) {
                navController.navigate(
                    if (row.debtId != null) Routes.debtDetail(row.debtId)
                    else Routes.planDetail(row.planId)
                )
            }
        }
    }
}

@Composable
private fun AccountsSection(state: HomeState, navController: NavHostController) {
    // Cuentas que lista la sección (los gadgets no se filtran por esto).
    val ids = state.prefs.accountIds
    val visibleAccounts = if (ids == null) state.accounts
    else state.accounts.filter { it.id in ids }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(
            "Cuentas",
            actionLabel = "+ Nueva cuenta",
            onAction = { navController.navigate(Routes.NEW_ACCOUNT) },
        )
        when {
            state.accounts.isEmpty() -> EmptyState(
                icon = Lucide.Wallet,
                title = "Sin cuentas todavía",
                description = "Crea tu primera cuenta o caja para empezar a registrar movimientos.",
                ctaLabel = "Crear cuenta",
                onCta = { navController.navigate(Routes.NEW_ACCOUNT) },
            )

            visibleAccounts.isEmpty() -> ChangeboxCard(corner = 16) {
                Text(
                    "Todas las cuentas están ocultas. Elige cuáles mostrar en «Personalizar Inicio».",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                )
            }

            else -> visibleAccounts.forEach { account ->
                AccountCard(account) {
                    navController.navigate(Routes.accountDetail(account.id))
                }
            }
        }
    }
}

fun CurrencyEntity.toDisplayLocal() = DisplayCurrencyOf(code, decimalPlaces)

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
