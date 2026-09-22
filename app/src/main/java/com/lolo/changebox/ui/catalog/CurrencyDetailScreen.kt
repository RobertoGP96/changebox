package com.lolo.changebox.ui.catalog

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.composables.icons.lucide.Banknote
import com.composables.icons.lucide.CreditCard
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Trash2
import com.lolo.changebox.data.ActionResult
import com.lolo.changebox.data.local.dao.DenominationWithUsage
import com.lolo.changebox.data.local.entity.CurrencyEntity
import com.lolo.changebox.di.AppContainer
import com.lolo.changebox.di.appViewModel
import com.lolo.changebox.domain.CurrencyKind
import com.lolo.changebox.domain.DenominationKind
import com.lolo.changebox.domain.DisplayCurrencyOf
import com.lolo.changebox.domain.fmtMinor
import com.lolo.changebox.domain.isDigitalCurrencyKind
import com.lolo.changebox.domain.minorToAmountInput
import com.lolo.changebox.ui.common.BadgeVariant
import com.lolo.changebox.ui.common.ChangeboxBadge
import com.lolo.changebox.ui.common.ChangeboxCard
import com.lolo.changebox.ui.common.ChangeboxTextField
import com.lolo.changebox.ui.common.ErrorBox
import com.lolo.changebox.ui.common.GhostButton
import com.lolo.changebox.ui.common.IconChip
import com.lolo.changebox.ui.common.InlineConfirm
import com.lolo.changebox.ui.common.LocalToast
import com.lolo.changebox.ui.common.OutlineButton
import com.lolo.changebox.ui.common.PrimaryButton
import com.lolo.changebox.ui.common.ScreenHeader
import com.lolo.changebox.ui.common.SectionTitle
import com.lolo.changebox.ui.common.SegmentedTabs
import com.lolo.changebox.ui.common.contentWidth
import com.lolo.changebox.domain.MinorCurrencyOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Denominaciones de una moneda: añadir, editar (sin uso), ocultar y eliminar
// (sin uso) agrupadas por billetes/monedas — port de monedas/[id].

data class CurrencyDetailState(
    val loaded: Boolean = false,
    val currency: CurrencyEntity? = null,
    val denominations: List<DenominationWithUsage> = emptyList(),
)

class CurrencyDetailViewModel(
    private val container: AppContainer,
    private val currencyId: String,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state = container.db.catalogDao().currencyFlow(currencyId).flatMapLatest { currency ->
        if (currency == null) {
            flowOf(CurrencyDetailState(loaded = true))
        } else {
            combine(
                container.db.catalogDao().denominationsWithUsageFlow(currencyId),
                flowOf(currency),
            ) { denominations, c ->
                CurrencyDetailState(true, c, denominations)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CurrencyDetailState())

    fun setKind(kind: CurrencyKind, onResult: (ActionResult<String>) -> Unit) {
        viewModelScope.launch { onResult(container.catalog.setCurrencyKind(currencyId, kind)) }
    }

    fun create(value: String, kind: String, onResult: (ActionResult<String>) -> Unit) {
        viewModelScope.launch {
            onResult(container.catalog.createDenomination(currencyId, value, kind))
        }
    }

    fun update(id: String, value: String, kind: String, onResult: (ActionResult<String>) -> Unit) {
        viewModelScope.launch { onResult(container.catalog.updateDenomination(id, value, kind)) }
    }

    fun toggle(id: String, onResult: (ActionResult<Boolean>) -> Unit) {
        viewModelScope.launch { onResult(container.catalog.toggleDenomination(id)) }
    }

    fun delete(id: String, onResult: (ActionResult<String>) -> Unit) {
        viewModelScope.launch { onResult(container.catalog.deleteDenomination(id)) }
    }
}

@Composable
fun CurrencyDetailScreen(navController: NavHostController, currencyId: String) {
    val vm = appViewModel(key = "moneda-$currencyId") { CurrencyDetailViewModel(it, currencyId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current

    val currency = state.currency
    val display = currency?.let { DisplayCurrencyOf(it.code, it.decimalPlaces) }

    var value by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("BILL") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editValue by remember { mutableStateOf("") }
    var editKind by remember { mutableStateOf("BILL") }
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }
    // Conversión efectivo ⇄ digital (con confirmación inline).
    var confirmingKind by remember { mutableStateOf(false) }
    var savingKind by remember { mutableStateOf(false) }
    var kindError by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(
            title = currency?.let { "${it.code} · ${it.name}" } ?: "Moneda",
            onBack = { navController.popBackStack() },
        ) {
            if (currency != null) {
                Text(
                    buildString {
                        append("${currency.decimalPlaces} decimales")
                        if (currency.isBase) append(" · Moneda base")
                        if (isDigitalCurrencyKind(currency.kind)) {
                            append(" · Digital (sin efectivo)")
                        } else if (state.denominations.isEmpty()) {
                            append(" · Sin denominaciones")
                        }
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        if (currency == null || display == null) return@Column

        Column(
            Modifier
                .contentWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val isDigital = isDigitalCurrencyKind(currency.kind)
            val nextKind = if (isDigital) CurrencyKind.CASH else CurrencyKind.DIGITAL

            // Clasificación de la moneda (efectivo ⇄ digital) con confirmación
            // inline; pasar a digital lo valida el repo: sin denominaciones ni
            // cuentas de efectivo/caja en esta moneda.
            ChangeboxCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconChip(
                            if (isDigital) Lucide.CreditCard else Lucide.Banknote,
                            size = 40,
                            corner = 12,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                CurrencyKind.from(currency.kind).labelEs,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                if (isDigital) "Solo saldo: sin denominaciones ni cuentas de caja."
                                else "Admite denominaciones, arqueos y cuentas de caja.",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (confirmingKind) {
                        InlineConfirm(
                            text = if (isDigital) "¿Convertir a efectivo?"
                            else "¿Convertir a digital?",
                            confirmLabel = if (savingKind) "Guardando…" else "Confirmar",
                            onConfirm = {
                                savingKind = true
                                kindError = null
                                vm.setKind(nextKind) { result ->
                                    savingKind = false
                                    confirmingKind = false
                                    when (result) {
                                        is ActionResult.Success -> toast(
                                            if (nextKind == CurrencyKind.DIGITAL)
                                                "Moneda marcada como digital"
                                            else "Moneda marcada como efectivo"
                                        )
                                        is ActionResult.Failure -> kindError = result.error
                                    }
                                }
                            },
                            onCancel = { confirmingKind = false },
                            busy = savingKind,
                        )
                    } else {
                        OutlineButton(
                            if (isDigital) "Convertir a efectivo" else "Convertir a digital",
                            onClick = {
                                kindError = null
                                confirmingKind = true
                            },
                        )
                    }
                    kindError?.let { ErrorBox(it) }
                }
            }

            // La web oculta la gestión de denominaciones en las monedas
            // digitales: no existen en efectivo, así que no llevan billetes.
            if (isDigital) {
                ChangeboxCard(corner = 16) {
                    Text(
                        "Esta moneda es digital: no existe en efectivo, así que no lleva billetes ni monedas. Conviértela a efectivo si necesitas denominaciones.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                return@Column
            }

            // Alta de denominación
            ChangeboxCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Nueva denominación",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    SegmentedTabs(
                        options = DenominationKind.entries.map { it.name to it.labelEs },
                        selectedKey = kind,
                        onSelect = { kind = it },
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            ChangeboxTextField(
                                value,
                                { value = it },
                                placeholder = if (currency.decimalPlaces > 0) "0.25" else "1000",
                                decimal = true,
                            )
                        }
                        PrimaryButton(
                            "Añadir",
                            enabled = !saving && value.isNotBlank(),
                            onClick = {
                                saving = true
                                error = null
                                vm.create(value, kind) { result ->
                                    saving = false
                                    when (result) {
                                        is ActionResult.Success -> {
                                            toast("Denominación añadida")
                                            value = ""
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

            // Grupos billetes/monedas
            DenominationKind.entries.forEach { groupKind ->
                val items = state.denominations.filter { it.denomination.kind == groupKind.name }
                if (items.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle("${groupKind.labelEs}s")
                        items.forEach { item ->
                            val d = item.denomination
                            val alpha = if (d.active) 1f else 0.55f
                            ChangeboxCard(corner = 13) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    when {
                                        editingId == d.id -> {
                                            Column(
                                                Modifier.padding(vertical = 6.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                SegmentedTabs(
                                                    options = DenominationKind.entries.map {
                                                        it.name to it.labelEs
                                                    },
                                                    selectedKey = editKind,
                                                    onSelect = { editKind = it },
                                                )
                                                ChangeboxTextField(
                                                    editValue, { editValue = it },
                                                    decimal = true,
                                                )
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    PrimaryButton(
                                                        "Guardar",
                                                        enabled = !saving && editValue.isNotBlank(),
                                                        onClick = {
                                                            saving = true
                                                            vm.update(d.id, editValue, editKind) { result ->
                                                                saving = false
                                                                when (result) {
                                                                    is ActionResult.Success -> {
                                                                        toast("Denominación actualizada")
                                                                        editingId = null
                                                                    }
                                                                    is ActionResult.Failure ->
                                                                        toast(result.error)
                                                                }
                                                            }
                                                        },
                                                    )
                                                    GhostButton(
                                                        "Cancelar",
                                                        onClick = { editingId = null },
                                                        enabled = !saving,
                                                    )
                                                }
                                            }
                                        }
                                        confirmDeleteId == d.id -> {
                                            Box(Modifier.padding(vertical = 6.dp)) {
                                                InlineConfirm(
                                                    text = "¿Eliminar ${fmtMinor(d.valueMinor, display)}?",
                                                    confirmLabel = "Eliminar",
                                                    onConfirm = {
                                                        saving = true
                                                        vm.delete(d.id) { result ->
                                                            saving = false
                                                            confirmDeleteId = null
                                                            when (result) {
                                                                is ActionResult.Success ->
                                                                    toast("Denominación eliminada")
                                                                is ActionResult.Failure ->
                                                                    toast(result.error)
                                                            }
                                                        }
                                                    },
                                                    onCancel = { confirmDeleteId = null },
                                                    busy = saving,
                                                )
                                            }
                                        }
                                        else -> {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Text(
                                                    fmtMinor(d.valueMinor, display),
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                        .copy(alpha = alpha),
                                                    modifier = Modifier.weight(1f),
                                                )
                                                if (item.usageCount > 0) {
                                                    ChangeboxBadge(
                                                        "${item.usageCount}",
                                                        BadgeVariant.NEUTRAL,
                                                    )
                                                }
                                                DenominationMenu(
                                                    active = d.active,
                                                    onEdit = {
                                                        // Las usadas no se editan: alteraría lo guardado.
                                                        if (item.usageCount > 0) {
                                                            toast("Ya se usó en arqueos; ocúltala y crea una nueva")
                                                        } else {
                                                            confirmDeleteId = null
                                                            editingId = d.id
                                                            editValue = minorToAmountInput(
                                                                d.valueMinor,
                                                                MinorCurrencyOf(currency.decimalPlaces),
                                                            )
                                                            editKind = d.kind
                                                        }
                                                    },
                                                    onToggle = {
                                                        vm.toggle(d.id) { result ->
                                                            when (result) {
                                                                is ActionResult.Success -> toast(
                                                                    if (result.data) "Denominación activada"
                                                                    else "Denominación oculta"
                                                                )
                                                                is ActionResult.Failure ->
                                                                    toast(result.error)
                                                            }
                                                        }
                                                    },
                                                    onDelete = {
                                                        if (item.usageCount > 0) {
                                                            toast("Se usó en arqueos guardados; ocúltala en su lugar")
                                                        } else {
                                                            editingId = null
                                                            confirmDeleteId = d.id
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Text(
                "Las denominaciones usadas en arqueos guardados no se pueden editar ni eliminar; ocúltalas y dejarán de aparecer en los arqueos nuevos.",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DenominationMenu(
    active: Boolean,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Lucide.EllipsisVertical,
                contentDescription = "Opciones",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Editar", fontSize = 13.sp) },
                leadingIcon = { Icon(Lucide.Pencil, null, Modifier.size(15.dp)) },
                onClick = {
                    open = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(if (active) "Ocultar" else "Activar", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(if (active) Lucide.EyeOff else Lucide.Eye, null, Modifier.size(15.dp))
                },
                onClick = {
                    open = false
                    onToggle()
                },
            )
            DropdownMenuItem(
                text = {
                    Text("Eliminar", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                },
                leadingIcon = {
                    Icon(
                        Lucide.Trash2, null, Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    open = false
                    onDelete()
                },
            )
        }
    }
}

