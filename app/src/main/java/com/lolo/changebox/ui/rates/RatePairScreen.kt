package com.lolo.changebox.ui.rates

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TrendingDown
import com.composables.icons.lucide.TrendingUp
import com.lolo.changebox.data.local.entity.ExchangeRateEntity
import com.lolo.changebox.di.AppContainer
import com.lolo.changebox.di.appViewModel
import com.lolo.changebox.domain.MoneyException
import com.lolo.changebox.domain.fmtRate
import com.lolo.changebox.domain.invertRateScaled
import com.lolo.changebox.ui.common.ChangeboxCard
import com.lolo.changebox.ui.common.RateLineChart
import com.lolo.changebox.ui.common.RatePoint
import com.lolo.changebox.ui.common.ScreenHeader
import com.lolo.changebox.ui.common.SectionTitle
import com.lolo.changebox.ui.common.fmtDate
import com.lolo.changebox.ui.common.contentWidth
import com.lolo.changebox.ui.theme.ChangeboxColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

// Detalle de un par de tasas: última cotización + inverso, variación vs
// registro anterior, gráfico lineal interactivo e histórico del par — port
// de tasas/[from]/[to]/page.tsx (los códigos de moneda viajan en la ruta).

data class RatePairState(
    val loaded: Boolean = false,
    val valid: Boolean = false,
    val history: List<ExchangeRateEntity> = emptyList(),
)

class RatePairViewModel(
    container: AppContainer,
    fromCode: String,
    toCode: String,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state = combine(
        container.db.catalogDao().currenciesFlow(),
        flowOf(Unit),
    ) { currencies, _ ->
        val from = currencies.find { it.code.equals(fromCode, ignoreCase = true) }
        val to = currencies.find { it.code.equals(toCode, ignoreCase = true) }
        from to to
    }.flatMapLatest { (from, to) ->
        if (from == null || to == null || from.id == to.id) {
            flowOf(RatePairState(loaded = true, valid = false))
        } else {
            container.rates.pairHistoryFlow(from.id, to.id).flatMapLatest { history ->
                flowOf(RatePairState(loaded = true, valid = true, history = history))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RatePairState())
}

@Composable
fun RatePairScreen(navController: NavHostController, fromParam: String, toParam: String) {
    val fromCode = fromParam.uppercase()
    val toCode = toParam.uppercase()
    val vm = appViewModel(key = "tasa-$fromCode-$toCode") {
        RatePairViewModel(it, fromCode, toCode)
    }
    val state by vm.state.collectAsStateWithLifecycle()

    val history = state.history
    val latest = history.lastOrNull()
    val previous = history.getOrNull(history.size - 2)

    // Variación frente al registro anterior (puntos escalados → %).
    val deltaScaled = if (latest != null && previous != null) {
        latest.rateScaled - previous.rateScaled
    } else {
        null
    }
    val deltaPct = if (deltaScaled != null && previous != null && previous.rateScaled > 0) {
        deltaScaled.toDouble() / previous.rateScaled * 100
    } else {
        null
    }

    val inverseScaled = latest?.let {
        try {
            invertRateScaled(it.rateScaled)
        } catch (e: MoneyException) {
            null
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(title = "$fromCode → $toCode", onBack = { navController.popBackStack() }) {
            if (latest != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        fmtRate(latest.rateScaled),
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.8).sp,
                    )
                    Text(
                        " $toCode",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Text(
                    buildString {
                        append("1 $fromCode = ${fmtRate(latest.rateScaled)} $toCode · ")
                        append(fmtDate(latest.effectiveAt))
                        if (inverseScaled != null) {
                            append(" · inverso: 1 $toCode = ${fmtRate(inverseScaled)} $fromCode")
                        }
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else if (state.loaded) {
                Text(
                    "Sin tasas registradas para este par.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Column(
            Modifier
                .contentWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (state.loaded && (!state.valid || history.isEmpty())) {
                ChangeboxCard(corner = 16) {
                    Text(
                        "Este par no tiene tasas registradas todavía. Registra la primera en Tasas.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                return@Column
            }

            // Evolución
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionTitle("Evolución") }
                    if (deltaScaled != null && deltaPct != null) {
                        val ext = ChangeboxColors.extended
                        val (bg, fg) = when {
                            deltaScaled == 0L ->
                                ext.chip to MaterialTheme.colorScheme.onSurfaceVariant
                            deltaScaled > 0 -> ext.ok.copy(alpha = 0.14f) to ext.ok
                            else -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f) to
                                MaterialTheme.colorScheme.error
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(bg)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (deltaScaled != 0L) {
                                Icon(
                                    if (deltaScaled > 0) Lucide.TrendingUp else Lucide.TrendingDown,
                                    contentDescription = null,
                                    tint = fg,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                            Text(
                                buildString {
                                    if (deltaScaled > 0) append("+")
                                    append(
                                        String.format(java.util.Locale.US, "%.2f", deltaPct)
                                            .trimEnd('0').trimEnd('.')
                                    )
                                    append("% vs anterior")
                                },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = fg,
                            )
                        }
                    }
                }
                ChangeboxCard {
                    Column(Modifier.padding(16.dp)) {
                        if (history.size == 1) {
                            Text(
                                "Solo hay un registro — el gráfico aparecerá cuando el par tenga al menos dos tasas.",
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        } else {
                            RateLineChart(
                                points = history.map { RatePoint(it.effectiveAt, it.rateScaled) },
                                fromCode = fromCode,
                                toCode = toCode,
                            )
                        }
                        Text(
                            "${history.size} ${if (history.size == 1) "registro" else "registros"} · desde ${fmtDate(history.first().effectiveAt)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }

            // Histórico del par
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Histórico del par")
                history.reversed().forEach { rate ->
                    ChangeboxCard(corner = 13) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    fromCode,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Icon(
                                    Lucide.ArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    "${fmtRate(rate.rateScaled)} $toCode",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                fmtDate(rate.effectiveAt),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

