package com.lolo.changebox.ui.rates

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
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.ArrowUpDown
import com.composables.icons.lucide.Lucide
import com.lolo.changebox.data.ActionResult
import com.lolo.changebox.data.local.entity.CurrencyEntity
import com.lolo.changebox.di.AppContainer
import com.lolo.changebox.di.appViewModel
import com.lolo.changebox.domain.DisplayCurrencyOf
import com.lolo.changebox.domain.MinorCurrencyOf
import com.lolo.changebox.domain.PairRateLite
import com.lolo.changebox.domain.buildPairMap
import com.lolo.changebox.domain.convertMinor
import com.lolo.changebox.domain.fmtMinor
import com.lolo.changebox.domain.fmtRate
import com.lolo.changebox.domain.parseAmountToMinor
import com.lolo.changebox.domain.resolveRateScaled
import com.lolo.changebox.ui.Routes
import com.lolo.changebox.ui.common.ChangeboxCard
import com.lolo.changebox.ui.common.ChangeboxSelect
import com.lolo.changebox.ui.common.ChangeboxTextField
import com.lolo.changebox.ui.common.ErrorBox
import com.lolo.changebox.ui.common.LocalToast
import com.lolo.changebox.ui.common.PrimaryButton
import com.lolo.changebox.ui.common.RateSparkline
import com.lolo.changebox.ui.common.ScreenHeader
import com.lolo.changebox.ui.common.SectionTitle
import com.lolo.changebox.ui.common.fmtDate
import com.lolo.changebox.ui.common.contentWidth
import com.lolo.changebox.ui.theme.ChangeboxColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Tasas de cambio: registrar tasa por par, pares vigentes con sparkline (→
// detalle), conversor rápido con resolución directo/inverso/vía base e
// histórico plano — port de tasas/page.tsx.

data class RatesState(
    val loaded: Boolean = false,
    val currencies: List<CurrencyEntity> = emptyList(),
    val series: Map<String, List<com.lolo.changebox.data.repo.PairRatePoint>> = emptyMap(),
)

class RatesViewModel(private val container: AppContainer) : ViewModel() {
    val state = combine(
        container.db.catalogDao().currenciesFlow(),
        container.rates.pairSeriesFlow(),
    ) { currencies, series -> RatesState(true, currencies, series) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RatesState())

    fun createRate(
        fromId: String,
        toId: String,
        rate: String,
        onResult: (ActionResult<String>) -> Unit,
    ) {
        viewModelScope.launch { onResult(container.rates.createExchangeRate(fromId, toId, rate)) }
    }
}

private data class PairCard(
    val key: String,
    val fromCode: String,
    val toCode: String,
    val rateScaled: Long,
    val effectiveAt: Long,
    val trend: List<Long>,
)

@Composable
fun RatesScreen(navController: NavHostController) {
    val vm = appViewModel { RatesViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current

    val activeCurrencies = state.currencies.filter { it.active }
    val base = activeCurrencies.firstOrNull { it.isBase }
    val codeById = state.currencies.associate { it.id to it.code }
    val activeIds = activeCurrencies.map { it.id }.toSet()

    // Pares vigentes (solo entre monedas activas) con su tendencia reciente.
    val currentPairs = state.series.entries.mapNotNull { (key, points) ->
        val (fromId, toId) = key.split("→")
        val fromCode = codeById[fromId] ?: return@mapNotNull null
        val toCode = codeById[toId] ?: return@mapNotNull null
        if (fromId !in activeIds || toId !in activeIds) return@mapNotNull null
        val latest = points.last()
        PairCard(
            key = key,
            fromCode = fromCode,
            toCode = toCode,
            rateScaled = latest.rateScaled,
            effectiveAt = latest.effectiveAt,
            trend = points.takeLast(12).map { it.rateScaled },
        )
    }.sortedByDescending { it.effectiveAt }

    val converterPairs = state.series.entries.map { (key, points) ->
        val (fromId, toId) = key.split("→")
        PairRateLite(fromId, toId, points.last().rateScaled)
    }

    // Últimos registros entre todos los pares (histórico plano).
    val history = state.series.entries.flatMap { (key, points) ->
        val (fromId, toId) = key.split("→")
        val fromCode = codeById[fromId] ?: return@flatMap emptyList()
        val toCode = codeById[toId] ?: return@flatMap emptyList()
        points.map { Triple("$fromCode→$toCode", it.rateScaled, it.effectiveAt) }
    }.sortedByDescending { it.third }.take(15)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(title = "Tasas de cambio", onBack = { navController.popBackStack() }) {
            if (base != null) {
                Text(
                    "Moneda base: ${base.code} · se cambia en Monedas",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.5.sp,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable { navController.navigate(Routes.CURRENCIES) },
                )
            }
        }

        Column(
            Modifier
                .contentWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Registrar tasa
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Registrar tasa")
                RateForm(
                    currencies = activeCurrencies,
                    onSubmit = { fromId, toId, rate, done ->
                        vm.createRate(fromId, toId, rate) { result ->
                            when (result) {
                                is ActionResult.Success -> {
                                    toast("Tasa guardada")
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

            // Pares vigentes
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    SectionTitle("Pares vigentes")
                    if (currentPairs.isNotEmpty()) {
                        Text(
                            "Toca un par para ver su evolución en detalle.",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (currentPairs.isEmpty()) {
                    ChangeboxCard(corner = 16) {
                        Text(
                            "Sin tasas registradas todavía. Registra la primera arriba — el par inverso se resuelve solo.",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp, horizontal = 16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                } else {
                    currentPairs.forEach { pair ->
                        ChangeboxCard(onClick = {
                            navController.navigate(Routes.ratePair(pair.fromCode, pair.toCode))
                        }) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Column(Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                pair.fromCode,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Icon(
                                                Lucide.ArrowRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    .copy(alpha = 0.6f),
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Text(
                                                pair.toCode,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                        Text(
                                            fmtRate(pair.rateScaled),
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = (-0.5).sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }
                                    RateSparkline(pair.trend)
                                }
                                Text(
                                    "1 ${pair.fromCode} = ${fmtRate(pair.rateScaled)} ${pair.toCode} · ${fmtDate(pair.effectiveAt)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Conversor rápido
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Conversor rápido")
                Converter(
                    currencies = activeCurrencies,
                    pairs = converterPairs,
                    baseId = base?.id,
                )
            }

            // Histórico
            if (history.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Histórico")
                    history.forEach { (pairLabel, rateScaled, effectiveAt) ->
                        val (fromCode, toCode) = pairLabel.split("→")
                        ChangeboxCard(corner = 13) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "1 $fromCode = ${fmtRate(rateScaled)} $toCode",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    fmtDate(effectiveAt),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
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
private fun RateForm(
    currencies: List<CurrencyEntity>,
    onSubmit: (fromId: String, toId: String, rate: String, done: (Boolean) -> Unit) -> Unit,
) {
    var fromId by remember(currencies) {
        mutableStateOf(currencies.getOrNull(1)?.id ?: currencies.firstOrNull()?.id ?: "")
    }
    var toId by remember(currencies) { mutableStateOf(currencies.firstOrNull()?.id ?: "") }
    var rate by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    val from = currencies.find { it.id == fromId }
    val to = currencies.find { it.id == toId }

    ChangeboxCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    ChangeboxSelect(
                        options = currencies,
                        selected = from,
                        onSelect = { fromId = it.id },
                        display = { it.code },
                        placeholder = "De",
                    )
                }
                Icon(
                    Lucide.ArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
                Box(Modifier.weight(1f)) {
                    ChangeboxSelect(
                        options = currencies,
                        selected = to,
                        onSelect = { toId = it.id },
                        display = { it.code },
                        placeholder = "A",
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    ChangeboxTextField(rate, { rate = it }, placeholder = "435.5", decimal = true)
                }
                PrimaryButton(
                    if (saving) "…" else "Guardar",
                    enabled = !saving && rate.isNotBlank() && fromId.isNotEmpty() &&
                        toId.isNotEmpty() && fromId != toId,
                    onClick = {
                        saving = true
                        onSubmit(fromId, toId, rate) { okDone ->
                            saving = false
                            if (okDone) rate = ""
                        }
                    },
                )
            }
            if (from != null && to != null) {
                Text(
                    if (from.id == to.id) {
                        "Elige dos monedas distintas"
                    } else {
                        "Cuántos ${to.code} vale 1 ${from.code} (hasta 4 decimales). El par inverso ${to.code} → ${from.code} se calcula solo."
                    },
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Converter(
    currencies: List<CurrencyEntity>,
    pairs: List<PairRateLite>,
    baseId: String?,
) {
    var amount by remember { mutableStateOf("100") }
    var fromId by remember(currencies) {
        mutableStateOf(currencies.getOrNull(1)?.id ?: currencies.firstOrNull()?.id ?: "")
    }
    var toId by remember(currencies) { mutableStateOf(currencies.firstOrNull()?.id ?: "") }

    val pairMap = remember(pairs) { buildPairMap(pairs) }
    val from = currencies.find { it.id == fromId }
    val to = currencies.find { it.id == toId }

    var result: String? = null
    var rateInfo: String? = null
    var warning: String? = null

    if (from != null && to != null && amount.isNotBlank()) {
        val rate = resolveRateScaled(pairMap, from.id, to.id, baseId)
        if (rate == null) {
            warning = "No hay tasa registrada que conecte ${from.code} con ${to.code}."
        } else {
            try {
                val minor = parseAmountToMinor(amount, MinorCurrencyOf(from.decimalPlaces))
                result = fmtMinor(
                    convertMinor(
                        minor,
                        MinorCurrencyOf(from.decimalPlaces),
                        MinorCurrencyOf(to.decimalPlaces),
                        rate,
                    ),
                    DisplayCurrencyOf(to.code, to.decimalPlaces),
                )
                rateInfo = "1 ${from.code} = ${fmtRate(rate)} ${to.code}"
            } catch (e: Exception) {
                warning = "Monto inválido"
            }
        }
    }

    ChangeboxCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Monto a lo ancho y selects debajo: en 360dp la fila única no cabe
            ChangeboxTextField(amount, { amount = it }, placeholder = "0", decimal = true)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    ChangeboxSelect(
                        options = currencies,
                        selected = from,
                        onSelect = { fromId = it.id },
                        display = { it.code },
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(ChangeboxColors.extended.chip)
                        .clickable {
                            val previous = fromId
                            fromId = toId
                            toId = previous
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Lucide.ArrowUpDown,
                        contentDescription = "Intercambiar monedas",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Box(Modifier.weight(1f)) {
                    ChangeboxSelect(
                        options = currencies,
                        selected = to,
                        onSelect = { toId = it.id },
                        display = { it.code },
                    )
                }
            }

            androidx.compose.material3.HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant
            )

            when {
                warning != null -> Text(
                    warning,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                result != null -> Column {
                    Text(
                        result,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    rateInfo?.let {
                        Text(
                            it,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> Text(
                    "Escribe un monto para convertir.",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

