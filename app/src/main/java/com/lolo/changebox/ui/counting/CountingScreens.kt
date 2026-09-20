package com.lolo.changebox.ui.counting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.composables.icons.lucide.Banknote
import com.composables.icons.lucide.Calculator
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Coins
import com.composables.icons.lucide.Eraser
import com.composables.icons.lucide.Lucide
import com.lolo.changebox.data.ActionResult
import com.lolo.changebox.data.local.dao.CashCountWithAccount
import com.lolo.changebox.data.local.entity.AccountEntity
import com.lolo.changebox.data.local.entity.CurrencyEntity
import com.lolo.changebox.data.local.entity.DenominationEntity
import com.lolo.changebox.data.repo.AccountWithBalance
import com.lolo.changebox.data.repo.CashCountLineInput
import com.lolo.changebox.data.repo.CashCountRepository
import com.lolo.changebox.di.AppContainer
import com.lolo.changebox.di.appViewModel
import com.lolo.changebox.domain.AccountType
import com.lolo.changebox.domain.CountableDenomination
import com.lolo.changebox.domain.DisplayCurrencyOf
import com.lolo.changebox.domain.countedPieces
import com.lolo.changebox.domain.countedTotalMinor
import com.lolo.changebox.domain.fmtMinor
import com.lolo.changebox.domain.fmtSignedMinor
import com.lolo.changebox.domain.isCashLike
import com.lolo.changebox.ui.Routes
import com.lolo.changebox.ui.common.BadgeVariant
import com.lolo.changebox.ui.common.ChangeboxBadge
import com.lolo.changebox.ui.common.ChangeboxCard
import com.lolo.changebox.ui.common.ChangeboxTextField
import com.lolo.changebox.ui.common.CounterDenomination
import com.lolo.changebox.ui.common.DenominationCounter
import com.lolo.changebox.ui.common.EmptyState
import com.lolo.changebox.ui.common.ErrorBox
import com.lolo.changebox.ui.common.IconChip
import com.lolo.changebox.ui.common.LocalToast
import com.lolo.changebox.ui.common.PrimaryButton
import com.lolo.changebox.ui.common.ScreenHeader
import com.lolo.changebox.ui.common.SectionTitle
import com.lolo.changebox.ui.common.fmtShortDateTime
import com.lolo.changebox.ui.common.contentWidth
import com.lolo.changebox.ui.theme.ChangeboxColors
import com.lolo.changebox.ui.theme.getAccountIcon
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Conteo de efectivo (listado de Changeboxs + arqueos recientes), arqueo de una
// caja y calculadora libre — ports de conteo/page, conteo/[id] y calculadora.

// ── Listado /conteo ─────────────────────────────────────────────────────────

data class CountingState(
    val loaded: Boolean = false,
    val cashAccounts: List<AccountWithBalance> = emptyList(),
    val counts: List<CashCountWithAccount> = emptyList(),
)

class CountingViewModel(container: AppContainer) : ViewModel() {
    val state = combine(
        container.accounts.accountsWithBalancesFlow(),
        container.cashCounts.recentCountsFlow(10),
    ) { accounts, counts ->
        CountingState(
            loaded = true,
            cashAccounts = accounts.filter {
                runCatching { AccountType.valueOf(it.type).isCashLike() }.getOrDefault(false)
            },
            counts = counts,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CountingState())
}

@Composable
fun CountingScreen(navController: NavHostController) {
    val vm = appViewModel { CountingViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(title = "Conteo de efectivo", onBack = { navController.popBackStack() })

        Column(
            Modifier
                .contentWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ChangeboxCard(onClick = { navController.navigate(Routes.CALCULATOR) }) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IconChip(Lucide.Calculator)
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Calculadora de efectivo",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Cuenta billetes y monedas sin elegir cuenta",
                            fontSize = 11.5.sp,
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

            if (state.loaded && state.cashAccounts.isEmpty()) {
                EmptyState(
                    icon = Lucide.Banknote,
                    title = "Sin Changeboxs de efectivo",
                    description = "Crea una cuenta de tipo Efectivo o Caja (denominaciones) para poder hacer arqueos.",
                    ctaLabel = "Crear cuenta",
                    onCta = { navController.navigate(Routes.NEW_ACCOUNT) },
                )
            } else if (state.cashAccounts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Elige la caja a contar")
                    state.cashAccounts.forEach { account ->
                        val type = runCatching { AccountType.valueOf(account.type) }
                            .getOrDefault(AccountType.CASH)
                        ChangeboxCard(onClick = { navController.navigate(Routes.cashCount(account.id)) }) {
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
                                        "Saldo teórico: " + fmtMinor(
                                            account.balanceMinor,
                                            DisplayCurrencyOf(
                                                account.currency.code,
                                                account.currency.decimalPlaces,
                                            ),
                                        ),
                                        fontSize = 11.5.sp,
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
                }
            }

            if (state.counts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Arqueos recientes")
                    state.counts.forEach { item ->
                        val display = DisplayCurrencyOf(item.currencyCode, item.currencyDecimals)
                        ChangeboxCard(corner = 16) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${item.accountName} · ${fmtMinor(item.count.totalMinor, display)}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        fmtShortDateTime(item.count.countedAt),
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (item.count.differenceMinor == 0L) {
                                    ChangeboxBadge("Cuadra", BadgeVariant.OK)
                                } else {
                                    ChangeboxBadge(
                                        fmtSignedMinor(item.count.differenceMinor, display),
                                        if (item.count.differenceMinor > 0) BadgeVariant.WARN
                                        else BadgeVariant.DANGER,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Arqueo /conteo/[id] ─────────────────────────────────────────────────────

data class CashCountState(
    val loaded: Boolean = false,
    val account: AccountEntity? = null,
    val currency: CurrencyEntity? = null,
    val denominations: List<DenominationEntity> = emptyList(),
    val expectedMinor: Long = 0,
)

class CashCountViewModel(
    private val container: AppContainer,
    private val accountId: String,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state = container.accounts.accountFlow(accountId).flatMapLatest { account ->
        if (account == null) {
            flowOf(CashCountState(loaded = true))
        } else {
            combine(
                container.db.catalogDao().currencyFlow(account.currencyId),
                container.db.catalogDao().activeDenominationsFlow(account.currencyId),
                container.accounts.accountBalanceFlow(accountId),
            ) { currency, denominations, balance ->
                CashCountState(true, account, currency, denominations, balance)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CashCountState())

    fun save(
        note: String?,
        createAdjustment: Boolean,
        lines: List<CashCountLineInput>,
        onResult: (ActionResult<CashCountRepository.CountOutcome>) -> Unit,
    ) {
        viewModelScope.launch {
            onResult(
                container.cashCounts.createCashCount(accountId, note, createAdjustment, lines)
            )
        }
    }
}

@Composable
fun CashCountScreen(navController: NavHostController, accountId: String) {
    val vm = appViewModel(key = "arqueo-$accountId") { CashCountViewModel(it, accountId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current

    var quantities by remember { mutableStateOf(mapOf<String, Int>()) }
    var note by remember { mutableStateOf("") }
    var createAdjustment by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val account = state.account
    val currency = state.currency
    val display = currency?.let { DisplayCurrencyOf(it.code, it.decimalPlaces) }

    val totalMinor = countedTotalMinor(
        state.denominations.map { CountableDenomination(it.id, it.valueMinor) },
        quantities,
    )
    val differenceMinor = totalMinor - state.expectedMinor

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(
            title = "Arqueo · ${account?.name ?: ""}",
            onBack = { navController.popBackStack() },
        ) {
            if (display != null) {
                Text(
                    "Saldo teórico: ${fmtMinor(state.expectedMinor, display)}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        if (account == null || currency == null || display == null) return@Column

        Column(
            Modifier
                .contentWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.denominations.isEmpty()) {
                ChangeboxCard(corner = 16) {
                    Text(
                        "La moneda ${currency.code} no tiene denominaciones configuradas (es saldo digital), así que no admite arqueo físico.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                return@Column
            }

            DenominationCounter(
                denominations = state.denominations.map {
                    CounterDenomination(it.id, it.valueMinor, it.kind)
                },
                quantities = quantities,
                onQtyChange = { id, qty -> quantities = quantities + (id to qty) },
                currency = display,
            )

            ChangeboxCard {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Total contado",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            fmtMinor(totalMinor, display),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    HorizontalDivider(
                        Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Teórico: ${fmtMinor(state.expectedMinor, display)}",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (differenceMinor == 0L) {
                            ChangeboxBadge("Cuadra", BadgeVariant.OK)
                        } else {
                            ChangeboxBadge(
                                (if (differenceMinor > 0) "Sobra " else "Falta ") +
                                    fmtMinor(
                                        if (differenceMinor < 0) -differenceMinor else differenceMinor,
                                        display,
                                    ),
                                if (differenceMinor > 0) BadgeVariant.WARN else BadgeVariant.DANGER,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(13.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = createAdjustment, onCheckedChange = { createAdjustment = it })
                Text(
                    buildString {
                        append("Registrar ajuste por la diferencia")
                        if (differenceMinor != 0L) {
                            append(" (${fmtSignedMinor(differenceMinor, display)})")
                        }
                    },
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ChangeboxTextField(
                note,
                { if (it.length <= 200) note = it },
                placeholder = "Nota (opcional)",
            )

            error?.let { ErrorBox(it) }

            PrimaryButton(
                text = if (saving) "Guardando…" else "Guardar arqueo",
                onClick = {
                    saving = true
                    error = null
                    vm.save(
                        note.trim().ifEmpty { null },
                        createAdjustment,
                        state.denominations.map {
                            CashCountLineInput(it.id, quantities[it.id] ?: 0)
                        },
                    ) { result ->
                        saving = false
                        when (result) {
                            is ActionResult.Success -> {
                                toast("Arqueo guardado")
                                navController.popBackStack()
                            }
                            is ActionResult.Failure -> error = result.error
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                large = true,
                enabled = !saving,
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Calculadora /calculadora ────────────────────────────────────────────────

data class CalculatorCurrency(
    val id: String,
    val code: String,
    val name: String,
    val decimalPlaces: Int,
    val denominations: List<DenominationEntity>,
)

class CalculatorViewModel(container: AppContainer) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val currencies = container.db.catalogDao().activeCurrenciesFlow()
        .flatMapLatest { currencies ->
            if (currencies.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    currencies.map { currency ->
                        container.db.catalogDao().activeDenominationsFlow(currency.id)
                            .flatMapLatest { denominations ->
                                flowOf(
                                    CalculatorCurrency(
                                        currency.id, currency.code, currency.name,
                                        currency.decimalPlaces, denominations,
                                    )
                                )
                            }
                    }
                ) { entries -> entries.filter { it.denominations.isNotEmpty() } }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun CalculatorScreen(navController: NavHostController) {
    val vm = appViewModel { CalculatorViewModel(it) }
    val currencies by vm.currencies.collectAsStateWithLifecycle()

    var currencyId by remember { mutableStateOf<String?>(null) }
    // Las cantidades se guardan POR MONEDA: cambiar de divisa no borra el
    // conteo en curso de la otra.
    var quantitiesByCurrency by remember {
        mutableStateOf(mapOf<String, Map<String, Int>>())
    }

    val currency = currencies.find { it.id == currencyId } ?: currencies.firstOrNull()
    val quantities = currency?.let { quantitiesByCurrency[it.id] } ?: emptyMap()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(
            title = "Calculadora de efectivo",
            onBack = { navController.popBackStack() },
        ) {
            Text(
                "Cuenta billetes y monedas sin elegir cuenta; no se guarda nada.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.5.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Column(
            Modifier
                .contentWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (currencies.isEmpty()) {
                EmptyState(
                    icon = Lucide.Coins,
                    title = "Sin denominaciones",
                    description = "Ninguna moneda tiene billetes o monedas configurados. Añádelos para poder contar efectivo.",
                    ctaLabel = "Ir a Monedas",
                    onCta = { navController.navigate(Routes.CURRENCIES) },
                )
                return@Column
            }

            if (currencies.size > 1) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    currencies.forEach { option ->
                        val selected = option.id == currency?.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (selected) ChangeboxColors.extended.chip
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(999.dp),
                                )
                                .clickable { currencyId = option.id }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                option.code,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (currency != null) {
                val display = DisplayCurrencyOf(currency.code, currency.decimalPlaces)
                val totalMinor = countedTotalMinor(
                    currency.denominations.map { CountableDenomination(it.id, it.valueMinor) },
                    quantities,
                )
                val pieces = countedPieces(quantities)

                ChangeboxCard {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "TOTAL CONTADO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.6.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                fmtMinor(totalMinor, display),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                if (pieces == 1) "1 pieza" else "$pieces piezas",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable(enabled = pieces > 0) {
                                    quantitiesByCurrency =
                                        quantitiesByCurrency + (currency.id to emptyMap())
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Lucide.Eraser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = if (pieces > 0) 1f else 0.4f),
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                "Limpiar",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = if (pieces > 0) 1f else 0.4f),
                            )
                        }
                    }
                }

                DenominationCounter(
                    denominations = currency.denominations.map {
                        CounterDenomination(it.id, it.valueMinor, it.kind)
                    },
                    quantities = quantities,
                    onQtyChange = { id, qty ->
                        quantitiesByCurrency = quantitiesByCurrency +
                            (currency.id to (quantities + (id to qty)))
                    },
                    currency = display,
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

