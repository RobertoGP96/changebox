package com.lolo.changebox.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowDown
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PencilLine
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.Trash2
import com.lolo.changebox.data.ActionResult
import com.lolo.changebox.data.local.entity.CurrencyEntity
import com.lolo.changebox.data.repo.AccountWithBalance
import com.lolo.changebox.domain.DASHBOARD_SECTIONS
import com.lolo.changebox.domain.DEFAULT_WIDGET_SIZE
import com.lolo.changebox.domain.DashboardPrefs
import com.lolo.changebox.domain.DashboardSectionPref
import com.lolo.changebox.domain.DashboardWidget
import com.lolo.changebox.domain.INCOME_CARD_METRICS
import com.lolo.changebox.domain.INCOME_CARD_PERIODS
import com.lolo.changebox.domain.INCOME_CARD_VARIANTS
import com.lolo.changebox.domain.IncomeCardMetric
import com.lolo.changebox.domain.IncomeCardPeriod
import com.lolo.changebox.domain.IncomeCardVariant
import com.lolo.changebox.domain.MAX_WIDGETS
import com.lolo.changebox.domain.WIDGET_SIZES
import com.lolo.changebox.domain.WIDGET_TYPES
import com.lolo.changebox.domain.WidgetSize
import com.lolo.changebox.domain.WidgetType
import com.lolo.changebox.domain.defaultDashboardPrefs
import com.lolo.changebox.ui.common.ChangeboxSelect
import com.lolo.changebox.ui.common.ChangeboxTextField
import com.lolo.changebox.ui.common.ErrorBox
import com.lolo.changebox.ui.common.GhostButton
import com.lolo.changebox.ui.common.PrimaryButton
import com.lolo.changebox.ui.theme.ChangeboxColors
import kotlinx.coroutines.launch

// Personalización de Inicio (port de dashboard-customizer.tsx): orden y
// visibilidad de secciones, gadgets del panel bento (alta/edición con
// configuración, tamaño, orden con flechas y baja) y qué cuentas lista la
// sección Cuentas. El reordenado por arrastre del escritorio web se sustituye
// por las flechas subir/bajar de este sheet (las mismas que usa la web en
// móvil). Se guarda con `onSave` (UserPrefs.saveDashboardPrefs).

private val FIXED_LABELS: Map<String, String> = DASHBOARD_SECTIONS.associate { it.key to it.labelEs }

private const val ALL_ACCOUNTS = "all"

private const val ID_CHARS = "0123456789abcdefghijklmnopqrstuvwxyz"

private fun newWidgetId(): String =
    "w" + java.lang.Long.toString(System.currentTimeMillis(), 36) +
        (1..5).map { ID_CHARS.random() }.joinToString("")

private fun <T> List<T>.swapped(index: Int, delta: Int): List<T> {
    val target = index + delta
    if (target < 0 || target >= size) return this
    val next = toMutableList()
    val tmp = next[index]
    next[index] = next[target]
    next[target] = tmp
    return next
}

private val VARIANT_OPTION_LABELS = mapOf(
    IncomeCardVariant.SOFT to "Estilo claro",
    IncomeCardVariant.DARK to "Estilo oscuro",
)

private val METRIC_OPTION_LABELS = mapOf(
    IncomeCardMetric.INCOME to "Gráfico: ingresos",
    IncomeCardMetric.EXPENSE to "Gráfico: gastos",
    IncomeCardMetric.NET to "Gráfico: neto",
)

private val PERIOD_OPTION_LABELS = mapOf(
    IncomeCardPeriod.DAY to "Empieza en: Día",
    IncomeCardPeriod.WEEK to "Empieza en: Semana",
    IncomeCardPeriod.MONTH to "Empieza en: Mes",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardCustomizerSheet(
    prefs: DashboardPrefs,
    accounts: List<AccountWithBalance>,
    currencies: List<CurrencyEntity>,
    onDismiss: () -> Unit,
    onSave: suspend (DashboardPrefs) -> ActionResult<Unit>,
    onSaved: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Al abrir se parte SIEMPRE del estado guardado.
    var sections by remember { mutableStateOf(prefs.sections) }
    var widgets by remember { mutableStateOf(prefs.widgets) }
    var accountIds by remember { mutableStateOf(prefs.accountIds) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Alta o edición de gadget en curso (editingId = null → alta)
    var editingId by remember { mutableStateOf<String?>(null) }
    var addType by remember { mutableStateOf<WidgetType?>(null) }
    var addSize by remember { mutableStateOf(WidgetSize.MD) }
    var addAccountId by remember { mutableStateOf("") }
    var addShowMovements by remember { mutableStateOf(false) }
    var addShowDenominations by remember { mutableStateOf(false) }
    var addFromId by remember { mutableStateOf("") }
    var addToId by remember { mutableStateOf("") }
    // Configuración del «Resumen de ingresos»
    var addVariant by remember { mutableStateOf(IncomeCardVariant.SOFT) }
    var addMetric by remember { mutableStateOf(IncomeCardMetric.INCOME) }
    var addPeriod by remember { mutableStateOf(IncomeCardPeriod.MONTH) }
    var addTitle by remember { mutableStateOf("") }
    var addShowTabs by remember { mutableStateOf(true) }
    var addShowDelta by remember { mutableStateOf(true) }
    var addShowIncome by remember { mutableStateOf(true) }
    var addShowExpense by remember { mutableStateOf(true) }
    var addShowNet by remember { mutableStateOf(true) }

    val accountById = accounts.associateBy { it.id }
    val currencyById = currencies.associateBy { it.id }

    fun widgetLabel(widget: DashboardWidget): String = when (widget.type) {
        WidgetType.ACCOUNT_CARD -> {
            val account = widget.accountId?.let { accountById[it] }
            "Cuenta · ${account?.name ?: "no disponible"}"
        }
        WidgetType.RATE_PAIR -> {
            val from = widget.fromCurrencyId?.let { currencyById[it]?.code } ?: "?"
            val to = widget.toCurrencyId?.let { currencyById[it]?.code } ?: "?"
            "Tasa $from → $to"
        }
        WidgetType.INCOME_CARD -> {
            val title = widget.title
            val account = widget.accountId?.let { accountById[it] }
            when {
                !title.isNullOrEmpty() -> title
                account != null -> "Resumen · ${account.name}"
                else -> "Resumen de ingresos"
            }
        }
        WidgetType.CURRENCY_TOTALS -> widget.type.labelEs
    }

    fun cancelForm() {
        addType = null
        editingId = null
    }

    fun startAdd(type: WidgetType) {
        editingId = null
        addType = type
        addSize = DEFAULT_WIDGET_SIZE.getValue(type)
        addAccountId = if (type == WidgetType.INCOME_CARD) ALL_ACCOUNTS
        else accounts.firstOrNull()?.id ?: ""
        addShowMovements = false
        addShowDenominations = false
        addFromId = currencies.getOrNull(0)?.id ?: ""
        addToId = currencies.getOrNull(1)?.id ?: ""
        addVariant = IncomeCardVariant.SOFT
        addMetric = IncomeCardMetric.INCOME
        addPeriod = IncomeCardPeriod.MONTH
        addTitle = ""
        addShowTabs = true
        addShowDelta = true
        addShowIncome = true
        addShowExpense = true
        addShowNet = true
    }

    /** Prellena el formulario con la configuración del gadget. */
    fun startEdit(widget: DashboardWidget) {
        editingId = widget.id
        addType = widget.type
        addSize = widget.size
        addAccountId = if (widget.type == WidgetType.INCOME_CARD) {
            widget.accountId ?: ALL_ACCOUNTS
        } else {
            widget.accountId ?: accounts.firstOrNull()?.id ?: ""
        }
        addShowMovements = widget.showMovements == true
        addShowDenominations = widget.showDenominations == true
        addFromId = widget.fromCurrencyId ?: currencies.getOrNull(0)?.id ?: ""
        addToId = widget.toCurrencyId ?: currencies.getOrNull(1)?.id ?: ""
        addVariant = widget.variant ?: IncomeCardVariant.SOFT
        addMetric = widget.metric ?: IncomeCardMetric.INCOME
        addPeriod = widget.defaultPeriod ?: IncomeCardPeriod.MONTH
        addTitle = widget.title ?: ""
        addShowTabs = widget.showTabs != false
        addShowDelta = widget.showDelta != false
        addShowIncome = widget.showIncome != false
        addShowExpense = widget.showExpense != false
        addShowNet = widget.showNet != false
    }

    fun confirmAdd() {
        val type = addType ?: return
        val editing = editingId
        if (editing == null && widgets.size >= MAX_WIDGETS) return
        val id = editing ?: newWidgetId()
        val widget: DashboardWidget = when (type) {
            WidgetType.ACCOUNT_CARD -> {
                if (addAccountId.isEmpty() || addAccountId == ALL_ACCOUNTS) return
                DashboardWidget(
                    id = id,
                    type = type,
                    size = addSize,
                    accountId = addAccountId,
                    showMovements = addShowMovements,
                    showDenominations = addShowDenominations,
                )
            }
            WidgetType.RATE_PAIR -> {
                if (addFromId.isEmpty() || addToId.isEmpty() || addFromId == addToId) return
                DashboardWidget(
                    id = id,
                    type = type,
                    size = addSize,
                    fromCurrencyId = addFromId,
                    toCurrencyId = addToId,
                )
            }
            WidgetType.INCOME_CARD -> {
                val title = addTitle.trim()
                DashboardWidget(
                    id = id,
                    type = type,
                    size = addSize,
                    accountId = if (addAccountId == ALL_ACCOUNTS) null else addAccountId,
                    variant = addVariant,
                    metric = addMetric,
                    defaultPeriod = addPeriod,
                    title = if (title.isNotEmpty()) title.take(40) else null,
                    showTabs = addShowTabs,
                    showDelta = addShowDelta,
                    showIncome = addShowIncome,
                    showExpense = addShowExpense,
                    showNet = addShowNet,
                )
            }
            WidgetType.CURRENCY_TOTALS -> DashboardWidget(id = id, type = type, size = addSize)
        }
        widgets = if (editing != null) {
            widgets.map { if (it.id == editing) widget else it }
        } else {
            widgets + widget
        }
        cancelForm()
    }

    fun toggleAccount(id: String) {
        val current = accountIds ?: accounts.map { it.id }
        accountIds = if (id in current) current.filter { it != id } else current + id
    }

    fun reset() {
        val defaults = defaultDashboardPrefs()
        sections = defaults.sections
        widgets = defaults.widgets
        accountIds = defaults.accountIds
        cancelForm()
    }

    fun save() {
        scope.launch {
            saving = true
            error = null
            val result = onSave(
                DashboardPrefs(sections = sections, widgets = widgets, accountIds = accountIds)
            )
            saving = false
            when (result) {
                is ActionResult.Success -> onSaved()
                is ActionResult.Failure -> error = result.error
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(
                    "Personalizar Inicio",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Ordena y oculta secciones, y arma tu panel de gadgets.",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Secciones: visibilidad y orden
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sections.forEachIndexed { index, item ->
                    SectionRow(
                        item = item,
                        label = FIXED_LABELS[item.key] ?: item.key,
                        canMoveUp = index > 0,
                        canMoveDown = index < sections.size - 1,
                        onToggle = {
                            sections = sections.map {
                                if (it.key == item.key) it.copy(visible = !it.visible) else it
                            }
                        },
                        onMoveUp = { sections = sections.swapped(index, -1) },
                        onMoveDown = { sections = sections.swapped(index, 1) },
                    )
                }
            }

            // Gadgets del panel bento: tamaño, orden y baja
            if (widgets.isNotEmpty()) {
                SheetBlock {
                    BlockTitle("Gadgets del panel")
                    Column(
                        Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        widgets.forEachIndexed { index, widget ->
                            val label = widgetLabel(widget)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(14.dp),
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        label,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    Text(
                                        widget.size.labelEs,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        modifier = Modifier.padding(start = 6.dp),
                                    )
                                }
                                SquareButton(
                                    icon = Lucide.PencilLine,
                                    contentDescription = "Editar $label",
                                    active = editingId == widget.id,
                                    onClick = { startEdit(widget) },
                                )
                                SquareButton(
                                    icon = Lucide.ArrowUp,
                                    contentDescription = "Subir $label",
                                    enabled = index > 0,
                                    onClick = { widgets = widgets.swapped(index, -1) },
                                )
                                SquareButton(
                                    icon = Lucide.ArrowDown,
                                    contentDescription = "Bajar $label",
                                    enabled = index < widgets.size - 1,
                                    onClick = { widgets = widgets.swapped(index, 1) },
                                )
                                SquareButton(
                                    icon = Lucide.Trash2,
                                    contentDescription = "Eliminar $label",
                                    muted = true,
                                    onClick = {
                                        widgets = widgets.filter { it.id != widget.id }
                                        // Si se borra el gadget en edición, la edición se cancela.
                                        if (widget.id == editingId) cancelForm()
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Alta y edición de gadgets
            SheetBlock {
                BlockTitle(if (editingId != null) "Editar gadget" else "Añadir gadget")
                Spacer(Modifier.size(8.dp))
                val type = addType
                if (widgets.size >= MAX_WIDGETS && editingId == null) {
                    Text(
                        "Límite de $MAX_WIDGETS gadgets alcanzado: elimina alguno para añadir otro.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (type == null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WIDGET_TYPES.forEach { option ->
                            val disabled =
                                (option == WidgetType.ACCOUNT_CARD && accounts.isEmpty()) ||
                                    (option == WidgetType.RATE_PAIR && currencies.size < 2)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .alpha(if (disabled) 0.4f else 1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .clickable(enabled = !disabled) { startAdd(option) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Lucide.Plus,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    option.labelEs,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            type.labelEs,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        when (type) {
                            WidgetType.ACCOUNT_CARD -> {
                                ChangeboxSelect(
                                    options = accounts.map { it.id },
                                    selected = addAccountId.takeIf { accountById.containsKey(it) },
                                    onSelect = { addAccountId = it },
                                    display = { id ->
                                        accountById[id]?.let { "${it.name} · ${it.currency.code}" } ?: id
                                    },
                                    placeholder = "Elige cuenta",
                                )
                                CheckRow(
                                    checked = addShowMovements,
                                    label = "Mostrar los últimos movimientos",
                                    onToggle = { addShowMovements = !addShowMovements },
                                )
                                if (accountById[addAccountId]?.type == "CASH_BOX") {
                                    CheckRow(
                                        checked = addShowDenominations,
                                        label = "Mostrar denominaciones en caja",
                                        onToggle = { addShowDenominations = !addShowDenominations },
                                    )
                                }
                            }
                            WidgetType.RATE_PAIR -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    ChangeboxSelect(
                                        options = currencies.map { it.id },
                                        selected = addFromId.takeIf { currencyById.containsKey(it) },
                                        onSelect = { addFromId = it },
                                        display = { id -> currencyById[id]?.code ?: id },
                                        placeholder = "Origen",
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "→",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    ChangeboxSelect(
                                        options = currencies.map { it.id }.filter { it != addFromId },
                                        selected = addToId.takeIf {
                                            currencyById.containsKey(it) && it != addFromId
                                        },
                                        onSelect = { addToId = it },
                                        display = { id -> currencyById[id]?.code ?: id },
                                        placeholder = "Destino",
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            WidgetType.CURRENCY_TOTALS -> {
                                Text(
                                    "Muestra la suma de saldos por cada divisa, sin conversión.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            WidgetType.INCOME_CARD -> {
                                ChangeboxSelect(
                                    options = listOf(ALL_ACCOUNTS) + accounts.map { it.id },
                                    selected = addAccountId.takeIf {
                                        it == ALL_ACCOUNTS || accountById.containsKey(it)
                                    },
                                    onSelect = { addAccountId = it },
                                    display = { id ->
                                        if (id == ALL_ACCOUNTS) {
                                            "Todas las cuentas (moneda base)"
                                        } else {
                                            accountById[id]?.let { "${it.name} · ${it.currency.code}" } ?: id
                                        }
                                    },
                                    placeholder = "Cuentas",
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ChangeboxSelect(
                                        options = INCOME_CARD_VARIANTS,
                                        selected = addVariant,
                                        onSelect = { addVariant = it },
                                        display = { VARIANT_OPTION_LABELS.getValue(it) },
                                        modifier = Modifier.weight(1f),
                                    )
                                    ChangeboxSelect(
                                        options = INCOME_CARD_METRICS,
                                        selected = addMetric,
                                        onSelect = { addMetric = it },
                                        display = { METRIC_OPTION_LABELS.getValue(it) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                ChangeboxSelect(
                                    options = INCOME_CARD_PERIODS,
                                    selected = addPeriod,
                                    onSelect = { addPeriod = it },
                                    display = { PERIOD_OPTION_LABELS.getValue(it) },
                                )
                                ChangeboxTextField(
                                    value = addTitle,
                                    onValueChange = { addTitle = it.take(40) },
                                    placeholder = "Título propio (opcional)",
                                )
                                CheckRow(
                                    checked = addShowTabs,
                                    label = "Tabs de periodo (Día / Semana / Mes)",
                                    onToggle = { addShowTabs = !addShowTabs },
                                )
                                CheckRow(
                                    checked = addShowDelta,
                                    label = "Variación vs el periodo anterior",
                                    onToggle = { addShowDelta = !addShowDelta },
                                )
                                CheckRow(
                                    checked = addShowIncome,
                                    label = "Pie: total de ingresos",
                                    onToggle = { addShowIncome = !addShowIncome },
                                )
                                CheckRow(
                                    checked = addShowExpense,
                                    label = "Pie: total de gastos",
                                    onToggle = { addShowExpense = !addShowExpense },
                                )
                                CheckRow(
                                    checked = addShowNet,
                                    label = "Pie: neto",
                                    onToggle = { addShowNet = !addShowNet },
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Tamaño",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ChangeboxSelect(
                                options = WIDGET_SIZES,
                                selected = addSize,
                                onSelect = { addSize = it },
                                display = { it.labelEs },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        val confirmDisabled =
                            (type == WidgetType.ACCOUNT_CARD &&
                                (addAccountId.isEmpty() || addAccountId == ALL_ACCOUNTS)) ||
                                (type == WidgetType.RATE_PAIR &&
                                    (addFromId.isEmpty() || addToId.isEmpty() || addFromId == addToId))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GhostButton("Cancelar", onClick = { cancelForm() })
                            PrimaryButton(
                                text = if (editingId != null) "Guardar cambios" else "Añadir",
                                onClick = { confirmAdd() },
                                enabled = !confirmDisabled,
                            )
                        }
                    }
                }
            }

            // Cuentas visibles en la sección Cuentas
            if (accounts.isNotEmpty()) {
                SheetBlock {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { BlockTitle("Cuentas visibles") }
                        Text(
                            "Todas",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .alpha(if (accountIds == null) 0.4f else 1f)
                                .clickable(enabled = accountIds != null) { accountIds = null },
                        )
                    }
                    Text(
                        "Qué cuentas lista la sección «Cuentas» de Inicio.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                    )
                    accounts.forEach { account ->
                        val ids = accountIds
                        CheckRow(
                            checked = ids == null || account.id in ids,
                            label = "${account.name} · ${account.currency.code}",
                            onToggle = { toggleAccount(account.id) },
                        )
                    }
                }
            }

            error?.let { ErrorBox(it) }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { reset() }) {
                    Icon(
                        Lucide.RotateCcw,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "Restablecer",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                PrimaryButton(
                    text = if (saving) "Guardando…" else "Guardar",
                    onClick = { save() },
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SectionRow(
    item: DashboardSectionPref,
    label: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (item.visible) MaterialTheme.colorScheme.surface
        else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (item.visible) 1f else 0.7f),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (item.visible) ChangeboxColors.extended.chip
                        else MaterialTheme.colorScheme.surface
                    )
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (item.visible) Lucide.Eye else Lucide.EyeOff,
                    contentDescription = if (item.visible) "Ocultar $label" else "Mostrar $label",
                    tint = if (item.visible) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            SquareButton(
                icon = Lucide.ArrowUp,
                contentDescription = "Subir $label",
                enabled = canMoveUp,
                onClick = onMoveUp,
            )
            SquareButton(
                icon = Lucide.ArrowDown,
                contentDescription = "Bajar $label",
                enabled = canMoveDown,
                onClick = onMoveDown,
            )
        }
    }
}

/** Botón cuadrado de icono (h-8 w-8 rounded-[10px] de la web). */
@Composable
private fun SquareButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    active: Boolean = false,
    muted: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (active) ChangeboxColors.extended.chip
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = when {
                active -> MaterialTheme.colorScheme.primary
                muted -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.secondary
            },
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun CheckRow(checked: Boolean, label: String, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                .border(
                    1.dp,
                    if (checked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(6.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Lucide.Check,
                contentDescription = null,
                tint = if (checked) Color.White else Color.Transparent,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Bloque blanco con borde (rounded-[16px] border p-3.5 de la web). */
@Composable
private fun SheetBlock(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun BlockTitle(text: String) {
    Text(
        text,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
