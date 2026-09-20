package com.lolo.changebox.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.composables.icons.lucide.ArrowRightLeft
import com.composables.icons.lucide.Banknote
import com.composables.icons.lucide.Calculator
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Coins
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.UserRound
import com.lolo.changebox.di.AppContainer
import com.lolo.changebox.di.appViewModel
import com.lolo.changebox.ui.Routes
import com.lolo.changebox.ui.common.ChangeboxCard
import com.lolo.changebox.ui.common.ChangeboxTextField
import com.lolo.changebox.ui.common.IconChip
import com.lolo.changebox.ui.common.LabeledField
import com.lolo.changebox.ui.common.LocalToast
import com.lolo.changebox.ui.common.PrimaryButton
import com.lolo.changebox.ui.common.ScreenHeader
import com.lolo.changebox.ui.common.contentWidth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Más opciones: accesos a conteo, calculadora, tasas, movimientos, categorías
// y monedas + los ajustes locales (la app offline no tiene sesión ni perfil
// remoto) — port de mas/page.tsx sin el bloque de cerrar sesión.

private data class MenuItem(
    val route: String,
    val icon: ImageVector,
    val title: String,
    val description: String,
)

private val ITEMS = listOf(
    MenuItem(
        Routes.COUNTING, Lucide.Banknote,
        "Conteo de efectivo", "Arqueo de Changeboxs con denominaciones",
    ),
    MenuItem(
        Routes.CALCULATOR, Lucide.Calculator,
        "Calculadora de efectivo", "Cuenta billetes y monedas sin elegir cuenta",
    ),
    MenuItem(
        Routes.RATES, Lucide.ArrowRightLeft,
        "Tasas de cambio", "Tasas vigentes, histórico y conversor",
    ),
    MenuItem(
        Routes.MOVEMENTS, Lucide.History,
        "Movimientos", "Historial completo con filtros",
    ),
    MenuItem(
        Routes.CATEGORIES, Lucide.Tags,
        "Categorías", "Categorías de gastos e ingresos",
    ),
    MenuItem(
        Routes.CURRENCIES, Lucide.Coins,
        "Monedas y denominaciones", "Divisas del sistema, billetes y monedas",
    ),
)

class MoreViewModel(container: AppContainer) : ViewModel() {
    val userName = container.prefs.userNameFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
}

@Composable
fun MoreScreen(navController: NavHostController) {
    val vm = appViewModel { MoreViewModel(it) }
    val userName by vm.userName.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(title = "Más opciones")

        Column(
            Modifier
                .contentWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ITEMS.forEach { item ->
                ChangeboxCard(onClick = { navController.navigate(item.route) }) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        IconChip(item.icon)
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                item.description,
                                fontSize = 12.sp,
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
                }
            }

            Spacer(Modifier.height(4.dp))

            ChangeboxCard(onClick = { navController.navigate(Routes.SETTINGS) }) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconChip(Lucide.UserRound)
                    Column(Modifier.weight(1f)) {
                        Text(
                            userName.ifEmpty { "Ajustes" },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Nombre y datos de la app · 100% offline",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Lucide.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val userName = container.prefs.userNameFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun save(name: String, onDone: () -> Unit) {
        viewModelScope.launch {
            container.prefs.setUserName(name)
            onDone()
        }
    }
}

/**
 * Ajustes locales: el equivalente offline del perfil de la web. No hay
 * correo, contraseña ni sesión — todos los datos viven en este dispositivo.
 */
@Composable
fun SettingsScreen(navController: NavHostController) {
    val vm = appViewModel { SettingsViewModel(it) }
    val savedName by vm.userName.collectAsStateWithLifecycle()
    val toast = LocalToast.current

    var name by remember(savedName) { mutableStateOf(savedName) }
    var saving by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(title = "Ajustes", onBack = { navController.popBackStack() })

        Column(
            Modifier
                .contentWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ChangeboxCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledField("Tu nombre (opcional)") {
                        ChangeboxTextField(
                            name,
                            { if (it.length <= 60) name = it },
                            placeholder = "¿Cómo te llamas?",
                        )
                    }
                    PrimaryButton(
                        if (saving) "Guardando…" else "Guardar",
                        enabled = !saving,
                        onClick = {
                            saving = true
                            vm.save(name) {
                                saving = false
                                toast("Ajustes guardados")
                            }
                        },
                    )
                }
            }

            ChangeboxCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Changebox · versión offline",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Todos tus datos (cuentas, movimientos, deudas, tasas y arqueos) viven únicamente en este dispositivo. No hay inicio de sesión ni conexión a ningún servidor. Puedes exportar tus movimientos a CSV desde la pantalla de Movimientos.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

