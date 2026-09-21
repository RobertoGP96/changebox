package com.lolo.changebox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.composables.icons.lucide.HandCoins
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Menu
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Wallet
import com.lolo.changebox.ui.accounts.AccountDetailScreen
import com.lolo.changebox.ui.accounts.AccountsScreen
import com.lolo.changebox.ui.accounts.ArchivedAccountsScreen
import com.lolo.changebox.ui.accounts.GroupsScreen
import com.lolo.changebox.ui.accounts.NewAccountScreen
import com.lolo.changebox.ui.catalog.CategoriesScreen
import com.lolo.changebox.ui.catalog.CurrenciesScreen
import com.lolo.changebox.ui.catalog.CurrencyDetailScreen
import com.lolo.changebox.ui.common.CtaGradient
import com.lolo.changebox.ui.common.LocalToast
import com.lolo.changebox.ui.counting.CalculatorScreen
import com.lolo.changebox.ui.counting.CashCountScreen
import com.lolo.changebox.ui.counting.CountingScreen
import com.lolo.changebox.ui.debts.DebtDetailScreen
import com.lolo.changebox.ui.debts.DebtsScreen
import com.lolo.changebox.ui.debts.NewDebtScreen
import com.lolo.changebox.ui.debts.MonthlyPlansScreen
import com.lolo.changebox.ui.debts.NewPlanScreen
import com.lolo.changebox.ui.debts.PlanDetailScreen
import com.lolo.changebox.ui.home.HomeScreen
import com.lolo.changebox.ui.more.MoreScreen
import com.lolo.changebox.ui.more.SettingsScreen
import com.lolo.changebox.ui.movements.EditMovementScreen
import com.lolo.changebox.ui.movements.MovementDetailScreen
import com.lolo.changebox.ui.movements.MovementsScreen
import com.lolo.changebox.ui.rates.RatePairScreen
import com.lolo.changebox.ui.rates.RatesScreen
import com.lolo.changebox.ui.register.RegisterScreen
import com.lolo.changebox.ui.theme.ChangeboxTheme
import kotlinx.coroutines.launch

// Estructura de navegación espejo de la web: barra inferior con Inicio,
// Cuentas, [+ Registrar], Deudas y Más; el resto de pantallas cuelgan de ahí.

object Routes {
    const val HOME = "inicio"
    const val REGISTER = "registrar?tipo={tipo}&cuenta={cuenta}"
    const val MOVEMENTS = "movimientos"
    const val MOVEMENT_DETAIL = "movimientos/{id}"
    const val EDIT_MOVEMENT = "movimientos/{id}/editar"
    const val ACCOUNTS = "cuentas"
    const val NEW_ACCOUNT = "cuentas/nueva"
    const val ACCOUNT_DETAIL = "cuentas/detalle/{id}"
    const val GROUPS = "cuentas/grupos"
    const val DEBTS = "deudas?dir={dir}"
    const val NEW_DEBT = "deudas/nueva"
    const val DEBT_DETAIL = "deudas/detalle/{id}"
    // Mensualidades: vista propia, como /mensualidades en la web. La ruta
    // literal "nueva" se registra ANTES que la de {id} en el NavHost.
    const val MONTHLY_PLANS = "mensualidades?tipo={tipo}"
    const val NEW_PLAN = "mensualidades/nueva"
    const val PLAN_DETAIL = "mensualidades/{id}"
    const val ARCHIVED_ACCOUNTS = "cuentas/archivadas"
    const val COUNTING = "conteo"
    const val CASH_COUNT = "conteo/{accountId}"
    const val CALCULATOR = "calculadora"
    const val CATEGORIES = "categorias"
    const val CURRENCIES = "monedas"
    const val CURRENCY_DETAIL = "monedas/{id}"
    const val RATES = "tasas"
    const val RATE_PAIR = "tasas/{from}/{to}"
    const val MORE = "mas"
    const val SETTINGS = "ajustes"

    fun register(tipo: String? = null, cuenta: String? = null): String =
        "registrar?tipo=${tipo ?: ""}&cuenta=${cuenta ?: ""}"

    fun movementDetail(id: String) = "movimientos/$id"
    fun editMovement(id: String) = "movimientos/$id/editar"
    fun accountDetail(id: String) = "cuentas/detalle/$id"
    fun debts(dir: String? = null) = "deudas?dir=${dir ?: ""}"
    fun debtDetail(id: String) = "deudas/detalle/$id"
    fun planDetail(id: String) = "mensualidades/$id"
    /** tipo = "pagar" | "cobrar" | null (todas), como ?tipo= de la web. */
    fun monthlyPlans(tipo: String? = null) = "mensualidades?tipo=${tipo ?: ""}"
    fun cashCount(accountId: String) = "conteo/$accountId"
    fun currencyDetail(id: String) = "monedas/$id"
    fun ratePair(from: String, to: String) = "tasas/$from/$to"
}

private data class NavItem(
    val route: String,
    val icon: ImageVector,
    val label: String,
    val isActive: (String) -> Boolean,
)

private val LEFT_ITEMS = listOf(
    NavItem(Routes.HOME, Lucide.House, "Inicio") { it == Routes.HOME },
    NavItem(Routes.ACCOUNTS, Lucide.Wallet, "Cuentas") {
        it.startsWith("cuentas") || it.startsWith("conteo")
    },
)

private val RIGHT_ITEMS = listOf(
    NavItem(Routes.DEBTS, Lucide.HandCoins, "Deudas") {
        // Las mensualidades cuelgan de la pestaña Deudas, como en la web.
        it.startsWith("deudas") || it.startsWith("mensualidades")
    },
    NavItem(Routes.MORE, Lucide.Menu, "Más") {
        it.startsWith("mas") || it.startsWith("tasas") || it.startsWith("movimientos") ||
            it.startsWith("categorias") || it.startsWith("monedas") ||
            it.startsWith("calculadora") || it.startsWith("ajustes")
    },
)

@Composable
fun ChangeboxApp() {
    ChangeboxTheme {
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val showToast: (String) -> Unit = { message ->
            scope.launch { snackbarHostState.showSnackbar(message) }
        }

        CompositionLocalProvider(LocalToast provides showToast) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = { ChangeboxBottomBar(navController) },
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = padding.calculateBottomPadding())
                        // El teclado no debe tapar los formularios (edge-to-edge)
                        .imePadding()
                ) {
                    ChangeboxNavHost(navController)
                }
            }
        }
    }
}

@Composable
private fun ChangeboxBottomBar(navController: NavHostController) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: Routes.HOME

    fun navigateTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // El FAB va como HERMANO del Surface (no hijo): el Surface recorta a su
    // shape y decapitaría el botón que sobresale de la barra.
    Box(Modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                LEFT_ITEMS.forEach { item ->
                    BottomNavItem(item, currentRoute) { navigateTab(item.route) }
                }
                // Hueco del botón central
                Spacer(Modifier.weight(1f))
                RIGHT_ITEMS.forEach { item ->
                    BottomNavItem(item, currentRoute) { navigateTab(item.route) }
                }
            }
        }
        // Botón central de registrar con gradiente CTA, flotando sobre la barra
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(56.dp)
                .shadow(10.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(CtaGradient)
                .clickable { navController.navigate(Routes.register()) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Lucide.Plus,
                contentDescription = "Registrar movimiento",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BottomNavItem(
    item: NavItem,
    currentRoute: String,
    onClick: () -> Unit,
) {
    val active = item.isActive(currentRoute)
    val tint = if (active) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .size(width = 56.dp, height = 32.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    if (active) com.lolo.changebox.ui.theme.ChangeboxColors.extended.chip
                    else Color.Transparent
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(19.dp))
        }
        Text(
            item.label,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = tint,
        )
    }
}

@Composable
private fun ChangeboxNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(navController) }
        composable(
            Routes.REGISTER,
            arguments = listOf(
                navArgument("tipo") { defaultValue = "" },
                navArgument("cuenta") { defaultValue = "" },
            ),
        ) { entry ->
            RegisterScreen(
                navController,
                initialTipo = entry.arguments?.getString("tipo").orEmpty(),
                initialCuenta = entry.arguments?.getString("cuenta").orEmpty(),
            )
        }
        composable(Routes.MOVEMENTS) { MovementsScreen(navController) }
        composable(Routes.MOVEMENT_DETAIL) { entry ->
            MovementDetailScreen(navController, entry.arguments?.getString("id").orEmpty())
        }
        composable(Routes.EDIT_MOVEMENT) { entry ->
            EditMovementScreen(navController, entry.arguments?.getString("id").orEmpty())
        }
        composable(Routes.ACCOUNTS) { AccountsScreen(navController) }
        composable(Routes.NEW_ACCOUNT) { NewAccountScreen(navController) }
        composable(Routes.ACCOUNT_DETAIL) { entry ->
            AccountDetailScreen(navController, entry.arguments?.getString("id").orEmpty())
        }
        composable(Routes.GROUPS) { GroupsScreen(navController) }
        composable(Routes.ARCHIVED_ACCOUNTS) { ArchivedAccountsScreen(navController) }
        composable(
            Routes.DEBTS,
            arguments = listOf(navArgument("dir") { defaultValue = "" }),
        ) { entry ->
            DebtsScreen(navController, entry.arguments?.getString("dir").orEmpty())
        }
        composable(Routes.NEW_DEBT) { NewDebtScreen(navController) }
        composable(Routes.DEBT_DETAIL) { entry ->
            DebtDetailScreen(navController, entry.arguments?.getString("id").orEmpty())
        }
        composable(
            Routes.MONTHLY_PLANS,
            arguments = listOf(navArgument("tipo") { defaultValue = "" }),
        ) { entry ->
            MonthlyPlansScreen(navController, entry.arguments?.getString("tipo").orEmpty())
        }
        // "mensualidades/nueva" va antes que "mensualidades/{id}".
        composable(Routes.NEW_PLAN) { NewPlanScreen(navController) }
        composable(Routes.PLAN_DETAIL) { entry ->
            PlanDetailScreen(navController, entry.arguments?.getString("id").orEmpty())
        }
        composable(Routes.COUNTING) { CountingScreen(navController) }
        composable(Routes.CASH_COUNT) { entry ->
            CashCountScreen(navController, entry.arguments?.getString("accountId").orEmpty())
        }
        composable(Routes.CALCULATOR) { CalculatorScreen(navController) }
        composable(Routes.CATEGORIES) { CategoriesScreen(navController) }
        composable(Routes.CURRENCIES) { CurrenciesScreen(navController) }
        composable(Routes.CURRENCY_DETAIL) { entry ->
            CurrencyDetailScreen(navController, entry.arguments?.getString("id").orEmpty())
        }
        composable(Routes.RATES) { RatesScreen(navController) }
        composable(Routes.RATE_PAIR) { entry ->
            RatePairScreen(
                navController,
                entry.arguments?.getString("from").orEmpty(),
                entry.arguments?.getString("to").orEmpty(),
            )
        }
        composable(Routes.MORE) { MoreScreen(navController) }
        composable(Routes.SETTINGS) { SettingsScreen(navController) }
    }
}

