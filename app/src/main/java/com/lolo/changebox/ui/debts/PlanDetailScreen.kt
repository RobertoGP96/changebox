package com.lolo.changebox.ui.debts

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.UserRound
import com.lolo.changebox.data.ActionResult
import com.lolo.changebox.data.local.dao.PlanDetailRow
import com.lolo.changebox.data.local.entity.AccountEntity
import com.lolo.changebox.data.local.entity.InstallmentEntity
import com.lolo.changebox.data.toLocalDate
import com.lolo.changebox.di.AppContainer
import com.lolo.changebox.di.appViewModel
import com.lolo.changebox.domain.DisplayCurrencyOf
import com.lolo.changebox.domain.Frequency
import com.lolo.changebox.domain.PlanKind
import com.lolo.changebox.domain.daysUntil
import com.lolo.changebox.domain.dueLabel
import com.lolo.changebox.domain.dueTone
import com.lolo.changebox.domain.fmtMinor
import com.lolo.changebox.domain.minorToInput
import com.lolo.changebox.ui.Routes
import com.lolo.changebox.ui.common.BadgeVariant
import com.lolo.changebox.ui.common.ChangeboxBadge
import com.lolo.changebox.ui.common.ChangeboxCard
import com.lolo.changebox.ui.common.GhostButton
import com.lolo.changebox.ui.common.InlineConfirm
import com.lolo.changebox.ui.common.LocalToast
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

// Detalle de plan de cuotas: próxima cuota (settle/omitir), cuenta vinculada,
// desactivar/eliminar e historial de cuotas — port de mensualidades/[id].

data class PlanDetailState(
    val loaded: Boolean = false,
    val plan: PlanDetailRow? = null,
    val installments: List<InstallmentEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
)

class PlanDetailViewModel(
    private val container: AppContainer,
    private val planId: String,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state = container.plans.planDetailFlow(planId).flatMapLatest { plan ->
        if (plan == null) {
            flowOf(PlanDetailState(loaded = true))
        } else {
            combine(
                container.plans.installmentsFlow(planId, 24),
                container.accounts.activeAccountsInCurrencyFlow(plan.plan.currencyId),
            ) { installments, accounts ->
                PlanDetailState(true, plan, installments, accounts)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanDetailState())

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

    fun setAccount(accountId: String?, onResult: (ActionResult<String>) -> Unit) {
        viewModelScope.launch { onResult(container.plans.setPlanAccount(planId, accountId)) }
    }

    fun deactivate(onResult: (ActionResult<String>) -> Unit) {
        viewModelScope.launch { onResult(container.plans.deactivatePlan(planId)) }
    }

    fun delete(onResult: (ActionResult<String?>) -> Unit) {
        viewModelScope.launch { onResult(container.plans.deletePlan(planId)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanDetailScreen(navController: NavHostController, planId: String) {
    val vm = appViewModel(key = "plan-$planId") { PlanDetailViewModel(it, planId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current

    val row = state.plan
    val plan = row?.plan
    val display = row?.let { DisplayCurrencyOf(it.currencyCode, it.currencyDecimals) }

    val pending = state.installments
        .filter { it.status == "PENDING" }
        .minByOrNull { it.dueAt }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(
            title = plan?.description ?: "Plan de cuotas",
            onBack = { navController.popBackStack() },
        ) {
            if (plan != null && display != null) {
                val contactName = row?.contactName ?: row?.debtContactName
                val paidCount = state.installments.count { it.status == "PAID" }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            fmtMinor(plan.amountMinor, display),
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                        )
                        Text(
                            buildString {
                                append(
                                    runCatching { PlanKind.valueOf(plan.kind).labelEs }
                                        .getOrDefault(plan.kind)
                                )
                                append(" · ")
                                append(
                                    runCatching { Frequency.valueOf(plan.frequency).labelEs }
                                        .getOrDefault(plan.frequency)
                                )
                            },
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.5.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    ChangeboxBadge(if (plan.active) "Activa" else "Finalizada", BadgeVariant.NEUTRAL)
                }
                FlowRow(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Lucide.UserRound,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            contactName ?: "Sin contacto",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.5.sp,
                        )
                    }
                    Text(
                        "$paidCount cuota${if (paidCount == 1) "" else "s"} saldada${if (paidCount == 1) "" else "s"}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.5.sp,
                    )
                    plan.endAt?.let { endAt ->
                        Text(
                            "Termina ${fmtDate(endAt)}",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.5.sp,
                        )
                    }
                }
            }
        }

        if (plan == null || display == null) {
            if (state.loaded) {
                Text(
                    "Plan no encontrado.",
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
            // Vínculo a la deuda origen
            if (plan.debtId != null && row.debtContactName != null) {
                ChangeboxCard(corner = 16, onClick = {
                    navController.navigate(Routes.debtDetail(plan.debtId))
                }) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Ver deuda de ${row.debtContactName}",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Lucide.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // Próxima cuota
            if (plan.active && pending != null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Próxima cuota")
                    ChangeboxCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        fmtMinor(pending.amountMinor, display),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        fmtDate(pending.dueAt),
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                val days = daysUntil(pending.dueAt.toLocalDate())
                                ChangeboxBadge(
                                    dueLabel(pending.dueAt.toLocalDate()),
                                    dueTone(days).toBadge(),
                                )
                            }
                            SettleInstallmentRow(
                                accounts = state.accounts,
                                defaultAmount = minorToInput(
                                    pending.amountMinor, display.decimalPlaces,
                                ),
                                currencyCode = display.code,
                                kind = plan.kind,
                                defaultAccountId = plan.accountId,
                                onSettle = { accountId, amount, done ->
                                    vm.settleInstallment(pending.id, accountId, amount) { result ->
                                        done()
                                        when (result) {
                                            is ActionResult.Success -> toast(
                                                if (plan.kind == "COLLECT") "Cobro registrado"
                                                else "Pago registrado"
                                            )
                                            is ActionResult.Failure -> toast(result.error)
                                        }
                                    }
                                },
                                onSkip = { done ->
                                    vm.skipInstallment(pending.id) { result ->
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

            // Cuenta vinculada
            if (plan.active) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Cuenta vinculada")
                    LinkedAccountEditor(
                        accounts = state.accounts,
                        currentAccountId = plan.accountId,
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

            // Finalizar + eliminar
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (plan.active) {
                    DeactivatePlanButton(onDeactivate = {
                        vm.deactivate { result ->
                            when (result) {
                                is ActionResult.Success -> toast("Mensualidad finalizada")
                                is ActionResult.Failure -> toast(result.error)
                            }
                        }
                    })
                }
            }
            DeleteEntityRow(
                buttonLabel = "Eliminar plan",
                warning = "¿Eliminar el plan? Se borran todas sus cuotas; los movimientos de las cuentas se conservan. No se puede deshacer.",
                onDelete = {
                    vm.delete { result ->
                        when (result) {
                            is ActionResult.Success -> {
                                toast("Plan eliminado")
                                navController.popBackStack()
                            }
                            is ActionResult.Failure -> toast(result.error)
                        }
                    }
                },
            )

            // Historial de cuotas
            if (state.installments.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Historial de cuotas")
                    state.installments.forEach { inst ->
                        ChangeboxCard(corner = 13) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    fmtDate(inst.dueAt),
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    fmtMinor(inst.amountMinor, display),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                when (inst.status) {
                                    "PAID" -> ChangeboxBadge("Saldada", BadgeVariant.OK)
                                    "SKIPPED" -> ChangeboxBadge("Omitida", BadgeVariant.NEUTRAL)
                                    else -> ChangeboxBadge(
                                        dueLabel(inst.dueAt.toLocalDate()),
                                        if (daysUntil(inst.dueAt.toLocalDate()) < 0) BadgeVariant.DANGER
                                        else BadgeVariant.WARN,
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

@Composable
private fun DeactivatePlanButton(onDeactivate: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    if (!confirming) {
        GhostButton("Finalizar mensualidad", onClick = { confirming = true })
    } else {
        InlineConfirm(
            text = "¿Finalizar? Las cuotas pendientes se omiten.",
            confirmLabel = "Sí, finalizar",
            onConfirm = {
                confirming = false
                onDeactivate()
            },
            onCancel = { confirming = false },
        )
    }
}

