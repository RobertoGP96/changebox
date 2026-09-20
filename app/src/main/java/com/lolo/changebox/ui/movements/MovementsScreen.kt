package com.lolo.changebox.ui.movements

import android.content.Intent
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Lucide
import com.lolo.changebox.data.atStartOfDayMillis
import com.lolo.changebox.data.local.dao.TxJoinRow
import com.lolo.changebox.data.local.entity.CurrencyEntity
import com.lolo.changebox.data.repo.toTxRow
import com.lolo.changebox.di.AppContainer
import com.lolo.changebox.di.appViewModel
import com.lolo.changebox.domain.MetricCurrency
import com.lolo.changebox.domain.MinorCurrencyOf
import com.lolo.changebox.domain.TransactionKind
import com.lolo.changebox.domain.convertMinor
import com.lolo.changebox.domain.fmtMinor
import com.lolo.changebox.ui.Routes
import com.lolo.changebox.ui.common.ChangeboxCard
import com.lolo.changebox.ui.common.ChangeboxSelect
import com.lolo.changebox.ui.common.EmptyState
import com.lolo.changebox.ui.common.GradientBar
import com.lolo.changebox.ui.common.LocalToast
import com.lolo.changebox.ui.common.ScreenHeader
import com.lolo.changebox.ui.common.TxList
import com.lolo.changebox.ui.common.contentWidth
import com.lolo.changebox.ui.home.toDisplayLocal
import com.lolo.changebox.ui.theme.ChangeboxColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Movimientos: filtros por mes (navegable / todo el historial), cuenta,
// categoría y tipo; totales convertidos a base; reporte de gastos por
// categoría y export CSV vía hoja de compartir — port de movimientos/page.

data class TxFilters(
    val month: YearMonth? = YearMonth.now(), // null = todo el historial
    val accountId: String? = null,
    val categoryId: String? = null,
    val kind: String? = null,
)

data class MovementsState(
    val loaded: Boolean = false,
    val rows: List<TxJoinRow> = emptyList(),
    val accounts: List<Pair<String, String>> = emptyList(),
    val categories: List<Pair<String, String>> = emptyList(),
    val base: CurrencyEntity? = null,
    val rates: Map<String, Long> = emptyMap(),
)

class MovementsViewModel(private val container: AppContainer) : ViewModel() {

    val filters = MutableStateFlow(TxFilters())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rowsFlow = filters.flatMapLatest { f ->
        val start = f.month?.atDay(1)?.atStartOfDayMillis()
        val end = f.month?.plusMonths(1)?.atDay(1)?.atStartOfDayMillis()
        container.ledger.filteredRowsFlow(start, end, f.accountId, f.categoryId, f.kind)
    }

    val state = combine(
        rowsFlow,
        container.accounts.activeAccountsFlow(),
        container.db.catalogDao().activeCategoriesFlow(),
        container.db.catalogDao().baseCurrencyFlow(),
        container.rates.latestRatesByCurrencyFlow(),
    ) { rows, accounts, categories, base, rates ->
        MovementsState(
            loaded = true,
            rows = rows,
            accounts = accounts.map { it.id to it.name },
            categories = categories.map { it.id to it.name },
            base = base,
            rates = rates.mapValues { it.value.rateScaled },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MovementsState())

    fun exportCsv(
        context: android.content.Context,
        onReady: (java.io.File) -> Unit,
        onError: (String) -> Unit,
    ) {
        val f = filters.value
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                val start = f.month?.atDay(1)?.atStartOfDayMillis()
                val end = f.month?.plusMonths(1)?.atDay(1)?.atStartOfDayMillis()
                val csv = container.ledger.exportCsv(start, end, f.accountId, f.categoryId, f.kind)
                val suffix = f.month?.toString() ?: "todos"
                val file = withContext(Dispatchers.IO) {
                    val dir = java.io.File(appContext.cacheDir, "exports").apply { mkdirs() }
                    java.io.File(dir, "movimientos-$suffix.csv").apply { writeText(csv) }
                }
                onReady(file)
            } catch (e: Exception) {
                onError("No se pudo exportar el CSV")
            }
        }
    }
}

private val MONTH_LABEL_FMT = Locale.forLanguageTag("es")

private fun monthLabel(month: YearMonth?): String {
    if (month == null) return "Todo el historial"
    val name = month.month.getDisplayName(TextStyle.FULL, MONTH_LABEL_FMT)
    return "${name.replaceFirstChar { it.uppercase(MONTH_LABEL_FMT) }} ${month.year}"
}

@Composable
fun MovementsScreen(navController: NavHostController) {
    val vm = appViewModel { MovementsViewModel(it) }
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    val toast = LocalToast.current

    // Totales y reporte por categoría convertidos a base con la tasa vigente
    // (sobre las mismas filas cargadas, igual que la web).
    var expenseMinor = 0L
    var incomeMinor = 0L
    val byCategory = linkedMapOf<String, Long>()
    val missingRates = mutableSetOf<String>()
    val base = state.base
    if (base != null) {
        for (tx in state.rows) {
            if (tx.kind != "EXPENSE" && tx.kind != "INCOME") continue
            var converted = tx.amountMinor
            if (tx.currencyId != base.id) {
                val rate = state.rates[tx.currencyId]
                if (rate == null) {
                    missingRates.add(tx.currencyCode)
                    continue
                }
                converted = convertMinor(
                    tx.amountMinor,
                    MetricCurrency(tx.currencyId, tx.currencyCode, tx.currencyDecimals),
                    MinorCurrencyOf(base.decimalPlaces),
                    rate,
                )
            }
            if (tx.kind == "EXPENSE") {
                expenseMinor += converted
                val name = tx.categoryName ?: "Sin categoría"
                byCategory[name] = (byCategory[name] ?: 0L) + converted
            } else {
                incomeMinor += converted
            }
        }
    }
    val topCategories = byCategory.entries.sortedByDescending { it.value }.take(8)
    val maxCategory = topCategories.firstOrNull()?.value ?: 0L

    val rows = state.rows.map { toTxRow(it) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(title = "Movimientos", onBack = { navController.popBackStack() })

        Column(
            Modifier
                .contentWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Navegador de mes
            ChangeboxCard(corner = 16) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MonthArrow(Lucide.ChevronLeft, "Mes anterior", enabled = filters.month != null) {
                        vm.filters.value = filters.copy(month = filters.month?.minusMonths(1))
                    }
                    Text(
                        monthLabel(filters.month),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                // Alternar entre mes y todo el historial
                                vm.filters.value = filters.copy(
                                    month = if (filters.month == null) YearMonth.now() else null
                                )
                            },
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    MonthArrow(Lucide.ChevronRight, "Mes siguiente", enabled = filters.month != null) {
                        vm.filters.value = filters.copy(month = filters.month?.plusMonths(1))
                    }
                }
            }

            // Filtros cuenta/categoría/tipo + export
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    ChangeboxSelect(
                        options = listOf<Pair<String, String>?>(null) + state.accounts,
                        selected = state.accounts.find { it.first == filters.accountId },
                        onSelect = { vm.filters.value = filters.copy(accountId = it?.first) },
                        display = { it?.second ?: "Todas las cuentas" },
                        placeholder = "Cuenta",
                    )
                }
                Box(Modifier.weight(1f)) {
                    ChangeboxSelect(
                        options = listOf<Pair<String, String>?>(null) + state.categories,
                        selected = state.categories.find { it.first == filters.categoryId },
                        onSelect = { vm.filters.value = filters.copy(categoryId = it?.first) },
                        display = { it?.second ?: "Todas las categorías" },
                        placeholder = "Categoría",
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    ChangeboxSelect(
                        options = listOf<String?>(null) + TransactionKind.entries.map { it.name },
                        selected = filters.kind,
                        onSelect = { vm.filters.value = filters.copy(kind = it) },
                        display = { kind ->
                            kind?.let { TransactionKind.valueOf(it).labelEs } ?: "Todos los tipos"
                        },
                        placeholder = "Tipo",
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(ChangeboxColors.extended.chip)
                        .clickable {
                            vm.exportCsv(
                                context,
                                onReady = { file ->
                                    val uri = FileProvider.getUriForFile(
                                        context, "${context.packageName}.fileprovider", file,
                                    )
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(intent, "Exportar movimientos")
                                    )
                                },
                                onError = { toast(it) },
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Lucide.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        "CSV",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Totales
            if (base != null && (expenseMinor > 0 || incomeMinor > 0)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TotalCard("GASTOS", fmtMinor(expenseMinor, base.toDisplayLocal()),
                        MaterialTheme.colorScheme.error, Modifier.weight(1f))
                    TotalCard("INGRESOS", fmtMinor(incomeMinor, base.toDisplayLocal()),
                        ChangeboxColors.extended.ok, Modifier.weight(1f))
                }
            }

            if (missingRates.isNotEmpty()) {
                Text(
                    "Los montos en ${missingRates.joinToString(", ")} no se incluyen en los totales por falta de tasa.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Gastos por categoría
            if (base != null && topCategories.isNotEmpty()) {
                ChangeboxCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Gastos por categoría",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        topCategories.forEach { (name, total) ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row {
                                    Text(
                                        name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        fmtMinor(total, base.toDisplayLocal()),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                GradientBar(total.toFloat() / maxOf(1L, maxCategory))
                            }
                        }
                    }
                }
            }

            if (state.loaded && rows.isEmpty()) {
                EmptyState(
                    icon = Lucide.History,
                    title = "Sin movimientos",
                    description = "No hay movimientos con estos filtros. Prueba otro mes o registra uno nuevo.",
                    ctaLabel = "Registrar",
                    onCta = { navController.navigate(Routes.register()) },
                )
            } else {
                TxList(rows) { id -> navController.navigate(Routes.movementDetail(id)) }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MonthArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.secondary.copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun TotalCard(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    ChangeboxCard(modifier = modifier, corner = 16) {
        Column(Modifier.padding(14.dp)) {
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        }
    }
}

