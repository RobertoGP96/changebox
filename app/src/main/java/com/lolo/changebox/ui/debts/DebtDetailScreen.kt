package com.lolo.changebox.ui.debts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.lolo.changebox.data.ActionResult
import com.lolo.changebox.data.local.dao.DebtWithMeta
import com.lolo.changebox.data.local.dao.PendingInstallmentRow
import com.lolo.changebox.data.local.entity.AccountEntity
import com.lolo.changebox.data.local.entity.DebtPaymentEntity
import com.lolo.changebox.data.toLocalDate
import com.lolo.changebox.di.AppContainer
import com.lolo.changebox.di.appViewModel
import com.lolo.changebox.domain.DebtDirection
import com.lolo.changebox.domain.DisplayCurrencyOf
import com.lolo.changebox.domain.daysUntil
import com.lolo.changebox.domain.dueLabel
import com.lolo.changebox.domain.fmtMinor
import com.lolo.changebox.domain.minorToInput
import com.lolo.changebox.ui.common.BadgeVariant
import com.lolo.changebox.ui.common.ChangeboxBadge
import com.lolo.changebox.ui.common.ChangeboxCard
import com.lolo.changebox.ui.common.ChangeboxSelect
import com.lolo.changebox.ui.common.ChangeboxTextField
import com.lolo.changebox.ui.common.ErrorBox
import com.lolo.changebox.ui.common.GhostButton
import com.lolo.changebox.ui.common.InlineConfirm
import com.lolo.changebox.ui.common.LocalToast
import com.lolo.changebox.ui.common.PrimaryButton
import com.lolo.changebox.ui.common.ScreenHeader
import com.lolo.changebox.ui.common.SectionTitle
import com.lolo.changebox.ui.common.fmtDate
import com.lolo.changebox.ui.common.contentWidth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Detalle de deuda: progreso de abonos, cuotas pendientes con settle, form de
// abono, cuenta vinculada, historial y eliminación — port de deudas/[id].

data class DebtDetailState(
    val loaded: Boolean = false,
    val debt: DebtWithMeta? = null,
    val pendingInstallments: List<PendingInstallmentRow> = emptyList(),
    val payments: List<DebtPaymentEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
)

class DebtDetailViewModel(
    private val container: AppContainer,
    private val debtId: String,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state = container.debts.debtWithMetaFlow(debtId).flatMapLatest { debt ->
        if (debt == null) {
            flowOf(DebtDetailState(loaded = true))
        } else {
            combine(
                container.debts.pendingInstallmentsForDebtFlow(debtId),
                container.debts.paymentsFlow(debtId),
                container.accounts.activeAccountsInCurrencyFlow(debt.debt.currencyId),
            ) { pending, payments, accounts ->
                DebtDetailState(true, debt, pending, payments, accounts)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebtDetailState())

    fun settleInstallment(
        installmentId: String,
        accountId: String,
        amount: String?,
        onResult: (ActionResult<*>) -> Unit,
    ) {
        viewModelScope.launch {
            onResult(container.plans.settleInstallment(installmentId, accountId, amount))
        }
    }

    fun skipInstallment(installmentId: String, onResult: (ActionResult<*>) -> Unit) {
        viewModelScope.launch { onResult(container.plans.skipInstallment(installmentId)) }
    }

    fun registerPayment(
        accountId: String,
        amount: String,
        note: String?,
        onResult: (ActionResult<com.lolo.changebox.data.repo.DebtRepository.PaymentOutcome>) -> Unit,
    ) {
        viewModelScope.launch {
            onResult(container.debts.registerDebtPayment(debtId, accountId, amount, note))
        }
    }

    fun setAccount(accountId: String?, onResult: (ActionResult<String>) -> Unit) {
        viewModelScope.launch { onResult(container.debts.setDebtAccount(debtId, accountId)) }
    }

    fun delete(onResult: (ActionResult<Unit>) -> Unit) {
        viewModelScope.launch { onResult(container.debts.deleteDebt(debtId)) }
    }
}

@Composable
fun DebtDetailScreen(navController: NavHostController, debtId: String) {
    val vm = appViewModel(key = "deuda-$debtId") { DebtDetailViewModel(it, debtId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current

    val item = state.debt
    val debt = item?.debt
    val display = item?.let { DisplayCurrencyOf(it.currencyCode, it.currencyDecimals) }

    val paid = item?.paidMinor ?: 0L
    val remaining = (debt?.totalMinor ?: 0L) - paid
    val pct = if (debt != null) {
        minOf(100, ((paid.toDouble() / maxOf(1L, debt.totalMinor)) * 100).toInt())
    } else {
        0
    }
    val isOpen = debt?.status == "OPEN"

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(
            title = item?.contactName ?: "Deuda",
            onBack = { navController.popBackStack() },
        ) {
            if (debt != null && display != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        debt.description,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.5.sp,
                        modifier = Modifier.weight(1f),
                    )
                    ChangeboxBadge(
                        runCatching { DebtDirection.valueOf(debt.direction).labelEs }
                            .getOrDefault(debt.direction),
                        BadgeVariant.NEUTRAL,
                    )
                }
                Text(
                    if (isOpen) fmtMinor(remaining, display) else "Saldada",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                // Barra de progreso de abonos
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.2f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(pct / 100f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.9f))
                    )
                }
                Text(
                    "Abonado ${fmtMinor(paid, display)} de ${fmtMinor(debt.totalMinor, display)} ($pct%)",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        if (debt == null || display == null) {
            if (state.loaded) {
                Text(
                    "Deuda no encontrada.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
            return@Column
        }

        Column(
            Modifier
                .contentWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Cuotas pendientes
            if (isOpen && state.pendingInstallments.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Cuotas pendientes")
                    state.pendingInstallments.forEach { pending ->
                        val inst = pending.installment
                        val days = daysUntil(inst.dueAt.toLocalDate())
                        ChangeboxCard {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            fmtMinor(inst.amountMinor, display),
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            fmtDate(inst.dueAt),
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    ChangeboxBadge(
                                        dueLabel(inst.dueAt.toLocalDate()),
                                        when {
                                            days < 0 -> BadgeVariant.DANGER
                                            days <= 1 -> BadgeVariant.WARN
                                            else -> BadgeVariant.NEUTRAL
                                        },
                                    )
                                }
                                SettleInstallmentRow(
                                    accounts = state.accounts,
                                    defaultAmount = minorToInput(
                                        minOf(inst.amountMinor, remaining),
                                        display.decimalPlaces,
                                    ),
                                    currencyCode = display.code,
                                    kind = pending.planKind,
                                    defaultAccountId = pending.planAccountId ?: debt.accountId,
                                    onSettle = { accountId, amount, done ->
                                        vm.settleInstallment(inst.id, accountId, amount) { result ->
                                            done()
                                            when (result) {
                                                is ActionResult.Success -> toast(
                                                    if (pending.planKind == "COLLECT") "Cobro registrado"
                                                    else "Pago registrado"
                                                )
                                                is ActionResult.Failure -> toast(result.error)
                                            }
                                        }
                                    },
                                    onSkip = { done ->
                                        vm.skipInstallment(inst.id) { result ->
                                            done()
                                            when (result) {
                                                is ActionResult.Success -> toast("Cuota omitida")
                                                is ActionResult.Failure -> toast(result.error)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Registrar abono
            if (isOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Registrar abono")
                    PaymentForm(
                        accounts = state.accounts,
                        currencyCode = display.code,
                        defaultAccountId = debt.accountId,
                        onSubmit = { accountId, amount, note, done ->
                            vm.registerPayment(accountId, amount, note) { result ->
                                when (result) {
                                    is ActionResult.Success -> {
                                        toast(
                                            if (result.data.settled) "Deuda saldada 🎉"
                                            else "Abono registrado"
                                        )
                                        done(true)
                                    }
                                    is ActionResult.Failure -> {
                                        done(false)
                                        toast(result.error)
                                    }
                                }
                            }
                        },
                    )
                }
            }

            // Cuenta vinculada
            if (isOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Cuenta vinculada")
                    LinkedAccountEditor(
                        accounts = state.accounts,
                        currentAccountId = debt.accountId,
                        currencyCode = display.code,
                        onChange = { accountId, done ->
                            vm.setAccount(accountId) { result ->
                                done()
                                when (result) {
                                    is ActionResult.Success -> toast(
                                        if (accountId != null) "Cuenta vinculada"
                                        else "Cuenta desvinculada"
                                    )
                                    is ActionResult.Failure -> toast(result.error)
                                }
                            }
                        },
                    )
                }
            }

            // Historial de abonos
            if (state.payments.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Historial de abonos")
                    state.payments.forEach { payment ->
                        ChangeboxCard(corner = 13) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    fmtDate(payment.paidAt),
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "+${fmtMinor(payment.amountMinor, display)}",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = com.lolo.changebox.ui.theme.ChangeboxColors.extended.ok,
                                )
                            }
                        }
                    }
                }
            }

            // Eliminar deuda
            DeleteEntityRow(
                buttonLabel = "Eliminar deuda",
                warning = "¿Eliminar la deuda? Se borran sus abonos y planes de cuotas; los movimientos de las cuentas se conservan. No se puede deshacer.",
                onDelete = {
                    vm.delete { result ->
                        when (result) {
                            is ActionResult.Success -> {
                                toast("Deuda eliminada")
                                navController.popBackStack()
                            }
                            is ActionResult.Failure -> toast(result.error)
                        }
                    }
                },
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Registrar el cobro/pago de una cuota, con cuenta preseleccionada. */
@Composable
fun SettleInstallmentRow(
    accounts: List<AccountEntity>,
    defaultAmount: String,
    currencyCode: String,
    kind: String,
    defaultAccountId: String?,
    onSettle: (accountId: String, amount: String?, done: () -> Unit) -> Unit,
    onSkip: (done: () -> Unit) -> Unit,
) {
    // Arranca en la cuenta vinculada al plan/deuda (si sigue disponible).
    var accountId by remember(defaultAccountId, accounts) {
        mutableStateOf(
            accounts.find { it.id == defaultAccountId }?.id ?: accounts.firstOrNull()?.id ?: ""
        )
    }
    var amount by remember(defaultAmount) { mutableStateOf(defaultAmount) }
    var saving by remember { mutableStateOf(false) }

    if (accounts.isEmpty()) {
        Text(
            "Crea una cuenta en $currencyCode para registrar el " +
                if (kind == "COLLECT") "cobro." else "pago.",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                ChangeboxSelect(
                    options = accounts,
                    selected = accounts.find { it.id == accountId },
                    onSelect = { accountId = it.id },
                    display = { it.name },
                    placeholder = "Cuenta",
                )
            }
            Box(Modifier.width(96.dp)) {
                ChangeboxTextField(amount, { amount = it }, decimal = true)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                if (kind == "COLLECT") "Cobrar" else "Pagar",
                enabled = !saving && accountId.isNotEmpty() && amount.isNotBlank(),
                onClick = {
                    saving = true
                    onSettle(accountId, amount.trim().ifEmpty { null }) { saving = false }
                },
            )
            GhostButton("Omitir", enabled = !saving, onClick = {
                saving = true
                onSkip { saving = false }
            })
        }
    }
}

/** Form de abono directo a la deuda. */
@Composable
private fun PaymentForm(
    accounts: List<AccountEntity>,
    currencyCode: String,
    defaultAccountId: String?,
    onSubmit: (accountId: String, amount: String, note: String?, done: (Boolean) -> Unit) -> Unit,
) {
    var accountId by remember(defaultAccountId, accounts) {
        mutableStateOf(
            accounts.find { it.id == defaultAccountId }?.id ?: accounts.firstOrNull()?.id ?: ""
        )
    }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    if (accounts.isEmpty()) {
        ChangeboxCard(corner = 13) {
            Text(
                "Crea una cuenta en $currencyCode para poder registrar abonos.",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(14.dp),
            )
        }
        return
    }

    ChangeboxCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    ChangeboxSelect(
                        options = accounts,
                        selected = accounts.find { it.id == accountId },
                        onSelect = { accountId = it.id },
                        display = { it.name },
                        placeholder = "Cuenta",
                    )
                }
                Box(Modifier.width(130.dp)) {
                    ChangeboxTextField(
                        amount, { amount = it },
                        placeholder = "Monto ($currencyCode)", decimal = true,
                    )
                }
            }
            ChangeboxTextField(
                note, { if (it.length <= 200) note = it },
                placeholder = "Nota (opcional)",
            )
            PrimaryButton(
                if (saving) "Guardando…" else "Registrar abono",
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving && amount.isNotBlank() && accountId.isNotEmpty(),
                onClick = {
                    saving = true
                    onSubmit(accountId, amount, note.trim().ifEmpty { null }) { okDone ->
                        saving = false
                        if (okDone) {
                            amount = ""
                            note = ""
                        }
                    }
                },
            )
        }
    }
}

/** Selector de cuenta vinculada (guarda al vuelo; null desvincula). */
@Composable
fun LinkedAccountEditor(
    accounts: List<AccountEntity>,
    currentAccountId: String?,
    currencyCode: String,
    onChange: (String?, done: () -> Unit) -> Unit,
) {
    var saving by remember { mutableStateOf(false) }

    if (accounts.isEmpty()) {
        ChangeboxCard(corner = 13) {
            Text(
                "Crea una cuenta en $currencyCode para poder vincularla.",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(14.dp),
            )
        }
        return
    }

    ChangeboxSelect(
        options = listOf<AccountEntity?>(null) + accounts,
        selected = accounts.find { it.id == currentAccountId },
        onSelect = { account ->
            saving = true
            onChange(account?.id) { saving = false }
        },
        display = { it?.name ?: "Sin cuenta" },
        enabled = !saving,
    )
}

/** Botón de eliminación con confirmación inline (deudas y planes). */
@Composable
fun DeleteEntityRow(
    buttonLabel: String,
    warning: String,
    onDelete: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        if (!confirming) {
            GhostButton(buttonLabel, danger = true, onClick = { confirming = true })
        } else {
            InlineConfirm(
                text = warning,
                confirmLabel = "Eliminar",
                onConfirm = {
                    saving = true
                    onDelete()
                },
                onCancel = { confirming = false },
                busy = saving,
            )
        }
    }
}

