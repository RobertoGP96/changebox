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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Trash2
import com.lolo.changebox.data.ActionResult
import com.lolo.changebox.data.local.dao.CategoryWithUsage
import com.lolo.changebox.di.AppContainer
import com.lolo.changebox.di.appViewModel
import com.lolo.changebox.ui.common.BadgeVariant
import com.lolo.changebox.ui.common.ChangeboxBadge
import com.lolo.changebox.ui.common.ChangeboxCard
import com.lolo.changebox.ui.common.ChangeboxTextField
import com.lolo.changebox.ui.common.ErrorBox
import com.lolo.changebox.ui.common.GhostButton
import com.lolo.changebox.ui.common.InlineConfirm
import com.lolo.changebox.ui.common.LocalToast
import com.lolo.changebox.ui.common.PrimaryButton
import com.lolo.changebox.ui.common.ScreenHeader
import com.lolo.changebox.ui.common.contentWidth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Categorías de gastos e ingresos con menú ⋮ por ítem (renombrar, ocultar,
// eliminar solo sin uso) — port de categorias/page + category-manager.

class CategoriesViewModel(private val container: AppContainer) : ViewModel() {

    val kind = MutableStateFlow("EXPENSE")

    @OptIn(ExperimentalCoroutinesApi::class)
    val categories = kind.flatMapLatest { k ->
        container.db.catalogDao().categoriesWithUsageFlow(k)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String, onResult: (ActionResult<String>) -> Unit) {
        viewModelScope.launch { onResult(container.catalog.createCategory(name, kind.value)) }
    }

    fun rename(id: String, name: String, onResult: (ActionResult<String>) -> Unit) {
        viewModelScope.launch { onResult(container.catalog.renameCategory(id, name)) }
    }

    fun toggle(id: String, onResult: (ActionResult<Boolean>) -> Unit) {
        viewModelScope.launch { onResult(container.catalog.toggleCategory(id)) }
    }

    fun delete(id: String, onResult: (ActionResult<String>) -> Unit) {
        viewModelScope.launch { onResult(container.catalog.deleteCategory(id)) }
    }
}

@Composable
fun CategoriesScreen(navController: NavHostController) {
    val vm = appViewModel { CategoriesViewModel(it) }
    var selectedKind by rememberSaveable { mutableStateOf("EXPENSE") }
    vm.kind.value = selectedKind
    val categories by vm.categories.collectAsStateWithLifecycle()
    val toast = LocalToast.current

    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(title = "Categorías", onBack = { navController.popBackStack() })

        Column(
            Modifier
                .contentWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KindChip("Gastos", selectedKind == "EXPENSE") { selectedKind = "EXPENSE" }
                KindChip("Ingresos", selectedKind == "INCOME") { selectedKind = "INCOME" }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    ChangeboxTextField(
                        name,
                        { if (it.length <= 40) name = it },
                        placeholder = "Nueva categoría",
                    )
                }
                PrimaryButton(
                    "Añadir",
                    enabled = !saving && name.isNotBlank(),
                    onClick = {
                        saving = true
                        error = null
                        vm.create(name) { result ->
                            saving = false
                            when (result) {
                                is ActionResult.Success -> {
                                    toast("Categoría creada")
                                    name = ""
                                }
                                is ActionResult.Failure -> error = result.error
                            }
                        }
                    },
                )
            }

            error?.let { ErrorBox(it) }

            categories.forEach { item ->
                CategoryRow(
                    item = item,
                    editing = editingId == item.category.id,
                    confirming = confirmDeleteId == item.category.id,
                    editName = editName,
                    onEditNameChange = { editName = it },
                    saving = saving,
                    onStartEdit = {
                        confirmDeleteId = null
                        editingId = item.category.id
                        editName = item.category.name
                    },
                    onToggle = {
                        vm.toggle(item.category.id) { result ->
                            when (result) {
                                is ActionResult.Success -> toast(
                                    if (result.data) "Categoría activada" else "Categoría oculta"
                                )
                                is ActionResult.Failure -> toast(result.error)
                            }
                        }
                    },
                    onStartDelete = {
                        // Con movimientos no se puede borrar: aviso directo.
                        if (item.usageCount > 0) {
                            toast("Tiene movimientos asociados; ocúltala en su lugar")
                        } else {
                            editingId = null
                            confirmDeleteId = item.category.id
                        }
                    },
                    onCancel = {
                        editingId = null
                        confirmDeleteId = null
                    },
                    onRename = {
                        saving = true
                        vm.rename(item.category.id, editName) { result ->
                            saving = false
                            when (result) {
                                is ActionResult.Success -> {
                                    toast("Categoría renombrada")
                                    editingId = null
                                }
                                is ActionResult.Failure -> toast(result.error)
                            }
                        }
                    },
                    onDelete = {
                        saving = true
                        vm.delete(item.category.id) { result ->
                            saving = false
                            confirmDeleteId = null
                            when (result) {
                                is ActionResult.Success -> toast("Categoría eliminada")
                                is ActionResult.Failure -> toast(result.error)
                            }
                        }
                    },
                )
            }

            if (categories.isEmpty()) {
                ChangeboxCard(corner = 13) {
                    Text(
                        "Sin categorías de este tipo.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            Text(
                "Las categorías con movimientos no se pueden eliminar para conservar el historial; ocúltalas y dejarán de aparecer al registrar.",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else com.lolo.changebox.ui.theme.ChangeboxColors.extended.chip
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun CategoryRow(
    item: CategoryWithUsage,
    editing: Boolean,
    confirming: Boolean,
    editName: String,
    onEditNameChange: (String) -> Unit,
    saving: Boolean,
    onStartEdit: () -> Unit,
    onToggle: () -> Unit,
    onStartDelete: () -> Unit,
    onCancel: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val alpha = if (item.category.active) 1f else 0.55f

    ChangeboxCard(corner = 13) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            when {
                editing -> {
                    // Campo a lo ancho y botones debajo: no desborda en móviles
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChangeboxTextField(editName, { if (it.length <= 40) onEditNameChange(it) })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrimaryButton(
                                "Guardar",
                                enabled = !saving && editName.isNotBlank(),
                                onClick = onRename,
                            )
                            GhostButton("Cancelar", onClick = onCancel, enabled = !saving)
                        }
                    }
                }
                confirming -> {
                    InlineConfirm(
                        text = "¿Eliminar “${item.category.name}”? No se puede deshacer.",
                        confirmLabel = "Eliminar",
                        onConfirm = onDelete,
                        onCancel = onCancel,
                        busy = saving,
                    )
                }
                else -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            item.category.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (item.usageCount > 0) {
                            ChangeboxBadge("${item.usageCount} mov.", BadgeVariant.NEUTRAL)
                        }
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { menuOpen = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Lucide.EllipsisVertical,
                                    contentDescription = "Opciones de ${item.category.name}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Renombrar", fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(Lucide.Pencil, null, Modifier.size(15.dp))
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onStartEdit()
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (item.category.active) "Ocultar" else "Activar",
                                            fontSize = 13.sp,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (item.category.active) Lucide.EyeOff else Lucide.Eye,
                                            null, Modifier.size(15.dp),
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onToggle()
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Eliminar",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Lucide.Trash2, null, Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onStartDelete()
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

