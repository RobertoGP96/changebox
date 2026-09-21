package com.lolo.changebox.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.Lucide
import com.lolo.changebox.data.repo.AccountWithBalance
import com.lolo.changebox.di.AppContainer
import com.lolo.changebox.di.appViewModel
import com.lolo.changebox.ui.Routes
import com.lolo.changebox.ui.common.EmptyState
import com.lolo.changebox.ui.common.ScreenHeader
import com.lolo.changebox.ui.common.contentWidth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// Cuentas archivadas: solo las archivadas, con su saldo derivado — port de
// cuentas/archivadas/page.tsx. Desde el detalle se pueden activar de nuevo.

data class ArchivedAccountsState(
    val loaded: Boolean = false,
    val accounts: List<AccountWithBalance> = emptyList(),
)

class ArchivedAccountsViewModel(container: AppContainer) : ViewModel() {
    val state = container.accounts.accountsWithBalancesFlow(includeArchived = true)
        .map { accounts -> ArchivedAccountsState(true, accounts.filter { it.archived }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArchivedAccountsState())
}

@Composable
fun ArchivedAccountsScreen(navController: NavHostController) {
    val vm = appViewModel { ArchivedAccountsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(
            title = "Cuentas archivadas",
            onBack = { navController.popBackStack() },
        )

        Column(
            Modifier
                .contentWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.loaded && state.accounts.isEmpty()) {
                EmptyState(
                    icon = Lucide.Archive,
                    title = "Sin cuentas archivadas",
                    description = "Cuando archives una cuenta desde su detalle, aparecerá aquí con su historial intacto.",
                )
            } else if (state.accounts.isNotEmpty()) {
                Text(
                    "Las cuentas archivadas conservan su historial y no aparecen al registrar movimientos. Entra al detalle para activarlas de nuevo.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.accounts.forEach { account ->
                        AccountTypeCard(account) {
                            navController.navigate(Routes.accountDetail(account.id))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
