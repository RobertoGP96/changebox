package com.lolo.changebox.ui.catalog

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Star
import com.lolo.changebox.data.ActionResult
import com.lolo.changebox.data.local.dao.CurrencyWithCounts
import com.lolo.changebox.di.AppContainer
import com.lolo.changebox.di.appViewModel
import com.lolo.changebox.ui.Routes
import com.lolo.changebox.ui.common.BadgeVariant
import com.lolo.changebox.ui.common.ChangeboxBadge
import com.lolo.changebox.ui.common.ChangeboxCard
import com.lolo.changebox.ui.common.ChangeboxSelect
import com.lolo.changebox.ui.common.ChangeboxTextField
import com.lolo.changebox.ui.common.ErrorBox
import com.lolo.changebox.ui.common.GhostButton
import com.lolo.changebox.ui.common.LocalToast
import com.lolo.changebox.ui.common.OutlineButton
import com.lolo.changebox.ui.common.PrimaryButton
import com.lolo.changebox.ui.common.ScreenHeader
import com.lolo.changebox.ui.common.contentWidth
import com.lolo.changebox.ui.theme.ChangeboxColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Monedas: crear, ocultar/activar, cambiar la base (con confirmación) y
// acceso a las denominaciones — port de monedas/page + currency-manager.

class CurrenciesViewModel(private val container: AppContainer) : ViewModel() {
    val currencies = container.db.catalogDao().currenciesWithCountsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(
        code: String,
        name: String,
        symbol: String,
        decimals: Int,
        onResult: (ActionResult<String>) -> Unit,
    ) {
        viewModelScope.launch {
            onResult(container.catalog.createCurrency(code, name, symbol, decimals))
        }
    }

    fun toggle(id: String, onResult: (ActionResult<Boolean>) -> Unit) {
        viewModelScope.launch { onResult(container.catalog.toggleCurrency(id)) }
    }

    fun setBase(id: String, onResult: (ActionResult<String>) -> Unit) {
        viewModelScope.launch { onResult(container.catalog.setBaseCurrency(id)) }
    }
}

@Composable
fun CurrenciesScreen(navController: NavHostController) {
    val vm = appViewModel { CurrenciesViewModel(it) }
    val currencies by vm.currencies.collectAsStateWithLifecycle()
    val toast = LocalToast.current

    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf("$") }
    var decimals by remember { mutableStateOf(2) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var confirmBaseId by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(title = "Monedas", onBack = { navController.popBackStack() }) {
            Text(
                "Divisas del sistema y sus denominaciones",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.5.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Column(
            Modifier
                .contentWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Alta de moneda
            ChangeboxCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Nueva moneda",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.width(90.dp)) {
                            ChangeboxTextField(
                                code,
                                { if (it.length <= 6) code = it.uppercase() },
                                placeholder = "MXN",
                            )
                        }
                        Box(Modifier.width(64.dp)) {
                            ChangeboxTextField(symbol, { if (it.length <= 4) symbol = it }, placeholder = "$")
                        }
                        Box(Modifier.weight(1f)) {
                            ChangeboxTextField(
                                name,
                                { if (it.length <= 60) name = it },
                                placeholder = "Peso mexicano",
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(160.dp)) {
                            ChangeboxSelect(
                                options = listOf(0, 2, 3, 4),
                                selected = decimals,
                                onSelect = { decimals = it },
                                display = { "$it decimales" },
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        PrimaryButton(
                            "Añadir",
                            enabled = !saving && code.isNotBlank() && name.isNotBlank() &&
                                symbol.isNotBlank(),
                            onClick = {
                                saving = true
                                error = null
                                vm.create(code, name, symbol, decimals) { result ->
                                    saving = false
                                    when (result) {
                                        is ActionResult.Success -> {
                                            toast("Moneda creada")
                                            code = ""
                                            name = ""
                                            symbol = "$"
                                            decimals = 2
                                        }
                                        is ActionResult.Failure -> error = result.error
                                    }
                                }
                            },
                        )
                    }
                    error?.let { ErrorBox(it) }
                }
            }

            currencies.forEach { item ->
                val currency = item.currency
                val alpha = if (currency.active) 1f else 0.55f
                ChangeboxCard {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.clickable {
                                navController.navigate(Routes.currencyDetail(currency.id))
                            },
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(ChangeboxColors.extended.chip),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    currency.symbol,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        currency.code,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                                    )
                                    if (currency.isBase) ChangeboxBadge("Base", BadgeVariant.FEATURED)
                                    if (!currency.active) ChangeboxBadge("Oculta", BadgeVariant.NEUTRAL)
                                }
                                Text(
                                    buildString {
                                        append("${currency.name} · ${currency.decimalPlaces} dec · ")
                                        append("${item.denominationCount} denominaciones")
                                        if (item.accountCount > 0) {
                                            append(" · ${item.accountCount} cuentas")
                                        }
                                    },
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                Lucide.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        if (!currency.isBase) {
                            HorizontalDivider(
                                Modifier.padding(vertical = 10.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (confirmBaseId == currency.id) {
                                    Text(
                                        "Revisa las tasas tras el cambio",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    PrimaryButton("Confirmar base", onClick = {
                                        confirmBaseId = null
                                        vm.setBase(currency.id) { result ->
                                            when (result) {
                                                is ActionResult.Success ->
                                                    toast("Moneda base actualizada")
                                                is ActionResult.Failure -> toast(result.error)
                                            }
                                        }
                                    })
                                    GhostButton("Cancelar", onClick = { confirmBaseId = null })
                                } else {
                                    OutlineButton(
                                        "Hacer base",
                                        onClick = { confirmBaseId = currency.id },
                                        icon = Lucide.Star,
                                    )
                                    GhostButton(
                                        if (currency.active) "Ocultar" else "Activar",
                                        onClick = {
                                            vm.toggle(currency.id) { result ->
                                                when (result) {
                                                    is ActionResult.Success -> toast(
                                                        if (result.data) "Moneda activada"
                                                        else "Moneda oculta"
                                                    )
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
            }

            Text(
                "Las tasas de cambio se cotizan contra la moneda base. Si cambias la base, registra tasas nuevas en Tasas.",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

