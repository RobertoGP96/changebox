package com.lolo.changebox.ui.accounts

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.composables.icons.lucide.Banknote
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.lolo.changebox.data.ActionResult
import com.lolo.changebox.data.local.dao.TxJoinRow
import com.lolo.changebox.data.local.entity.AccountEntity
import com.lolo.changebox.data.local.entity.AccountGroupEntity
import com.lolo.changebox.data.local.entity.CurrencyEntity
import com.lolo.changebox.data.repo.AccountDenominationStock
import com.lolo.changebox.data.repo.toTxRow
import com.lolo.changebox.di.AppContainer
import com.lolo.changebox.di.appViewModel
import com.lolo.changebox.domain.AccountType
import com.lolo.changebox.domain.ActivityDelta
import com.lolo.changebox.domain.DenominationKind
import com.lolo.changebox.domain.activityDeltas
import com.lolo.changebox.domain.DisplayCurrencyOf
import com.lolo.changebox.domain.fmtMinor
import com.lolo.changebox.domain.isCashLike
import com.lolo.changebox.ui.Routes
import com.lolo.changebox.ui.common.BadgeVariant
import com.lolo.changebox.ui.common.ChangeboxBadge
import com.lolo.changebox.ui.common.ChangeboxCard
import com.lolo.changebox.ui.common.ChangeboxSelect
import com.lolo.changebox.ui.common.ChangeboxTextField
import com.lolo.changebox.ui.common.GhostButton
import com.lolo.changebox.ui.common.InlineConfirm
import com.lolo.changebox.ui.common.LocalToast
import com.lolo.changebox.ui.common.OutlineButton
import com.lolo.changebox.ui.common.PrimaryButton
import com.lolo.changebox.ui.common.ScreenHeader
import com.lolo.changebox.ui.common.SectionTitle
import com.lolo.changebox.ui.common.TxList
import com.lolo.changebox.ui.common.fmtShortDateTime
import com.lolo.changebox.ui.common.contentWidth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Detalle de cuenta: saldo, acciones (registrar/arqueo), editor de icono,
// grupo, gestión (renombrar/archivar/eliminar), denominaciones en caja para
// CASH_BOX y movimientos recientes — port de cuentas/[id]/page.tsx.

data class AccountDetailState(
    val loaded: Boolean = false,
    val account: AccountEntity? = null,
    val currency: CurrencyEntity? = null,
    val balanceMinor: Long = 0,
    val rows: List<TxJoinRow> = emptyList(),
    val groups: List<AccountGroupEntity> = emptyList(),
    val hasUsage: Boolean = false,
    val stock: AccountDenominationStock? = null,
    /** Deltas del libro mayor COMPLETO de la cuenta para el gráfico. */
    val activity: List<ActivityDelta> = emptyList(),
)

/** Libro mayor completo reducido a lo que usa el detalle. */
private data class AccountUsage(val activity: List<ActivityDelta>, val hasUsage: Boolean)

class AccountDetailViewModel(
    private val container: AppContainer,
    private val accountId: String,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state = container.accounts.accountFlow(accountId).flatMapLatest { account ->
        if (account == null) {
            flowOf(AccountDetailState(loaded = true))
        } else {
            // Libro mayor completo (ambos lados) + arqueos: alimenta el gráfico
            // de actividad y decide `hasUsage` (como `ledger.length +
            // countCount > 0` en la web), no solo los 30 últimos movimientos.
            val usage = combine(
                container.accounts.accountLedgerFlow(accountId),
                container.accounts.cashCountCountFlow(accountId),
            ) { ledger, countCount ->
                AccountUsage(
                    activity = activityDeltas(accountId, ledger),
                    hasUsage = ledger.size + countCount > 0,
                )
            }
            val detail = combine(
                container.db.catalogDao().currencyFlow(account.currencyId),
                container.accounts.accountBalanceFlow(accountId),
                container.ledger.accountRowsFlow(accountId, 30),
                container.db.catalogDao().groupsFlow(),
                if (account.type == "CASH_BOX") {
                    container.accounts.denominationStockFlow(accountId)
                } else {
                    flowOf(null)
                },
            ) { currency, balance, rows, groups, stock ->
                AccountDetailState(
                    loaded = true,
                    account = account,
                    currency = currency,
                    balanceMinor = balance,
                    // Últimos 30 en orden cronológico (el más reciente al
                    // final), como el `.reverse()` de la web.
                    rows = rows.reversed(),
                    groups = groups,
                    stock = stock,
                )
            }
            combine(detail, usage) { state, u ->
                state.copy(activity = u.activity, hasUsage = u.hasUsage)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountDetailState())

    fun setIcon(icon: String?, onResult: (ActionResult<String>) -> Unit) {
        viewModelScope.launch { onResult(container.accounts.setAccountIcon(accountId, icon)) }
    }

    fun assignGroup(groupId: String?, onResult: (ActionResult<String>) -> Unit) {
        viewModelScope.launch { onResult(container.catalog.assignGroup(accountId, groupId)) }
    }

    fun rename(name: String, onResult: (ActionResult<String>) -> Unit) {
        viewModelScope.launch { onResult(container.accounts.updateAccount(accountId, name)) }
    }

    fun setArchived(name: String, archived: Boolean, onResult: (ActionResult<String>) -> Unit) {
        viewModelScope.launch {
            onResult(container.accounts.updateAccount(accountId, name, archived))
        }
    }

    fun delete(onResult: (ActionResult<Unit>) -> Unit) {
        viewModelScope.launch { onResult(container.accounts.deleteAccount(accountId)) }
    }
}

@Composable
fun AccountDetailScreen(navController: NavHostController, accountId: String) {
    val vm = appViewModel(key = "cuenta-$accountId") { AccountDetailViewModel(it, accountId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current

    val account = state.account
    val currency = state.currency

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val type = account?.let {
            runCatching { AccountType.valueOf(it.type) }.getOrNull()
        }

        ScreenHeader(
            title = account?.name ?: "Cuenta",
            onBack = { navController.popBackStack() },
        ) {
            if (account != null && currency != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "SALDO ACTUAL",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.8.sp,
                        )
                        Text(
                            fmtMinor(
                                state.balanceMinor,
                                DisplayCurrencyOf(currency.code, currency.decimalPlaces),
                            ),
                            color = if (state.balanceMinor < 0) Color(0xFFFFA4B0) else Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                        )
                    }
                    ChangeboxBadge(
                        buildString {
                            append(type?.labelEs ?: account.type)
                            if (account.archived) append(" · Archivada")
                        },
                        BadgeVariant.NEUTRAL,
                    )
                }
            }
        }

        if (account == null || currency == null) {
            if (state.loaded) {
                Text(
                    "Cuenta no encontrada.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
            return@Column
        }

        val display = DisplayCurrencyOf(currency.code, currency.decimalPlaces)

        Column(
            Modifier
                .contentWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Acciones principales
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    PrimaryButton(
                        "Registrar",
                        onClick = { navController.navigate(Routes.register(cuenta = account.id)) },
                        modifier = Modifier.fillMaxWidth(),
                        large = true,
                    )
                }
                if (type?.isCashLike() == true) {
                    Box(Modifier.weight(1f)) {
                        OutlineButton(
                            "Arqueo",
                            onClick = { navController.navigate(Routes.cashCount(account.id)) },
                            modifier = Modifier.fillMaxWidth(),
                            icon = Lucide.Banknote,
                        )
                    }
                }
            }

            // Actividad de la cuenta (libro mayor completo, ambos lados)
            if (state.activity.isNotEmpty()) {
                AccountActivityChart(deltas = state.activity, currency = display)
            }

            // Icono de la cuenta
            ChangeboxCard(corner = 16) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Icono",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconPicker(account.icon) { newIcon ->
                        vm.setIcon(newIcon) { result ->
                            when (result) {
                                is ActionResult.Success -> toast(
                                    if (newIcon == null) "Icono automático" else "Icono actualizado"
                                )
                                is ActionResult.Failure -> toast(result.error)
                            }
                        }
                    }
                }
            }

            // Grupo
            if (state.groups.isNotEmpty()) {
                ChangeboxCard(corner = 16) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "Grupo",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(Modifier.weight(1f)) {
                            ChangeboxSelect(
                                options = listOf<AccountGroupEntity?>(null) + state.groups,
                                selected = state.groups.find { it.id == account.groupId },
                                onSelect = { group ->
                                    vm.assignGroup(group?.id) { result ->
                                        when (result) {
                                            is ActionResult.Success -> toast(
                                                if (group == null) "Cuenta sin grupo"
                                                else "Grupo asignado"
                                            )
                                            is ActionResult.Failure -> toast(result.error)
                                        }
                                    }
                                },
                                display = { it?.name ?: "Sin grupo" },
                            )
                        }
                    }
                }
            }

            // Gestión de la cuenta (renombrar/archivar/eliminar)
            AccountEditor(
                account = account,
                hasUsage = state.hasUsage,
                onRename = { newName, done ->
                    vm.rename(newName) { result ->
                        when (result) {
                            is ActionResult.Success -> {
                                toast("Cuenta renombrada"); done(true)
                            }
                            is ActionResult.Failure -> {
                                toast(result.error); done(false)
                            }
                        }
                    }
                },
                onToggleArchived = {
                    vm.setArchived(account.name, !account.archived) { result ->
                        when (result) {
                            is ActionResult.Success ->
                                toast(if (account.archived) "Cuenta activada" else "Cuenta archivada")
                            is ActionResult.Failure -> toast(result.error)
                        }
                    }
                },
                onDelete = {
                    vm.delete { result ->
                        when (result) {
                            is ActionResult.Success -> {
                                toast("Cuenta eliminada")
                                navController.popBackStack()
                            }
                            is ActionResult.Failure -> toast(result.error)
                        }
                    }
                },
            )

            // Denominaciones en caja (solo CASH_BOX)
            val stock = state.stock
            if (account.type == "CASH_BOX" && stock != null) {
                DenominationAvailabilityCard(stock, display) {
                    navController.navigate(Routes.cashCount(account.id))
                }
            }

            // Movimientos recientes
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Movimientos recientes")
                if (state.rows.isEmpty()) {
                    ChangeboxCard(corner = 16) {
                        Text(
                            "Sin movimientos todavía.",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                } else {
                    TxList(
                        state.rows.map { toTxRow(it, account.id, display) }
                    ) { id -> navController.navigate(Routes.movementDetail(id)) }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountEditor(
    account: AccountEntity,
    hasUsage: Boolean,
    onRename: (String, (Boolean) -> Unit) -> Unit,
    onToggleArchived: () -> Unit,
    onDelete: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(account.name) }
    var confirmDelete by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    ChangeboxCard(corner = 16) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                editing -> {
                    // Campo a lo ancho y botones debajo: no desborda en móviles
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChangeboxTextField(editName, { if (it.length <= 60) editName = it })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrimaryButton(
                                "Guardar",
                                enabled = !saving && editName.isNotBlank(),
                                onClick = {
                                    saving = true
                                    onRename(editName) { okDone ->
                                        saving = false
                                        if (okDone) editing = false
                                    }
                                },
                            )
                            GhostButton("Cancelar", onClick = { editing = false }, enabled = !saving)
                        }
                    }
                }
                confirmDelete -> {
                    InlineConfirm(
                        text = if (hasUsage) {
                            "¿Eliminar “${account.name}”? Se borrarán también TODOS sus movimientos y arqueos (incluidas transferencias con otras cuentas y abonos vinculados). No se puede deshacer."
                        } else {
                            "¿Eliminar “${account.name}”? No se puede deshacer."
                        },
                        confirmLabel = "Eliminar",
                        onConfirm = onDelete,
                        onCancel = { confirmDelete = false },
                    )
                }
                else -> {
                    // Los botones envuelven a una segunda línea si no caben
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Gestionar cuenta",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlineButton("Renombrar", onClick = {
                                editName = account.name
                                editing = true
                            })
                            OutlineButton(
                                if (account.archived) "Activar" else "Archivar",
                                onClick = onToggleArchived,
                            )
                            GhostButton("Eliminar", onClick = { confirmDelete = true }, danger = true)
                        }
                    }
                }
            }
            Text(
                "Eliminar una cuenta borra también todo su historial (movimientos, arqueos y abonos vinculados). Si prefieres conservarlo, archívala y dejará de aparecer al registrar.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Disponibilidad de denominaciones de una caja: derivada, nunca almacenada. */
@Composable
private fun DenominationAvailabilityCard(
    stock: AccountDenominationStock,
    currency: DisplayCurrencyOf,
    onUpdateCount: () -> Unit,
) {
    val lines = stock.lines.filter { it.quantity != 0 }
    val totalMinor = stock.lines.sumOf { it.valueMinor * it.quantity }
    val hasData = stock.countedAt != null || stock.movements > 0

    ChangeboxCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Denominaciones en caja",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Actualizar conteo",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.clickable(onClick = onUpdateCount),
                )
            }

            if (!hasData) {
                Text(
                    "Todavía no hay arqueos ni movimientos con desglose. Haz el primer conteo para registrar qué billetes y monedas hay en la caja.",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                Text(
                    buildString {
                        append(
                            stock.countedAt?.let { "Según el arqueo del ${fmtShortDateTime(it)}" }
                                ?: "Sin arqueo base"
                        )
                        if (stock.movements > 0) {
                            append(
                                " · ${stock.movements} " + if (stock.movements == 1) {
                                    "movimiento posterior"
                                } else {
                                    "movimientos posteriores"
                                }
                            )
                        }
                    },
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (lines.isEmpty()) {
                    Text(
                        "La caja está vacía según el último conteo.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    lines.forEach { line ->
                        val negative = line.quantity < 0
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (negative) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                        else com.lolo.changebox.ui.theme.ChangeboxColors.extended.chip
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Lucide.Banknote,
                                    contentDescription = null,
                                    tint = if (negative) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    fmtMinor(line.valueMinor, currency),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    buildString {
                                        append(
                                            runCatching {
                                                DenominationKind.valueOf(line.kind).labelEs
                                            }.getOrDefault(line.kind)
                                        )
                                        append(" · × ${line.quantity}")
                                        if (negative) append(" · revisa los desgloses")
                                    },
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                fmtMinor(line.valueMinor * line.quantity, currency),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (negative) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Total en denominaciones",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            fmtMinor(totalMinor, currency),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

