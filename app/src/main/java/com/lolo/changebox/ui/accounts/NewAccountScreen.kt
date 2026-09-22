package com.lolo.changebox.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.lolo.changebox.data.ActionResult
import com.lolo.changebox.data.local.entity.AccountGroupEntity
import com.lolo.changebox.data.local.entity.CurrencyEntity
import com.lolo.changebox.di.AppContainer
import com.lolo.changebox.di.appViewModel
import com.lolo.changebox.domain.AccountType
import com.lolo.changebox.domain.isCashLike
import com.lolo.changebox.domain.isDigitalCurrencyKind
import com.lolo.changebox.ui.Routes
import com.lolo.changebox.ui.common.ChangeboxSelect
import com.lolo.changebox.ui.common.ChangeboxTextField
import com.lolo.changebox.ui.common.ErrorBox
import com.lolo.changebox.ui.common.LabeledField
import com.lolo.changebox.ui.common.LocalToast
import com.lolo.changebox.ui.common.PrimaryButton
import com.lolo.changebox.ui.common.ScreenHeader
import com.lolo.changebox.ui.common.SegmentedTabs
import com.lolo.changebox.ui.common.contentWidth
import com.lolo.changebox.ui.theme.ACCOUNT_ICONS
import com.lolo.changebox.ui.theme.ChangeboxColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Nueva cuenta: nombre, tipo (4 botones), icono opcional, moneda, grupo y
// saldo inicial — port de cuentas/nueva/account-form.tsx.

data class NewAccountData(
    val currencies: List<CurrencyEntity> = emptyList(),
    val groups: List<AccountGroupEntity> = emptyList(),
)

class NewAccountViewModel(private val container: AppContainer) : ViewModel() {
    val data = combine(
        container.db.catalogDao().activeCurrenciesFlow(),
        container.db.catalogDao().groupsFlow(),
    ) { currencies, groups -> NewAccountData(currencies, groups) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NewAccountData())

    fun create(
        name: String,
        type: String,
        currencyId: String,
        initialAmount: String?,
        groupId: String?,
        icon: String?,
        onResult: (ActionResult<String>) -> Unit,
    ) {
        viewModelScope.launch {
            onResult(
                container.accounts.createAccount(name, type, currencyId, initialAmount, groupId, icon)
            )
        }
    }
}

/** Cuadrícula de iconos lucide; tocar el seleccionado lo deselecciona. */
@Composable
fun IconPicker(value: String?, onChange: (String?) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.height(180.dp),
        userScrollEnabled = false,
    ) {
        items(ACCOUNT_ICONS) { def ->
            val selected = value == def.name
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) ChangeboxColors.extended.chip
                        else MaterialTheme.colorScheme.surface
                    )
                    .border(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable { onChange(if (selected) null else def.name) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    def.icon,
                    contentDescription = "Icono ${def.name}",
                    tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun NewAccountScreen(navController: NavHostController) {
    val vm = appViewModel { NewAccountViewModel(it) }
    val data by vm.data.collectAsStateWithLifecycle()
    val toast = LocalToast.current

    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("CASH") }
    var currencyId by rememberSaveable { mutableStateOf("") }
    var groupId by rememberSaveable { mutableStateOf("") }
    var icon by rememberSaveable { mutableStateOf<String?>(null) }
    var initialAmount by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(data.currencies) {
        if (currencyId.isEmpty()) currencyId = data.currencies.firstOrNull()?.id ?: ""
    }

    // Las monedas digitales no existen en efectivo: para tipos de caja se
    // ocultan del selector (y el repo lo re-valida al crear la cuenta).
    val cashLike = AccountType.entries.firstOrNull { it.name == type }?.isCashLike() == true
    val eligibleCurrencies =
        if (cashLike) data.currencies.filter { !isDigitalCurrencyKind(it.kind) }
        else data.currencies
    val hiddenDigitalCount = data.currencies.size - eligibleCurrencies.size

    // Al pasar a un tipo de caja con una moneda digital elegida, salta a la
    // primera elegible.
    val pickType: (String) -> Unit = { nextType ->
        type = nextType
        val nextCashLike =
            AccountType.entries.firstOrNull { it.name == nextType }?.isCashLike() == true
        val currentKind = data.currencies.find { it.id == currencyId }?.kind ?: "CASH"
        if (nextCashLike && isDigitalCurrencyKind(currentKind)) {
            currencyId = data.currencies.firstOrNull { !isDigitalCurrencyKind(it.kind) }?.id ?: ""
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(title = "Nueva cuenta", onBack = { navController.popBackStack() })

        Column(
            Modifier
                .contentWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LabeledField("Nombre") {
                ChangeboxTextField(
                    name,
                    { if (it.length <= 60) name = it },
                    placeholder = "Caja principal, Banco, Tarjeta MLC…",
                )
            }

            LabeledField("Tipo") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegmentedTabs(
                        options = listOf(
                            "CASH" to AccountType.CASH.labelEs,
                            "CASH_BOX" to AccountType.CASH_BOX.labelEs,
                        ),
                        selectedKey = type,
                        onSelect = pickType,
                    )
                    SegmentedTabs(
                        options = listOf(
                            "BANK" to AccountType.BANK.labelEs,
                            "DIGITAL" to AccountType.DIGITAL.labelEs,
                        ),
                        selectedKey = type,
                        onSelect = pickType,
                    )
                }
            }

            LabeledField("Icono (opcional; si no eliges, se usa el del tipo)") {
                IconPicker(icon) { icon = it }
            }

            LabeledField(
                "Moneda",
                hint = if (cashLike && hiddenDigitalCount > 0) {
                    "Las monedas digitales no aparecen: no existen en efectivo."
                } else {
                    null
                },
            ) {
                ChangeboxSelect(
                    options = eligibleCurrencies,
                    selected = eligibleCurrencies.find { it.id == currencyId },
                    onSelect = { currencyId = it.id },
                    display = { "${it.code} · ${it.name}" },
                    placeholder = "Elige moneda",
                )
            }

            if (data.groups.isNotEmpty()) {
                LabeledField("Grupo (opcional)") {
                    ChangeboxSelect(
                        options = listOf<AccountGroupEntity?>(null) + data.groups,
                        selected = data.groups.find { it.id == groupId },
                        onSelect = { groupId = it?.id ?: "" },
                        display = { it?.name ?: "Sin grupo" },
                    )
                }
            }

            LabeledField("Saldo inicial (opcional)") {
                ChangeboxTextField(initialAmount, { initialAmount = it }, placeholder = "0", decimal = true)
            }

            error?.let { ErrorBox(it) }

            PrimaryButton(
                text = if (saving) "Creando…" else "Crear cuenta",
                onClick = {
                    saving = true
                    error = null
                    vm.create(
                        name, type, currencyId,
                        initialAmount.trim().ifEmpty { null },
                        groupId.ifEmpty { null },
                        icon,
                    ) { result ->
                        saving = false
                        when (result) {
                            is ActionResult.Success -> {
                                toast("Cuenta creada")
                                navController.popBackStack()
                                navController.navigate(Routes.accountDetail(result.data))
                            }
                            is ActionResult.Failure -> error = result.error
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                large = true,
                enabled = !saving && name.isNotBlank() && currencyId.isNotEmpty(),
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

