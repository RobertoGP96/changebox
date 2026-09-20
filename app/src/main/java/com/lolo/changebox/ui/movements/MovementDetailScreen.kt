package com.lolo.changebox.ui.movements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.composables.icons.lucide.HandCoins
import com.composables.icons.lucide.Lucide
import com.lolo.changebox.data.local.dao.DebtLinkRow
import com.lolo.changebox.data.local.dao.PlanLinkRow
import com.lolo.changebox.data.local.dao.TxDenomLineRow
import com.lolo.changebox.data.local.dao.TxDetailRow
import com.lolo.changebox.di.AppContainer
import com.lolo.changebox.di.appViewModel
import com.lolo.changebox.domain.DisplayCurrencyOf
import com.lolo.changebox.domain.TransactionKind
import com.lolo.changebox.domain.fmtMinor
import com.lolo.changebox.domain.fmtRate
import com.lolo.changebox.domain.fmtSignedMinor
import com.lolo.changebox.ui.Routes
import com.lolo.changebox.ui.common.BadgeVariant
import com.lolo.changebox.ui.common.ChangeboxBadge
import com.lolo.changebox.ui.common.ChangeboxCard
import com.lolo.changebox.ui.common.DetailRow
import com.lolo.changebox.ui.common.IconChip
import com.lolo.changebox.ui.common.ScreenHeader
import com.lolo.changebox.ui.common.SectionTitle
import com.lolo.changebox.ui.common.fmtDateTime
import com.lolo.changebox.ui.common.contentWidth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

// Detalle de movimiento: cuentas origen/destino, monto original multi-moneda,
// tasa, categoría, nota, fechas, desglose por lado y el vínculo a deuda/plan
// — port de movimientos/[id]/page.tsx.

data class MovementDetailState(
    val loaded: Boolean = false,
    val tx: TxDetailRow? = null,
    val lines: List<TxDenomLineRow> = emptyList(),
    val debtLink: DebtLinkRow? = null,
    val planLink: PlanLinkRow? = null,
)

class MovementDetailViewModel(container: AppContainer, txId: String) : ViewModel() {
    val state = combine(
        container.ledger.txDetailFlow(txId),
        container.ledger.txDenominationLinesFlow(txId),
        container.ledger.debtLinkFlow(txId),
        container.ledger.planLinkFlow(txId),
    ) { tx, lines, debtLink, planLink ->
        MovementDetailState(true, tx, lines, debtLink, planLink)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MovementDetailState())
}

@Composable
fun MovementDetailScreen(navController: NavHostController, txId: String) {
    val vm = appViewModel(key = "movimiento-$txId") { MovementDetailViewModel(it, txId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val tx = state.tx

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val kindLabel = tx?.let {
            runCatching { TransactionKind.valueOf(it.kind).labelEs }.getOrDefault(it.kind)
        } ?: ""
        // Signo desde la cuenta de origen (en TRANSFER el destino recibe aparte).
        val signedMinor = tx?.let {
            if (it.kind == "EXPENSE" || it.kind == "TRANSFER") -it.amountMinor else it.amountMinor
        } ?: 0L

        ScreenHeader(
            title = "Detalle del movimiento",
            onBack = { navController.popBackStack() },
        ) {
            if (tx != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            kindLabel.uppercase(),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.8.sp,
                        )
                        Text(
                            fmtSignedMinor(
                                signedMinor,
                                DisplayCurrencyOf(tx.currencyCode, tx.currencyDecimals),
                            ),
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                        )
                    }
                    ChangeboxBadge(kindLabel, BadgeVariant.NEUTRAL)
                }
            }
        }

        if (tx == null) {
            if (state.loaded) {
                Text(
                    "Movimiento no encontrado.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
            return@Column
        }

        val isTransfer = tx.kind == "TRANSFER"
        // INCOME/EXPENSE multi-moneda: el original quedó en counterAmountMinor.
        val crossCurrency = !isTransfer && tx.counterAmountMinor != null &&
            tx.counterCurrencyCode != null
        val counterDisplay = tx.counterCurrencyCode?.let {
            DisplayCurrencyOf(it, tx.counterCurrencyDecimals ?: 2)
        }

        Column(
            Modifier
                .contentWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ChangeboxCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    DetailRow("Fecha") { DetailValue(fmtDateTime(tx.occurredAt)) }
                    RowDivider()
                    DetailRow(if (isTransfer) "Cuenta de origen" else "Cuenta") {
                        Text(
                            tx.accountName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                navController.navigate(Routes.accountDetail(tx.accountId))
                            },
                        )
                    }
                    if (isTransfer && tx.counterAccountName != null) {
                        RowDivider()
                        DetailRow("Cuenta de destino") {
                            Text(
                                tx.counterAccountName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    tx.counterAccountId?.let {
                                        navController.navigate(Routes.accountDetail(it))
                                    }
                                },
                            )
                        }
                    }
                    RowDivider()
                    DetailRow(if (isTransfer) "Monto enviado" else "Monto") {
                        DetailValue(
                            fmtMinor(
                                tx.amountMinor,
                                DisplayCurrencyOf(tx.currencyCode, tx.currencyDecimals),
                            )
                        )
                    }
                    if (isTransfer && tx.counterAmountMinor != null && counterDisplay != null) {
                        RowDivider()
                        DetailRow("Monto recibido") {
                            DetailValue(fmtMinor(tx.counterAmountMinor, counterDisplay))
                        }
                    }
                    if (crossCurrency && counterDisplay != null) {
                        RowDivider()
                        DetailRow("Monto original") {
                            DetailValue(fmtMinor(tx.counterAmountMinor!!, counterDisplay))
                        }
                    }
                    if (tx.rateScaled != null && tx.counterCurrencyCode != null) {
                        RowDivider()
                        DetailRow("Tasa aplicada") {
                            DetailValue(
                                "1 ${tx.currencyCode} = ${fmtRate(tx.rateScaled)} ${tx.counterCurrencyCode}"
                            )
                        }
                    }
                    if (tx.categoryName != null) {
                        RowDivider()
                        DetailRow("Categoría") { DetailValue(tx.categoryName) }
                    }
                    if (tx.note != null) {
                        RowDivider()
                        DetailRow("Nota") { DetailValue(tx.note) }
                    }
                    RowDivider()
                    DetailRow("Registrado el") { DetailValue(fmtDateTime(tx.createdAt)) }
                }
            }

            // Desglose por lado (en TRANSFER puede haber salida y entrada);
            // dentro de cada lado, mayor valor primero.
            if (state.lines.isNotEmpty()) {
                val sides = state.lines
                    .groupBy { it.accountId }
                    .map { (sideAccountId, lines) ->
                        Triple(
                            sideAccountId,
                            lines.first().accountName,
                            lines.sortedWith(
                                compareBy<TxDenomLineRow> { it.kind }
                                    .thenByDescending { it.valueMinor }
                            ),
                        )
                    }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Desglose de denominaciones")
                    sides.forEach { (sideAccountId, sideAccountName, lines) ->
                        val isOrigin = sideAccountId == tx.accountId
                        val sideCurrency = if (isOrigin) {
                            DisplayCurrencyOf(tx.currencyCode, tx.currencyDecimals)
                        } else {
                            counterDisplay ?: DisplayCurrencyOf(tx.currencyCode, tx.currencyDecimals)
                        }
                        val enters = tx.kind == "INCOME" || !isOrigin
                        ChangeboxCard(corner = 16) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text(
                                    "${if (enters) "Entra en" else "Sale de"} «$sideAccountName»",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                lines.forEach { line ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.Bottom,
                                    ) {
                                        Text(
                                            buildString {
                                                append(fmtMinor(line.valueMinor, sideCurrency))
                                                append(" × ${line.quantity}")
                                            },
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            fmtMinor(line.valueMinor * line.quantity, sideCurrency),
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Vínculo con deudas: abono directo o cuota de un plan.
            val debtLink = state.debtLink
            val planLink = state.planLink
            val linkedDebtId = debtLink?.debtId ?: planLink?.debtId
            if (debtLink != null || planLink != null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Vinculado a")
                    ChangeboxCard(corner = 16, onClick = {
                        if (linkedDebtId != null) {
                            navController.navigate(Routes.debtDetail(linkedDebtId))
                        } else if (planLink != null) {
                            navController.navigate(Routes.planDetail(planLink.planId))
                        }
                    }) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            IconChip(Lucide.HandCoins, size = 36, corner = 12)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when {
                                        debtLink != null -> "Deuda con ${debtLink.contactName}"
                                        planLink?.debtContactName != null ->
                                            "Deuda con ${planLink.debtContactName}"
                                        else -> planLink?.description ?: ""
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    buildString {
                                        when {
                                            debtLink != null -> append(debtLink.description)
                                            planLink?.debtDescription != null ->
                                                append(planLink.debtDescription)
                                            else -> append("Plan de cuotas")
                                        }
                                        planLink?.contactName?.let { append(" · $it") }
                                    },
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DetailValue(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = androidx.compose.ui.text.style.TextAlign.End,
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

