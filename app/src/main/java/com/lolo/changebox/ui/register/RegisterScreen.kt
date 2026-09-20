package com.lolo.changebox.ui.register

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.composables.icons.lucide.ArrowRightLeft
import com.composables.icons.lucide.Lucide
import com.lolo.changebox.data.ActionResult
import com.lolo.changebox.data.atNoonMillis
import com.lolo.changebox.data.local.entity.CategoryEntity
import com.lolo.changebox.data.local.entity.CurrencyEntity
import com.lolo.changebox.data.repo.DenomLineInput
import com.lolo.changebox.data.repo.IncomeExpenseInput
import com.lolo.changebox.data.repo.RateDirection
import com.lolo.changebox.data.repo.TransferInput
import com.lolo.changebox.di.AppContainer
import com.lolo.changebox.di.appViewModel
import com.lolo.changebox.domain.DisplayCurrencyOf
import com.lolo.changebox.domain.MinorCurrencyOf
import com.lolo.changebox.domain.PairRateLite
import com.lolo.changebox.domain.RATE_SCALE
import com.lolo.changebox.domain.buildPairMap
import com.lolo.changebox.domain.convertMinor
import com.lolo.changebox.domain.convertMinorInverse
import com.lolo.changebox.domain.countedTotalMinor
import com.lolo.changebox.domain.CountableDenomination
import com.lolo.changebox.domain.fmtMinor
import com.lolo.changebox.domain.invertRateScaled
import com.lolo.changebox.domain.minorToAmountInput
import com.lolo.changebox.domain.parseAmountToMinor
import com.lolo.changebox.domain.resolveRateScaled
import com.lolo.changebox.ui.common.ChangeboxSelect
import com.lolo.changebox.ui.common.ChangeboxTextField
import com.lolo.changebox.ui.common.CounterDenomination
import com.lolo.changebox.ui.common.DateField
import com.lolo.changebox.ui.common.DenominationBreakdownField
import com.lolo.changebox.ui.common.ErrorBox
import com.lolo.changebox.ui.common.LabeledField
import com.lolo.changebox.ui.common.LocalToast
import com.lolo.changebox.ui.common.PrimaryButton
import com.lolo.changebox.ui.common.ScreenHeader
import com.lolo.changebox.ui.common.SegmentedTabs
import com.lolo.changebox.ui.common.contentWidth
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Registrar: gasto/ingreso/transferencia, port 1:1 de registrar/register-form
// incluida la operación multi-moneda (tasa prellenada desde los pares,
// invertible ⇄, con vista previa) y el desglose de denominaciones por lado.

data class AccountOption(
    val id: String,
    val name: String,
    val type: String,
    val currency: CurrencyEntity,
)

data class RegisterData(
    val loaded: Boolean = false,
    val accounts: List<AccountOption> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val currencies: List<CurrencyEntity> = emptyList(),
    val pairRates: List<PairRateLite> = emptyList(),
    val baseCurrencyId: String? = null,
    /** Denominaciones (con stock derivado) por cuenta CASH_BOX. */
    val cashBoxStock: Map<String, List<CounterDenomination>> = emptyMap(),
)

class RegisterViewModel(private val container: AppContainer) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val data = combine(
        container.accounts.activeAccountsFlow(),
        container.db.catalogDao().activeCategoriesFlow(),
        container.db.catalogDao().activeCurrenciesFlow(),
        container.rates.latestPairRatesLiteFlow(),
        container.db.catalogDao().baseCurrencyFlow(),
    ) { accounts, categories, currencies, pairs, base ->
        val currencyById = currencies.associateBy { it.id }
        Partial(
            accounts.mapNotNull { account ->
                val currency = currencyById[account.currencyId]
                    ?: return@mapNotNull null
                AccountOption(account.id, account.name, account.type, currency)
            },
            categories, currencies, pairs, base?.id,
        )
    }.flatMapLatest { partial ->
        val cashBoxes = partial.accounts.filter { it.type == "CASH_BOX" }
        if (cashBoxes.isEmpty()) {
            flowOf(partial to emptyMap())
        } else {
            combine(
                cashBoxes.map { box ->
                    container.accounts.denominationStockFlow(box.id).map { stock ->
                        // Igual que la web: activas o con stock, available>=0.
                        box.id to stock.lines
                            .filter { it.active || it.quantity > 0 }
                            .map {
                                CounterDenomination(
                                    id = it.denominationId,
                                    valueMinor = it.valueMinor,
                                    kind = it.kind,
                                    available = maxOf(0, it.quantity),
                                )
                            }
                    }
                }
            ) { entries -> partial to entries.filter { it.second.isNotEmpty() }.toMap() }
        }
    }.map { (partial, stock) ->
        RegisterData(
            loaded = true,
            accounts = partial.accounts,
            categories = partial.categories,
            currencies = partial.currencies,
            pairRates = partial.pairs,
            baseCurrencyId = partial.baseId,
            cashBoxStock = stock,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RegisterData())

    private data class Partial(
        val accounts: List<AccountOption>,
        val categories: List<CategoryEntity>,
        val currencies: List<CurrencyEntity>,
        val pairs: List<PairRateLite>,
        val baseId: String?,
    )

    fun submitIncomeExpense(input: IncomeExpenseInput, onResult: (ActionResult<String>) -> Unit) {
        viewModelScope.launch { onResult(container.ledger.registerIncomeExpense(input)) }
    }

    fun submitTransfer(input: TransferInput, onResult: (ActionResult<String>) -> Unit) {
        viewModelScope.launch { onResult(container.ledger.registerTransfer(input)) }
    }
}

private val RATE_DECIMALS = MinorCurrencyOf(4)

private fun safeMinor(text: String, currency: MinorCurrencyOf?): Long? {
    if (currency == null) return null
    return try {
        val minor = parseAmountToMinor(text, currency)
        if (minor > 0) minor else null
    } catch (e: Exception) {
        null
    }
}

@Composable
fun RegisterScreen(
    navController: NavHostController,
    initialTipo: String,
    initialCuenta: String,
) {
    val vm = appViewModel { RegisterViewModel(it) }
    val data by vm.data.collectAsStateWithLifecycle()
    val toast = LocalToast.current

    val validModes = listOf("gasto", "ingreso", "transferencia")
    var mode by rememberSaveable {
        mutableStateOf(if (initialTipo in validModes) initialTipo else "gasto")
    }
    var accountId by rememberSaveable { mutableStateOf(initialCuenta) }
    var counterAccountId by rememberSaveable { mutableStateOf("") }
    var originLines by remember { mutableStateOf(mapOf<String, Int>()) }
    var destLines by remember { mutableStateOf(mapOf<String, Int>()) }
    var amount by rememberSaveable { mutableStateOf("") }
    var counterAmount by rememberSaveable { mutableStateOf("") }
    // "" = misma moneda de la cuenta; otro id = operación multi-moneda
    var amountCurrencyId by rememberSaveable { mutableStateOf("") }
    var rate by rememberSaveable { mutableStateOf("") }
    var rateDirection by rememberSaveable { mutableStateOf(RateDirection.ACCOUNT_TO_AMOUNT.name) }
    var categoryId by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    // Cuenta por defecto en cuanto cargan los datos
    LaunchedEffect(data.loaded) {
        if (data.loaded && data.accounts.none { it.id == accountId }) {
            accountId = data.accounts.firstOrNull()?.id ?: ""
        }
    }

    val from = data.accounts.find { it.id == accountId }
    val to = data.accounts.find { it.id == counterAccountId }
    val crossCurrency = mode == "transferencia" && from != null && to != null &&
        from.currency.id != to.currency.id

    val kind = if (mode == "gasto") "EXPENSE" else "INCOME"
    val modeCategories = data.categories.filter { it.kind == kind }
    val pairMap = remember(data.pairRates) { buildPairMap(data.pairRates) }

    // Moneda en que se escribe el monto (solo ingreso/gasto)
    val accountCurrency = from?.currency
    val opCurrency = if (mode != "transferencia") {
        data.currencies.find { it.id == (amountCurrencyId.ifEmpty { accountCurrency?.id }) }
    } else {
        null
    }
    val crossCurrencyOp = accountCurrency != null && opCurrency != null &&
        opCurrency.id != accountCurrency.id

    // Prellena la tasa definida al cambiar el par monto↔cuenta, en la
    // dirección más legible (número ≥ 1). Editable después.
    val pairId = if (crossCurrencyOp) "${opCurrency!!.id}→${accountCurrency!!.id}" else ""
    LaunchedEffect(pairId) {
        if (pairId.isNotEmpty() && opCurrency != null && accountCurrency != null) {
            val toAccount = resolveRateScaled(
                pairMap, opCurrency.id, accountCurrency.id, data.baseCurrencyId,
            )
            val toAmount = resolveRateScaled(
                pairMap, accountCurrency.id, opCurrency.id, data.baseCurrencyId,
            )
            if (toAccount != null && (toAmount == null || toAccount >= RATE_SCALE)) {
                rateDirection = RateDirection.AMOUNT_TO_ACCOUNT.name
                rate = minorToAmountInput(toAccount, RATE_DECIMALS)
            } else if (toAmount != null) {
                rateDirection = RateDirection.ACCOUNT_TO_AMOUNT.name
                rate = minorToAmountInput(toAmount, RATE_DECIMALS)
            } else {
                rate = ""
            }
        }
    }

    // Vista previa del monto convertido a la moneda de la cuenta
    val convertedMinor: Long? = if (crossCurrencyOp && opCurrency != null && accountCurrency != null) {
        try {
            val opMinor = parseAmountToMinor(amount, MinorCurrencyOf(opCurrency.decimalPlaces))
            val rateScaled = parseAmountToMinor(rate, RATE_DECIMALS)
            if (opMinor > 0 && rateScaled > 0) {
                val minor = if (rateDirection == RateDirection.ACCOUNT_TO_AMOUNT.name) {
                    convertMinorInverse(
                        opMinor,
                        MinorCurrencyOf(opCurrency.decimalPlaces),
                        MinorCurrencyOf(accountCurrency.decimalPlaces),
                        rateScaled,
                    )
                } else {
                    convertMinor(
                        opMinor,
                        MinorCurrencyOf(opCurrency.decimalPlaces),
                        MinorCurrencyOf(accountCurrency.decimalPlaces),
                        rateScaled,
                    )
                }
                if (minor > 0) minor else null
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    } else {
        null
    }
    val convertedPreview = if (convertedMinor != null && accountCurrency != null) {
        fmtMinor(convertedMinor, DisplayCurrencyOf(accountCurrency.code, accountCurrency.decimalPlaces))
    } else {
        null
    }

    // Desglose de denominaciones: aplica cuando el lado es una caja CASH_BOX
    // con denominaciones. El monto a cuadrar va SIEMPRE en la moneda de la
    // cuenta afectada (convertido si la operación es multi-moneda).
    val originBoxDenoms = data.cashBoxStock[accountId]
    val destBoxDenoms = if (mode == "transferencia") data.cashBoxStock[counterAccountId] else null

    val originTarget = if (mode == "transferencia" || !crossCurrencyOp) {
        safeMinor(amount, from?.currency?.let { MinorCurrencyOf(it.decimalPlaces) })
    } else {
        convertedMinor
    }
    val destTarget = if (crossCurrency) {
        safeMinor(counterAmount, to?.currency?.let { MinorCurrencyOf(it.decimalPlaces) })
    } else {
        safeMinor(amount, from?.currency?.let { MinorCurrencyOf(it.decimalPlaces) })
    }

    val originOutflow = mode != "ingreso"
    val originBreakdownOk = originBoxDenoms.isNullOrEmpty() ||
        (originTarget != null && countedTotalMinor(
            originBoxDenoms.map { CountableDenomination(it.id, it.valueMinor) }, originLines,
        ) == originTarget)
    val destBreakdownOk = destBoxDenoms.isNullOrEmpty() ||
        (destTarget != null && countedTotalMinor(
            destBoxDenoms.map { CountableDenomination(it.id, it.valueMinor) }, destLines,
        ) == destTarget)

    fun linesPayload(lines: Map<String, Int>): List<DenomLineInput>? {
        val entries = lines.filter { it.value > 0 }
            .map { DenomLineInput(it.key, it.value) }
        return entries.ifEmpty { null }
    }

    fun flipRateDirection() {
        rateDirection = if (rateDirection == RateDirection.AMOUNT_TO_ACCOUNT.name) {
            RateDirection.ACCOUNT_TO_AMOUNT.name
        } else {
            RateDirection.AMOUNT_TO_ACCOUNT.name
        }
        // Reaprovecha la tasa escrita invirtiéndola; si no se puede, se limpia.
        rate = try {
            val rateScaled = parseAmountToMinor(rate, RATE_DECIMALS)
            if (rateScaled > 0) minorToAmountInput(invertRateScaled(rateScaled), RATE_DECIMALS) else ""
        } catch (e: Exception) {
            ""
        }
    }

    fun switchMode(next: String) {
        mode = next
        categoryId = ""
        originLines = emptyMap()
        destLines = emptyMap()
        error = null
    }

    fun submit() {
        saving = true
        error = null
        val occurredAt = date.atNoonMillis()
        val onResult: (ActionResult<String>) -> Unit = { result ->
            saving = false
            when (result) {
                is ActionResult.Success -> {
                    toast(
                        when (mode) {
                            "gasto" -> "Gasto registrado"
                            "ingreso" -> "Ingreso registrado"
                            else -> "Transferencia registrada"
                        }
                    )
                    navController.popBackStack(Routes_HOME, inclusive = false)
                }
                is ActionResult.Failure -> error = result.error
            }
        }
        if (mode == "transferencia") {
            vm.submitTransfer(
                TransferInput(
                    accountId = accountId,
                    counterAccountId = counterAccountId,
                    amount = amount,
                    counterAmount = if (crossCurrency) counterAmount else null,
                    note = note.trim().ifEmpty { null },
                    occurredAt = occurredAt,
                    denominationLines = if (!originBoxDenoms.isNullOrEmpty()) linesPayload(originLines) else null,
                    counterDenominationLines = if (!destBoxDenoms.isNullOrEmpty()) linesPayload(destLines) else null,
                ),
                onResult,
            )
        } else {
            vm.submitIncomeExpense(
                IncomeExpenseInput(
                    kind = kind,
                    accountId = accountId,
                    amount = amount,
                    amountCurrencyId = if (crossCurrencyOp) opCurrency?.id else null,
                    rate = if (crossCurrencyOp) rate else null,
                    rateDirection = if (crossCurrencyOp) RateDirection.valueOf(rateDirection) else null,
                    categoryId = categoryId.ifEmpty { null },
                    note = note.trim().ifEmpty { null },
                    occurredAt = occurredAt,
                    denominationLines = if (!originBoxDenoms.isNullOrEmpty()) linesPayload(originLines) else null,
                ),
                onResult,
            )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(title = "Registrar", onBack = { navController.popBackStack() })

        Column(
            Modifier
                .contentWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (data.loaded && data.accounts.isEmpty()) {
                Text(
                    "Primero crea una cuenta para poder registrar movimientos.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                return@Column
            }

            SegmentedTabs(
                options = listOf(
                    "gasto" to "Gasto",
                    "ingreso" to "Ingreso",
                    "transferencia" to "Transferencia",
                ),
                selectedKey = mode,
                onSelect = ::switchMode,
            )

            LabeledField(if (mode == "transferencia") "Cuenta de origen" else "Cuenta") {
                ChangeboxSelect(
                    options = data.accounts,
                    selected = from,
                    onSelect = {
                        accountId = it.id
                        originLines = emptyMap()
                    },
                    display = { "${it.name} · ${it.currency.code}" },
                    placeholder = "Elige cuenta",
                )
            }

            if (mode == "transferencia") {
                LabeledField("Cuenta de destino") {
                    ChangeboxSelect(
                        options = data.accounts.filter { it.id != accountId },
                        selected = to,
                        onSelect = {
                            counterAccountId = it.id
                            destLines = emptyMap()
                        },
                        display = { "${it.name} · ${it.currency.code}" },
                        placeholder = "Elige cuenta",
                    )
                }
            }

            LabeledField(
                buildString {
                    append("Monto")
                    if (mode == "transferencia" && from != null) {
                        append(" (${from.currency.code})")
                    } else if (opCurrency != null) {
                        append(" (${opCurrency.code})")
                    }
                },
                hint = if (mode != "transferencia" && !crossCurrencyOp && data.currencies.size > 1) {
                    "Puedes anotar el monto en otra moneda y se convertirá a la de la cuenta."
                } else {
                    null
                },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        ChangeboxTextField(amount, { amount = it }, placeholder = "0", decimal = true)
                    }
                    if (mode != "transferencia" && data.currencies.size > 1) {
                        Box(Modifier.width(112.dp)) {
                            ChangeboxSelect(
                                options = data.currencies,
                                selected = opCurrency,
                                onSelect = { amountCurrencyId = it.id },
                                display = { it.code },
                                placeholder = "Moneda",
                            )
                        }
                    }
                }
            }

            if (crossCurrencyOp && opCurrency != null && accountCurrency != null && from != null) {
                LabeledField(
                    "Tasa (1 " + if (rateDirection == RateDirection.AMOUNT_TO_ACCOUNT.name) {
                        "${opCurrency.code} = ? ${accountCurrency.code})"
                    } else {
                        "${accountCurrency.code} = ? ${opCurrency.code})"
                    },
                    hint = convertedPreview?.let { "Se registrará $it en «${from.name}»." }
                        ?: "La cuenta es en ${accountCurrency.code}: el monto se convertirá con esta tasa.",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            ChangeboxTextField(rate, { rate = it }, placeholder = "0", decimal = true)
                        }
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { flipRateDirection() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Lucide.ArrowRightLeft,
                                contentDescription = "Invertir la dirección de la tasa",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            if (!originBoxDenoms.isNullOrEmpty() && from != null) {
                DenominationBreakdownField(
                    title = if (originOutflow) "Sale de «${from.name}» (denominaciones)"
                    else "Entra en «${from.name}» (denominaciones)",
                    denominations = originBoxDenoms,
                    currency = DisplayCurrencyOf(from.currency.code, from.currency.decimalPlaces),
                    targetMinor = originTarget,
                    quantities = originLines,
                    onQtyChange = { originLines = it },
                    outflow = originOutflow,
                )
            }

            if (crossCurrency) {
                LabeledField(
                    "Monto recibido (${to?.currency?.code})",
                    hint = "Las cuentas usan monedas distintas: indica cuánto entra en destino.",
                ) {
                    ChangeboxTextField(counterAmount, { counterAmount = it }, placeholder = "0", decimal = true)
                }
            }

            if (!destBoxDenoms.isNullOrEmpty() && to != null) {
                DenominationBreakdownField(
                    title = "Entra en «${to.name}» (denominaciones)",
                    denominations = destBoxDenoms,
                    currency = DisplayCurrencyOf(to.currency.code, to.currency.decimalPlaces),
                    targetMinor = destTarget,
                    quantities = destLines,
                    onQtyChange = { destLines = it },
                    outflow = false,
                )
            }

            if (mode != "transferencia") {
                LabeledField("Categoría (opcional)") {
                    ChangeboxSelect(
                        options = listOf<CategoryEntity?>(null) + modeCategories,
                        selected = modeCategories.find { it.id == categoryId },
                        onSelect = { categoryId = it?.id ?: "" },
                        display = { it?.name ?: "Sin categoría" },
                        placeholder = "Sin categoría",
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledField("Fecha", Modifier.weight(1f)) {
                    DateField(date, { date = it }, maxToday = true)
                }
                LabeledField("Nota (opcional)", Modifier.weight(1f)) {
                    ChangeboxTextField(note, { if (it.length <= 200) note = it }, placeholder = "Detalle")
                }
            }

            error?.let { ErrorBox(it) }

            PrimaryButton(
                text = if (saving) "Guardando…" else "Guardar",
                onClick = ::submit,
                modifier = Modifier.fillMaxWidth(),
                large = true,
                enabled = !saving && accountId.isNotEmpty() && amount.isNotBlank() &&
                    !(crossCurrencyOp && rate.isBlank()) &&
                    !(mode == "transferencia" &&
                        (counterAccountId.isEmpty() || (crossCurrency && counterAmount.isBlank()))) &&
                    originBreakdownOk && destBreakdownOk,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

private const val Routes_HOME = "inicio"

