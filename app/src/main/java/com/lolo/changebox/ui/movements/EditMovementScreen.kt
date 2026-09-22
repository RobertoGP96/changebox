package com.lolo.changebox.ui.movements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.lolo.changebox.data.ActionResult
import com.lolo.changebox.data.atCurrentTimeMillis
import com.lolo.changebox.data.local.dao.TxDetailRow
import com.lolo.changebox.data.local.entity.CategoryEntity
import com.lolo.changebox.data.repo.DenomLineInput
import com.lolo.changebox.data.repo.UpdateTransactionInput
import com.lolo.changebox.data.toLocalDate
import com.lolo.changebox.di.AppContainer
import com.lolo.changebox.di.appViewModel
import com.lolo.changebox.domain.CountableDenomination
import com.lolo.changebox.domain.DisplayCurrencyOf
import com.lolo.changebox.domain.MinorCurrencyOf
import com.lolo.changebox.domain.MoneyException
import com.lolo.changebox.domain.TransactionKind
import com.lolo.changebox.domain.countedTotalMinor
import com.lolo.changebox.domain.minorToAmountInput
import com.lolo.changebox.domain.parseAmountToMinor
import com.lolo.changebox.ui.common.ChangeboxCard
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
import com.lolo.changebox.ui.common.contentWidth
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Editar movimiento: monto, monto del otro lado, categoría, fecha, nota y
// desglose de denominaciones (que REEMPLAZA al anterior). Port de
// movimientos/[id]/editar/edit-transaction-form.tsx. El tipo y las cuentas NO
// se cambian: para eso se elimina y se registra de nuevo.

data class EditMovementData(
    val loaded: Boolean = false,
    val tx: TxDetailRow? = null,
    /** Cantidades ya guardadas, por lado (accountId → denominationId → qty). */
    val savedLines: Map<String, Map<String, Int>> = emptyMap(),
    val categories: List<CategoryEntity> = emptyList(),
    val originDenoms: List<CounterDenomination> = emptyList(),
    val destDenoms: List<CounterDenomination> = emptyList(),
    /** Nació de un abono o de una cuota: al cambiar el monto se ajusta la deuda. */
    val isLinked: Boolean = false,
)

class EditMovementViewModel(
    private val container: AppContainer,
    private val txId: String,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val data = container.ledger.txDetailFlow(txId).flatMapLatest { tx ->
        if (tx == null) {
            flowOf(EditMovementData(loaded = true))
        } else {
            combine(
                container.ledger.txDenominationLinesFlow(txId),
                container.db.catalogDao().activeCategoriesFlow(),
                boxDenomsFlow(tx.accountId, tx.accountType),
                boxDenomsFlow(tx.counterAccountId, tx.counterAccountType),
                combine(
                    container.ledger.debtLinkFlow(txId),
                    container.ledger.planLinkFlow(txId),
                ) { debt, plan -> debt != null || plan != null },
            ) { lines, categories, originDenoms, destDenoms, isLinked ->
                EditMovementData(
                    loaded = true,
                    tx = tx,
                    savedLines = lines
                        .groupBy { it.accountId }
                        .mapValues { (_, rows) ->
                            rows.associate { it.denominationId to it.quantity }
                        },
                    // La categoría debe ser del mismo tipo que el movimiento.
                    categories = categories.filter { it.kind == tx.kind },
                    originDenoms = originDenoms,
                    destDenoms = destDenoms,
                    isLinked = isLinked,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditMovementData())

    /** Denominaciones con stock del lado, solo si ese lado es una caja. */
    private fun boxDenomsFlow(accountId: String?, type: String?) =
        if (accountId == null || type != "CASH_BOX") {
            flowOf(emptyList())
        } else {
            container.accounts.denominationStockFlow(accountId).map { stock ->
                // Igual que la web: activas o con stock, available >= 0.
                stock.lines
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

    fun save(input: UpdateTransactionInput, onResult: (ActionResult<String>) -> Unit) {
        viewModelScope.launch { onResult(container.ledger.updateTransaction(input)) }
    }
}

@Composable
fun EditMovementScreen(navController: NavHostController, txId: String) {
    val vm = appViewModel(key = "editar-$txId") { EditMovementViewModel(it, txId) }
    val data by vm.data.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val tx = data.tx

    var amount by remember { mutableStateOf("") }
    var counterAmount by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var note by remember { mutableStateOf("") }
    var originLines by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var destLines by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val accountCurrency = tx?.let { MinorCurrencyOf(it.currencyDecimals) }

    // El movimiento conserva su HORA: la edición solo cambia el día.
    val originalTime = remember(tx?.id) {
        tx?.let {
            java.time.Instant.ofEpochMilli(it.occurredAt)
                .atZone(ZoneId.systemDefault()).toLocalTime()
        } ?: LocalTime.NOON
    }

    // La moneda del otro lado sigue el MISMO criterio que el repositorio: en
    // una transferencia se deriva de la cuenta destino (las viejas tienen
    // counterCurrencyId nulo); en ingreso/gasto, de la moneda de la operación.
    val isTransfer = tx?.kind == TransactionKind.TRANSFER.name
    val counterCurrencyCode: String?
    val counterCurrencyDecimals: Int?
    if (tx == null) {
        counterCurrencyCode = null
        counterCurrencyDecimals = null
    } else if (isTransfer) {
        val differs = tx.counterAccountCurrencyId != null &&
            tx.counterAccountCurrencyId != tx.currencyId
        counterCurrencyCode = if (differs) tx.counterAccountCurrencyCode else null
        counterCurrencyDecimals = if (differs) tx.counterAccountCurrencyDecimals else null
    } else {
        val differs = tx.counterCurrencyId != null && tx.counterCurrencyId != tx.currencyId
        counterCurrencyCode = if (differs) tx.counterCurrencyCode else null
        counterCurrencyDecimals = if (differs) tx.counterCurrencyDecimals else null
    }
    val cross = counterCurrencyCode != null && counterCurrencyDecimals != null

    // Rellenar el formulario con lo guardado (una vez por movimiento).
    LaunchedEffect(tx?.id, data.savedLines) {
        val current = tx ?: return@LaunchedEffect
        amount = minorToAmountInput(current.amountMinor, MinorCurrencyOf(current.currencyDecimals))
        counterAmount = current.counterAmountMinor?.let {
            minorToAmountInput(it, MinorCurrencyOf(counterCurrencyDecimals ?: current.currencyDecimals))
        }.orEmpty()
        categoryId = current.categoryId
        date = current.occurredAt.toLocalDate()
        note = current.note.orEmpty()
        originLines = data.savedLines[current.accountId].orEmpty()
        destLines = current.counterAccountId?.let { data.savedLines[it] }.orEmpty()
    }

    fun safeMinor(text: String, currency: MinorCurrencyOf?): Long? {
        if (currency == null) return null
        return try {
            parseAmountToMinor(text, currency)
        } catch (e: MoneyException) {
            null
        }
    }

    val originTarget = safeMinor(amount, accountCurrency)
    val destTarget = if (cross) {
        safeMinor(counterAmount, MinorCurrencyOf(counterCurrencyDecimals!!))
    } else {
        originTarget
    }

    // Contar rellena el monto (drivesAmount), igual que en la web.
    fun totalInput(denoms: List<CounterDenomination>, lines: Map<String, Int>, decimals: Int): String {
        val total = countedTotalMinor(
            denoms.map { CountableDenomination(it.id, it.valueMinor) }, lines,
        )
        return if (total > 0) minorToAmountInput(total, MinorCurrencyOf(decimals)) else ""
    }

    val originOk = data.originDenoms.isEmpty() || (
        originTarget != null && countedTotalMinor(
            data.originDenoms.map { CountableDenomination(it.id, it.valueMinor) }, originLines,
        ) == originTarget
        )
    val destOk = data.destDenoms.isEmpty() || (
        destTarget != null && countedTotalMinor(
            data.destDenoms.map { CountableDenomination(it.id, it.valueMinor) }, destLines,
        ) == destTarget
        )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(
            title = "Editar movimiento",
            onBack = { navController.popBackStack() },
        )

        Column(
            Modifier
                .contentWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (tx == null) {
                if (data.loaded) ErrorBox("Movimiento no encontrado")
                return@Column
            }

            val kindLabel = runCatching { TransactionKind.valueOf(tx.kind).labelEs }
                .getOrDefault(tx.kind)

            ChangeboxCard(corner = 16) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        buildString {
                            append(kindLabel)
                            append(" · ")
                            if (isTransfer && tx.counterAccountName != null) {
                                append("${tx.accountName} → ${tx.counterAccountName}")
                            } else {
                                append(tx.accountName)
                            }
                        },
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "El tipo y las cuentas no se cambian: si te equivocaste de cuenta, " +
                            "elimina el movimiento y regístralo de nuevo.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (data.isLinked) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Este movimiento nació de un abono o cuota: al cambiar el monto " +
                                "se actualiza también el pendiente de la deuda.",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Los desgloses van ANTES del monto que rellenan.
            if (data.originDenoms.isNotEmpty()) {
                DenominationBreakdownField(
                    title = if (tx.kind == TransactionKind.INCOME.name) {
                        "Entra en «${tx.accountName}» (denominaciones)"
                    } else {
                        "Sale de «${tx.accountName}» (denominaciones)"
                    },
                    denominations = data.originDenoms,
                    currency = DisplayCurrencyOf(tx.currencyCode, tx.currencyDecimals),
                    targetMinor = originTarget,
                    quantities = originLines,
                    onQtyChange = {
                        originLines = it
                        amount = totalInput(data.originDenoms, it, tx.currencyDecimals)
                    },
                    outflow = tx.kind != TransactionKind.INCOME.name,
                    drivesAmount = true,
                )
            }

            if (!cross && data.destDenoms.isNotEmpty() && tx.counterAccountName != null) {
                DenominationBreakdownField(
                    title = "Entra en «${tx.counterAccountName}» (denominaciones)",
                    denominations = data.destDenoms,
                    currency = DisplayCurrencyOf(tx.currencyCode, tx.currencyDecimals),
                    targetMinor = destTarget,
                    quantities = destLines,
                    onQtyChange = {
                        destLines = it
                        amount = totalInput(data.destDenoms, it, tx.currencyDecimals)
                    },
                    outflow = false,
                    drivesAmount = true,
                )
            }

            LabeledField(
                if (isTransfer) "Monto enviado (${tx.currencyCode})"
                else "Monto (${tx.currencyCode})"
            ) {
                ChangeboxTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    placeholder = "0",
                    decimal = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (cross && data.destDenoms.isNotEmpty() && tx.counterAccountName != null) {
                DenominationBreakdownField(
                    title = "Entra en «${tx.counterAccountName}» (denominaciones)",
                    denominations = data.destDenoms,
                    currency = DisplayCurrencyOf(counterCurrencyCode!!, counterCurrencyDecimals!!),
                    targetMinor = destTarget,
                    quantities = destLines,
                    onQtyChange = {
                        destLines = it
                        counterAmount = totalInput(data.destDenoms, it, counterCurrencyDecimals)
                    },
                    outflow = false,
                    drivesAmount = true,
                )
            }

            if (cross) {
                LabeledField(
                    if (isTransfer) "Monto recibido ($counterCurrencyCode)"
                    else "Monto original ($counterCurrencyCode)",
                    hint = if (isTransfer) {
                        "Lo que entra en la cuenta de destino; la tasa implícita se recalcula."
                    } else {
                        "La operación se anotó en $counterCurrencyCode; la tasa implícita se recalcula."
                    },
                ) {
                    ChangeboxTextField(
                        value = counterAmount,
                        onValueChange = { counterAmount = it },
                        placeholder = "0",
                        decimal = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (!isTransfer) {
                LabeledField("Categoría (opcional)") {
                    ChangeboxSelect(
                        options = listOf<CategoryEntity?>(null) + data.categories,
                        selected = data.categories.firstOrNull { it.id == categoryId },
                        onSelect = { categoryId = it?.id },
                        display = { it?.name ?: "Sin categoría" },
                        placeholder = "Sin categoría",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            LabeledField("Fecha") {
                DateField(
                    value = date,
                    onChange = { date = it },
                    maxToday = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            LabeledField("Nota (opcional)") {
                ChangeboxTextField(
                    value = note,
                    onValueChange = { if (it.length <= 200) note = it },
                    placeholder = "Detalle",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            error?.let { ErrorBox(it) }

            PrimaryButton(
                text = if (saving) "Guardando…" else "Guardar cambios",
                large = true,
                enabled = !saving && amount.isNotBlank() &&
                    (!cross || counterAmount.isNotBlank()) && originOk && destOk,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    saving = true
                    error = null
                    vm.save(
                        UpdateTransactionInput(
                            id = tx.id,
                            amount = amount,
                            counterAmount = counterAmount.takeIf { cross && it.isNotBlank() },
                            categoryId = if (isTransfer) null else categoryId,
                            note = note,
                            // Día elegido + la hora original del movimiento.
                            occurredAt = date.atCurrentTimeMillis(originalTime),
                            denominationLines = originLines
                                .filter { it.value > 0 }
                                .map { DenomLineInput(it.key, it.value) }
                                .takeIf { data.originDenoms.isNotEmpty() },
                            counterDenominationLines = destLines
                                .filter { it.value > 0 }
                                .map { DenomLineInput(it.key, it.value) }
                                .takeIf { data.destDenoms.isNotEmpty() },
                        )
                    ) { result ->
                        saving = false
                        when (result) {
                            is ActionResult.Success -> {
                                toast("Movimiento actualizado")
                                navController.popBackStack()
                            }
                            is ActionResult.Failure -> error = result.error
                        }
                    }
                },
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}
