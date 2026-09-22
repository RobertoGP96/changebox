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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.lolo.changebox.data.ActionResult
import com.lolo.changebox.data.local.entity.AccountEntity
import com.lolo.changebox.data.local.entity.ContactEntity
import com.lolo.changebox.data.local.entity.CurrencyEntity
import com.lolo.changebox.data.repo.CreateDebtInput
import com.lolo.changebox.data.repo.CreatePlanInput
import com.lolo.changebox.di.AppContainer
import com.lolo.changebox.di.appViewModel
import com.lolo.changebox.domain.DebtDirection
import com.lolo.changebox.domain.Frequency
import com.lolo.changebox.domain.PlanKind
import com.lolo.changebox.ui.Routes
import com.lolo.changebox.ui.common.ChangeboxSelect
import com.lolo.changebox.ui.common.ChangeboxTextField
import com.lolo.changebox.ui.common.DateField
import com.lolo.changebox.ui.common.ErrorBox
import com.lolo.changebox.ui.common.LabeledField
import com.lolo.changebox.ui.common.LocalToast
import com.lolo.changebox.ui.common.PrimaryButton
import com.lolo.changebox.ui.common.ScreenHeader
import com.lolo.changebox.ui.common.SegmentedTabs
import com.lolo.changebox.ui.common.contentWidth
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Nueva deuda (con plan de cuotas opcional) y nuevo plan standalone — ports
// de deudas/nueva/debt-form.tsx y deudas/plan/nuevo/plan-form.tsx.

data class DebtFormData(
    val contacts: List<ContactEntity> = emptyList(),
    val currencies: List<CurrencyEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
)

class DebtFormViewModel(private val container: AppContainer) : ViewModel() {
    val data = combine(
        container.debts.contactsFlow(),
        container.db.catalogDao().activeCurrenciesFlow(),
        container.accounts.activeAccountsFlow(),
    ) { contacts, currencies, accounts -> DebtFormData(contacts, currencies, accounts) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebtFormData())

    fun createDebt(input: CreateDebtInput, onResult: (ActionResult<String>) -> Unit) {
        viewModelScope.launch { onResult(container.debts.createDebt(input)) }
    }

    fun createPlan(input: CreatePlanInput, onResult: (ActionResult<String>) -> Unit) {
        viewModelScope.launch { onResult(container.plans.createPlan(input)) }
    }
}

private const val NEW_CONTACT = "__new__"

@Composable
fun NewDebtScreen(navController: NavHostController) {
    val vm = appViewModel { DebtFormViewModel(it) }
    val data by vm.data.collectAsStateWithLifecycle()
    val toast = LocalToast.current

    var direction by rememberSaveable { mutableStateOf("RECEIVABLE") }
    var contactId by rememberSaveable { mutableStateOf(NEW_CONTACT) }
    var contactName by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var total by rememberSaveable { mutableStateOf("") }
    var currencyId by rememberSaveable { mutableStateOf("") }
    var accountId by rememberSaveable { mutableStateOf("") }
    var withPlan by rememberSaveable { mutableStateOf(false) }
    var frequency by rememberSaveable { mutableStateOf("MONTHLY") }
    var installmentAmount by rememberSaveable { mutableStateOf("") }
    var firstDueAt by remember { mutableStateOf(LocalDate.now()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(data.currencies) {
        if (currencyId.isEmpty()) currencyId = data.currencies.firstOrNull()?.id ?: ""
    }

    // Solo cuentas de la moneda elegida: es donde se registrarán los abonos.
    val currencyAccounts = data.accounts.filter { it.currencyId == currencyId }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(title = "Nueva deuda", onBack = { navController.popBackStack() })

        Column(
            Modifier
                .contentWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SegmentedTabs(
                options = DebtDirection.entries.map { it.name to it.labelEs },
                selectedKey = direction,
                onSelect = { direction = it },
            )

            LabeledField("Contacto") {
                ChangeboxSelect(
                    options = listOf(NEW_CONTACT) + data.contacts.map { it.id },
                    selected = contactId,
                    onSelect = { contactId = it },
                    display = { id ->
                        if (id == NEW_CONTACT) "+ Nuevo contacto"
                        else data.contacts.find { it.id == id }?.name ?: id
                    },
                )
            }

            if (contactId == NEW_CONTACT) {
                ChangeboxTextField(
                    contactName,
                    { if (it.length <= 60) contactName = it },
                    placeholder = "Nombre del contacto",
                )
            }

            LabeledField("Descripción") {
                ChangeboxTextField(
                    description,
                    { if (it.length <= 120) description = it },
                    placeholder = "Préstamo, mercancía, alquiler…",
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledField("Monto total", Modifier.weight(1f)) {
                    ChangeboxTextField(total, { total = it }, placeholder = "0", decimal = true)
                }
                LabeledField("Moneda", Modifier.width(110.dp)) {
                    ChangeboxSelect(
                        options = data.currencies,
                        selected = data.currencies.find { it.id == currencyId },
                        onSelect = { currency ->
                            currencyId = currency.id
                            val selected = data.accounts.find { it.id == accountId }
                            if (selected != null && selected.currencyId != currency.id) {
                                accountId = ""
                            }
                        },
                        display = { it.code },
                    )
                }
            }

            if (currencyAccounts.isNotEmpty()) {
                LabeledField(
                    "Cuenta para abonos (opcional)",
                    hint = "Se preseleccionará al registrar abonos y cuotas.",
                ) {
                    ChangeboxSelect(
                        options = listOf<AccountEntity?>(null) + currencyAccounts,
                        selected = currencyAccounts.find { it.id == accountId },
                        onSelect = { accountId = it?.id ?: "" },
                        display = { it?.name ?: "Sin cuenta" },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = withPlan, onCheckedChange = { withPlan = it })
                Text(
                    "Con plan de cuotas (recordatorios periódicos)",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (withPlan) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(com.lolo.changebox.ui.theme.ChangeboxColors.extended.chip.copy(alpha = 0.4f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LabeledField("Frecuencia", Modifier.weight(1f)) {
                            ChangeboxSelect(
                                options = Frequency.entries.map { it.name },
                                selected = frequency,
                                onSelect = { frequency = it },
                                display = { Frequency.valueOf(it).labelEs },
                            )
                        }
                        LabeledField("Cuota", Modifier.weight(1f)) {
                            ChangeboxTextField(
                                installmentAmount,
                                { installmentAmount = it },
                                placeholder = "0",
                                decimal = true,
                            )
                        }
                    }
                    LabeledField("Primer vencimiento") {
                        DateField(firstDueAt, { firstDueAt = it })
                    }
                }
            }

            error?.let { ErrorBox(it) }

            PrimaryButton(
                text = if (saving) "Creando…" else "Crear deuda",
                onClick = {
                    saving = true
                    error = null
                    vm.createDebt(
                        CreateDebtInput(
                            contactId = if (contactId == NEW_CONTACT) null else contactId,
                            contactName = if (contactId == NEW_CONTACT) contactName else null,
                            direction = direction,
                            description = description,
                            total = total,
                            currencyId = currencyId,
                            accountId = accountId.ifEmpty { null },
                            frequency = if (withPlan) frequency else null,
                            installmentAmount = if (withPlan) installmentAmount else null,
                            firstDueAt = if (withPlan) firstDueAt else null,
                        )
                    ) { result ->
                        saving = false
                        when (result) {
                            is ActionResult.Success -> {
                                toast("Deuda creada")
                                navController.popBackStack()
                                navController.navigate(Routes.debtDetail(result.data))
                            }
                            is ActionResult.Failure -> error = result.error
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                large = true,
                enabled = !saving && description.isNotBlank() && total.isNotBlank() &&
                    currencyId.isNotEmpty() &&
                    !(contactId == NEW_CONTACT && contactName.isBlank()) &&
                    !(withPlan && installmentAmount.isBlank()),
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun NewPlanScreen(navController: NavHostController) {
    val vm = appViewModel { DebtFormViewModel(it) }
    val data by vm.data.collectAsStateWithLifecycle()
    val toast = LocalToast.current

    var kind by rememberSaveable { mutableStateOf("PAY") }
    var description by rememberSaveable { mutableStateOf("") }
    var contactId by rememberSaveable { mutableStateOf("") }
    var currencyId by rememberSaveable { mutableStateOf("") }
    var accountId by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var frequency by rememberSaveable { mutableStateOf("MONTHLY") }
    var firstDueAt by remember { mutableStateOf(LocalDate.now()) }
    var endAt by remember { mutableStateOf<LocalDate?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(data.currencies) {
        if (currencyId.isEmpty()) currencyId = data.currencies.firstOrNull()?.id ?: ""
    }

    // Solo cuentas de la moneda elegida: es donde se pagan/cobran las cuotas.
    val currencyAccounts = data.accounts.filter { it.currencyId == currencyId }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(title = "Nueva mensualidad", onBack = { navController.popBackStack() })

        Column(
            Modifier
                .contentWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SegmentedTabs(
                options = PlanKind.entries.map {
                    it.name to "${it.labelEs}${if (it == PlanKind.COLLECT) " (me pagan)" else " (yo pago)"}"
                },
                selectedKey = kind,
                onSelect = { kind = it },
            )

            LabeledField("Descripción") {
                ChangeboxTextField(
                    description,
                    { if (it.length <= 120) description = it },
                    placeholder = "Renta, mensualidad, suscripción…",
                )
            }

            LabeledField("Contacto (opcional)") {
                ChangeboxSelect(
                    options = listOf<ContactEntity?>(null) + data.contacts,
                    selected = data.contacts.find { it.id == contactId },
                    onSelect = { contactId = it?.id ?: "" },
                    display = { it?.name ?: "Sin contacto" },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledField("Cuota", Modifier.weight(1f)) {
                    ChangeboxTextField(amount, { amount = it }, placeholder = "0", decimal = true)
                }
                LabeledField("Moneda", Modifier.width(110.dp)) {
                    ChangeboxSelect(
                        options = data.currencies,
                        selected = data.currencies.find { it.id == currencyId },
                        onSelect = { currency ->
                            currencyId = currency.id
                            val selected = data.accounts.find { it.id == accountId }
                            if (selected != null && selected.currencyId != currency.id) {
                                accountId = ""
                            }
                        },
                        display = { it.code },
                    )
                }
            }

            if (currencyAccounts.isNotEmpty()) {
                LabeledField(
                    "Cuenta para las cuotas (opcional)",
                    hint = "Se preseleccionará al pagar o cobrar cada cuota.",
                ) {
                    ChangeboxSelect(
                        options = listOf<AccountEntity?>(null) + currencyAccounts,
                        selected = currencyAccounts.find { it.id == accountId },
                        onSelect = { accountId = it?.id ?: "" },
                        display = { it?.name ?: "Sin cuenta" },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledField("Frecuencia", Modifier.weight(1f)) {
                    ChangeboxSelect(
                        options = Frequency.entries.map { it.name },
                        selected = frequency,
                        onSelect = { frequency = it },
                        display = { Frequency.valueOf(it).labelEs },
                    )
                }
                LabeledField("Primer vencimiento", Modifier.weight(1f)) {
                    DateField(firstDueAt, { firstDueAt = it })
                }
            }

            LabeledField("Termina el (opcional)") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DateField(endAt ?: firstDueAt, { endAt = it }, minDate = firstDueAt)
    if (endAt == null) {
                        Text(
                            "Sin fecha de fin (toca el campo para fijar una)",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "Quitar fecha de fin",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { endAt = null }
                                .padding(2.dp),
                        )
                    }
                }
            }

            error?.let { ErrorBox(it) }

            PrimaryButton(
                text = if (saving) "Creando…" else "Crear mensualidad",
                onClick = {
                    saving = true
                    error = null
                    vm.createPlan(
                        CreatePlanInput(
                            kind = kind,
                            description = description,
                            contactId = contactId.ifEmpty { null },
                            currencyId = currencyId,
                            accountId = accountId.ifEmpty { null },
                            amount = amount,
                            frequency = frequency,
                            firstDueAt = firstDueAt,
                            endAt = endAt,
                        )
                    ) { result ->
                        saving = false
                        when (result) {
                            is ActionResult.Success -> {
                                toast("Mensualidad creada")
                                navController.popBackStack()
                                navController.navigate(Routes.planDetail(result.data))
                            }
                            is ActionResult.Failure -> error = result.error
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                large = true,
                enabled = !saving && description.isNotBlank() && amount.isNotBlank() &&
                    currencyId.isNotEmpty(),
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

