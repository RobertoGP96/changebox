package com.lolo.changebox.ui.debts

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.composables.icons.lucide.ArrowDownLeft
import com.composables.icons.lucide.ArrowUpRight
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.HandCoins
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.UserRound
import com.lolo.changebox.data.ActionResult
import com.lolo.changebox.data.local.dao.PlanListRow
import com.lolo.changebox.data.local.entity.AccountEntity
import com.lolo.changebox.data.toLocalDate
import com.lolo.changebox.di.AppContainer
import com.lolo.changebox.di.appViewModel
import com.lolo.changebox.domain.DisplayCurrencyOf
import com.lolo.changebox.domain.Frequency
import com.lolo.changebox.domain.PLANS_BY_URGENCY
import com.lolo.changebox.domain.PlanKind
import com.lolo.changebox.domain.PlanLike
import com.lolo.changebox.domain.UrgencySortable
import com.lolo.changebox.domain.daysUntil
import com.lolo.changebox.domain.dueLabel
import com.lolo.changebox.domain.dueTone
import com.lolo.changebox.domain.fmtMinor
import com.lolo.changebox.domain.minorToInput
import com.lolo.changebox.domain.monthlyCommitmentByCurrency
import com.lolo.changebox.ui.Routes
import com.lolo.changebox.ui.common.BadgeVariant
import com.lolo.changebox.ui.common.ChangeboxBadge
import com.lolo.changebox.ui.common.ChangeboxCard
import com.lolo.changebox.ui.common.EmptyState
import com.lolo.changebox.ui.common.LocalToast
import com.lolo.changebox.ui.common.ScreenHeader
import com.lolo.changebox.ui.common.SectionTitle
import com.lolo.changebox.ui.common.contentWidth
import com.lolo.changebox.ui.theme.ChangeboxColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Mensualidades: vista propia de TODOS los planes de cuotas (standalone y
// ligados a deuda) con el contacto, frecuencia, cuotas saldadas y badge de
// vencimiento; filtros Todas / Que pago / Que cobro, compromiso mensual por
// moneda en la cabecera, orden por urgencia, saldar/omitir embebido cuando la
// próxima cuota vence en ≤7 días y sección «Finalizadas» — port de
// mensualidades/page.tsx.

// Una cuota se salda desde la propia lista si ya venció o vence dentro de
// esta ventana: es lo que permite despachar varias mensualidades de una vez.
private const val SETTLE_WINDOW_DAYS = 7

/** Filtros de la web: valor del ?tipo= y su etiqueta ("" = Todas). */
private val PLAN_FILTERS = listOf(
    "" to "Todas",
    "pagar" to "Que pago",
    "cobrar" to "Que cobro",
)

data class MonthlyPlansState(
    val loaded: Boolean = false,
    val plans: List<PlanListRow> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
)

class MonthlyPlansViewModel(private val container: AppContainer) : ViewModel() {

    val state = combine(
        container.plans.allPlansFlow(),
        container.accounts.activeAccountsFlow(),
    ) { plans, accounts ->
        MonthlyPlansState(loaded = true, plans = plans, accounts = accounts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthlyPlansState())

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
}

/** Adaptador de la fila de Room al criterio de orden del dominio. */
private class UrgencyRow(val item: PlanListRow) : UrgencySortable {
    override val nextDueAtMillis: Long? get() = item.nextPendingDueAt
    override val description: String get() = item.plan.description
}

@Composable
fun MonthlyPlansScreen(navController: NavHostController, tipo: String) {
    val vm = appViewModel { MonthlyPlansViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current

    var selectedTipo by rememberSaveable {
        mutableStateOf(if (tipo == "pagar" || tipo == "cobrar") tipo else "")
    }
    val kindFilter = when (selectedTipo) {
        "pagar" -> "PAY"
        "cobrar" -> "COLLECT"
        else -> null
    }

    val visible = if (kindFilter != null) {
        state.plans.filter { it.plan.kind == kindFilter }
    } else {
        state.plans
    }
    val active = visible
        .filter { it.plan.active }
        .map { UrgencyRow(it) }
        .sortedWith(PLANS_BY_URGENCY)
        .map { it.item }
    val ended = visible.filter { !it.plan.active }

    // Compromiso mensual: el equivalente a un mes de cada plan activo,
    // separado por dirección porque uno sale de las cuentas y el otro entra.
    val currencyById = state.plans.associate {
        it.plan.currencyId to DisplayCurrencyOf(it.currencyCode, it.currencyDecimals)
    }
    fun totalsText(kind: String): String =
        monthlyCommitmentByCurrency(
            active
                .filter { it.plan.kind == kind }
                .map {
                    PlanLike(
                        active = it.plan.active,
                        amountMinor = it.plan.amountMinor,
                        frequency = it.plan.frequency,
                        currencyId = it.plan.currencyId,
                    )
                }
        ).mapNotNull { total ->
            currencyById[total.currencyId]?.let { fmtMinor(total.amountMinor, it) }
        }.joinToString(" · ")
    val payText = totalsText("PAY")
    val collectText = totalsText("COLLECT")
    val overdueCount = active.count { isOverdue(it) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(
            title = "Mensualidades",
            actions = { HeaderNewButton { navController.navigate(Routes.NEW_PLAN) } },
        ) {
            HeaderTotals(listOf("Pago al mes" to payText, "Cobro al mes" to collectText))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HeaderChip("${active.size} activa${if (active.size == 1) "" else "s"}")
                if (overdueCount > 0) {
                    HeaderChip("$overdueCount con cuota vencida", danger = true)
                }
            }
        }

        Column(
            Modifier
                .contentWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            FilterBar(
                filters = PLAN_FILTERS,
                selected = selectedTipo,
                onSelect = { selectedTipo = it },
                linkLabel = "Deudas",
                linkIcon = Lucide.HandCoins,
                onLink = { navController.navigate(Routes.debts()) },
            )

            if (state.loaded && active.isEmpty() && ended.isEmpty()) {
                EmptyState(
                    icon = Lucide.CalendarClock,
                    title = "Sin mensualidades",
                    description = "Renta, suscripciones, cuotas fijas… crea una y te avisamos de cada vencimiento.",
                    ctaLabel = "Nueva mensualidad",
                    onCta = { navController.navigate(Routes.NEW_PLAN) },
                )
            } else if (active.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    active.forEach { item ->
                        ActivePlanCard(
                            item = item,
                            accounts = state.accounts.filter { it.currencyId == item.plan.currencyId },
                            onOpen = { navController.navigate(Routes.planDetail(item.plan.id)) },
                            onSettle = { installmentId, accountId, amount, done ->
                                vm.settleInstallment(installmentId, accountId, amount) { result ->
                                    done()
                                    when (result) {
                                        is ActionResult.Success -> toast(
                                            if (item.plan.kind == "COLLECT") "Cobro registrado"
                                            else "Pago registrado"
                                        )
                                        is ActionResult.Failure -> toast(result.error)
                                    }
                                }
                            },
                            onSkip = { installmentId, done ->
                                vm.skipInstallment(installmentId) { result ->
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

            if (ended.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Finalizadas")
                    ended.forEach { item ->
                        EndedPlanCard(item) {
                            navController.navigate(Routes.planDetail(item.plan.id))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Vencida = su cuota pendiente más próxima ya pasó (estado derivado). */
private fun isOverdue(item: PlanListRow): Boolean =
    item.nextPendingDueAt?.let { daysUntil(it.toLocalDate()) < 0 } ?: false

private fun paidCountLabel(count: Int): String =
    "$count saldada${if (count == 1) "" else "s"}"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivePlanCard(
    item: PlanListRow,
    accounts: List<AccountEntity>,
    onOpen: () -> Unit,
    onSettle: (installmentId: String, accountId: String, amount: String?, done: () -> Unit) -> Unit,
    onSkip: (installmentId: String, done: () -> Unit) -> Unit,
) {
    val plan = item.plan
    val display = DisplayCurrencyOf(item.currencyCode, item.currencyDecimals)
    val nextId = item.nextPendingId
    val nextDueAt = item.nextPendingDueAt
    val nextAmount = item.nextPendingAmountMinor
    val days = nextDueAt?.let { daysUntil(it.toLocalDate()) }
    val overdue = days != null && days < 0
    val collect = plan.kind == "COLLECT"
    val ext = ChangeboxColors.extended
    val canSettle = nextId != null && nextAmount != null && days != null &&
        days <= SETTLE_WINDOW_DAYS

    val (iconBg, iconFg) = when {
        overdue -> MaterialTheme.colorScheme.error.copy(alpha = 0.13f) to MaterialTheme.colorScheme.error
        collect -> ext.ok.copy(alpha = 0.14f) to ext.ok
        else -> ext.chip to MaterialTheme.colorScheme.primary
    }

    // Borde rojizo si la cuota ya venció (border-danger/35 de la web).
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (overdue) MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(iconBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (collect) Lucide.ArrowDownLeft else Lucide.ArrowUpRight,
                            contentDescription = null,
                            tint = iconFg,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                plan.description,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                fmtMinor(plan.amountMinor, display),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                        }
                        Row(
                            Modifier.padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Lucide.UserRound,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                item.contactName ?: "Sin contacto",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        FlowRow(
                            Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ChangeboxBadge(
                                runCatching { Frequency.valueOf(plan.frequency).labelEs }
                                    .getOrDefault(plan.frequency),
                                BadgeVariant.NEUTRAL,
                            )
                            if (nextDueAt != null && days != null) {
                                ChangeboxBadge(
                                    dueLabel(nextDueAt.toLocalDate()),
                                    dueTone(days).toBadge(),
                                    icon = Lucide.CalendarClock,
                                )
                            }
                            if (item.paidCount > 0) {
                                Text(
                                    paidCountLabel(item.paidCount),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    modifier = Modifier.align(Alignment.CenterVertically),
                                )
                            }
                        }
                    }
                    Box(Modifier.padding(top = 4.dp)) { ChevronIcon() }
                }
            }

            if (canSettle && nextId != null && nextAmount != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    SettleInstallmentRow(
                        accounts = accounts,
                        defaultAmount = minorToInput(nextAmount, display.decimalPlaces),
                        currencyCode = display.code,
                        kind = plan.kind,
                        defaultAccountId = plan.accountId,
                        onSettle = { accountId, amount, done -> onSettle(nextId, accountId, amount, done) },
                        onSkip = { done -> onSkip(nextId, done) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EndedPlanCard(item: PlanListRow, onClick: () -> Unit) {
    val plan = item.plan
    val display = DisplayCurrencyOf(item.currencyCode, item.currencyDecimals)
    ChangeboxCard(corner = 16, onClick = onClick, modifier = Modifier.alpha(0.8f)) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    plan.description,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(
                            runCatching { PlanKind.valueOf(plan.kind).labelEs }
                                .getOrDefault(plan.kind)
                        )
                        item.contactName?.let { append(" · $it") }
                        append(" · ")
                        append(paidCountLabel(item.paidCount))
                    },
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                fmtMinor(plan.amountMinor, display),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ChevronIcon()
        }
    }
}
