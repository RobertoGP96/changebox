package com.lolo.changebox.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.Lucide
import com.lolo.changebox.data.ActionResult
import com.lolo.changebox.data.local.dao.GroupWithCount
import com.lolo.changebox.di.AppContainer
import com.lolo.changebox.di.appViewModel
import com.lolo.changebox.ui.common.BadgeVariant
import com.lolo.changebox.ui.common.ChangeboxBadge
import com.lolo.changebox.ui.common.ChangeboxCard
import com.lolo.changebox.ui.common.ChangeboxTextField
import com.lolo.changebox.ui.common.ErrorBox
import com.lolo.changebox.ui.common.GhostButton
import com.lolo.changebox.ui.common.IconChip
import com.lolo.changebox.ui.common.InlineConfirm
import com.lolo.changebox.ui.common.LocalToast
import com.lolo.changebox.ui.common.PrimaryButton
import com.lolo.changebox.ui.common.ScreenHeader
import com.lolo.changebox.ui.common.contentWidth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Grupos de cuentas: crear, renombrar y eliminar (las cuentas del grupo
// eliminado pasan a "Sin grupo") — port de cuentas/grupos/group-manager.tsx.

class GroupsViewModel(private val container: AppContainer) : ViewModel() {
    val groups = container.db.catalogDao().groupsWithCountsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String, onResult: (ActionResult<String>) -> Unit) {
        viewModelScope.launch { onResult(container.catalog.createGroup(name)) }
    }

    fun rename(id: String, name: String, onResult: (ActionResult<String>) -> Unit) {
        viewModelScope.launch { onResult(container.catalog.renameGroup(id, name)) }
    }

    fun delete(id: String, onResult: (ActionResult<String>) -> Unit) {
        viewModelScope.launch { onResult(container.catalog.deleteGroup(id)) }
    }
}

@Composable
fun GroupsScreen(navController: NavHostController) {
    val vm = appViewModel { GroupsViewModel(it) }
    val groups by vm.groups.collectAsStateWithLifecycle()
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
        ScreenHeader(title = "Grupos de cuentas", onBack = { navController.popBackStack() })

        Column(
            Modifier
                .contentWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    ChangeboxTextField(
                        name,
                        { if (it.length <= 40) name = it },
                        placeholder = "Nuevo grupo (ej. Negocio)",
                    )
                }
                PrimaryButton(
                    "Crear",
                    enabled = !saving && name.isNotBlank(),
                    onClick = {
                        saving = true
                        error = null
                        vm.create(name) { result ->
                            saving = false
                            when (result) {
                                is ActionResult.Success -> {
                                    toast("Grupo creado")
                                    name = ""
                                }
                                is ActionResult.Failure -> error = result.error
                            }
                        }
                    },
                )
            }

            error?.let { ErrorBox(it) }

            groups.forEach { item ->
                GroupRow(
                    item = item,
                    editing = editingId == item.group.id,
                    confirming = confirmDeleteId == item.group.id,
                    editName = editName,
                    onEditNameChange = { editName = it },
                    saving = saving,
                    onStartEdit = {
                        confirmDeleteId = null
                        editingId = item.group.id
                        editName = item.group.name
                    },
                    onStartDelete = {
                        editingId = null
                        confirmDeleteId = item.group.id
                    },
                    onCancel = {
                        editingId = null
                        confirmDeleteId = null
                    },
                    onRename = {
                        saving = true
                        vm.rename(item.group.id, editName) { result ->
                            saving = false
                            when (result) {
                                is ActionResult.Success -> {
                                    toast("Grupo renombrado")
                                    editingId = null
                                }
                                is ActionResult.Failure -> toast(result.error)
                            }
                        }
                    },
                    onDelete = {
                        saving = true
                        vm.delete(item.group.id) { result ->
                            saving = false
                            confirmDeleteId = null
                            when (result) {
                                is ActionResult.Success -> toast("Grupo eliminado")
                                is ActionResult.Failure -> toast(result.error)
                            }
                        }
                    },
                )
            }

            if (groups.isEmpty()) {
                ChangeboxCard(corner = 13) {
                    Text(
                        "Sin grupos todavía. Crea el primero para organizar tus cuentas.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GroupRow(
    item: GroupWithCount,
    editing: Boolean,
    confirming: Boolean,
    editName: String,
    onEditNameChange: (String) -> Unit,
    saving: Boolean,
    onStartEdit: () -> Unit,
    onStartDelete: () -> Unit,
    onCancel: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    ChangeboxCard(corner = 13) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
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
                        text = "¿Eliminar “${item.group.name}”? Sus cuentas pasan a “Sin grupo”.",
                        confirmLabel = "Eliminar",
                        onConfirm = onDelete,
                        onCancel = onCancel,
                        busy = saving,
                    )
                }
                else -> {
                    // Nombre arriba, acciones debajo: los botones nunca aplastan el nombre
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            IconChip(Lucide.FolderOpen, size = 32, corner = 10)
                            Text(
                                item.group.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (item.accountCount > 0) {
                                ChangeboxBadge("${item.accountCount} cuentas", BadgeVariant.NEUTRAL)
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            GhostButton("Renombrar", onClick = onStartEdit)
                            GhostButton("Eliminar", onClick = onStartDelete)
                        }
                    }
                }
            }
        }
    }
}

