package com.lolo.changebox.ui.accounts

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Wallet
import com.lolo.changebox.data.local.entity.CurrencyEntity
import com.lolo.changebox.data.repo.AccountWithBalance
import com.lolo.changebox.data.repo.PairRatePoint
import com.lolo.changebox.di.AppContainer
import com.lolo.changebox.di.appViewModel
import com.lolo.changebox.domain.AccountType
import com.lolo.changebox.domain.DisplayCurrencyOf
import com.lolo.changebox.domain.MinorCurrencyOf
import com.lolo.changebox.domain.convertMinor
import com.lolo.changebox.domain.fmtMinor
import com.lolo.changebox.ui.Routes
import com.lolo.changebox.ui.common.EmptyState
import com.lolo.changebox.ui.common.OutlineButton
import com.lolo.changebox.ui.common.PrimaryButton
import com.lolo.changebox.ui.common.ScreenHeader
import com.lolo.changebox.ui.common.contentWidth
import com.lolo.changebox.ui.home.AccountCard
import com.lolo.changebox.ui.home.toDisplayLocal
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

// Cuentas: listado agrupado con subtotal por grupo consolidado a la base —
// port de cuentas/page.tsx. Los detalles de cuenta muestran el tipo.

data class AccountsState(
    val loaded: Boolean = false,
    val accounts: List<AccountWithBalance> = emptyList(),
    val groups: List<Pair<String, String>> = emptyList(),
    val base: CurrencyEntity? = null,
    val rates: Map<String, PairRatePoint> = emptyMap(),
)

class AccountsViewModel(container: AppContainer) : ViewModel() {
    val state = combine(
        container.accounts.accountsWithBalancesFlow(),
        container.db.catalogDao().groupsFlow(),
        container.db.catalogDao().baseCurrencyFlow(),
        container.rates.latestRatesByCurrencyFlow(),
    ) { accounts, groups, base, rates ->
        AccountsState(true, accounts, groups.map { it.id to it.name }, base, rates)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsState())
}

private data class Section(
    val key: String,
    val name: String?,
    val accounts: List<AccountWithBalance>,
    val subtotalMinor: Long?,
)

@Composable
fun AccountsScreen(navController: NavHostController) {
    val vm = appViewModel { AccountsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    val sections: List<Section> = if (state.groups.isNotEmpty()) {
        state.groups.map { (id, name) ->
            Section(id, name, state.accounts.filter { it.group?.id == id }, null)
        } + Section("none", "Sin grupo", state.accounts.filter { it.group == null }, null)
    } else {
        listOf(Section("all", null, state.accounts, null))
    }
    val visible = sections.filter { it.accounts.isNotEmpty() }.map { section ->
        // Subtotal por grupo consolidado a la base; se omite si falta una tasa.
        val base = state.base ?: return@map section
        var subtotal = 0L
        var complete = true
        for (account in section.accounts) {
            if (account.currency.id == base.id) {
                subtotal += account.balanceMinor
            } else {
                val rate = state.rates[account.currency.id]
                if (rate != null) {
                    subtotal += convertMinor(
                        account.balanceMinor,
                        MinorCurrencyOf(account.currency.decimalPlaces),
                        MinorCurrencyOf(base.decimalPlaces),
                        rate.rateScaled,
                    )
                } else if (account.balanceMinor != 0L) {
                    complete = false
                    break
                }
            }
        }
        section.copy(subtotalMinor = if (complete) subtotal else null)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(title = "Cuentas")

        Column(
            Modifier
                .contentWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlineButton(
                    "Grupos",
                    onClick = { navController.navigate(Routes.GROUPS) },
                    icon = Lucide.FolderOpen,
                )
                PrimaryButton("Nueva cuenta", onClick = { navController.navigate(Routes.NEW_ACCOUNT) })
            }

            if (state.loaded && state.accounts.isEmpty()) {
                EmptyState(
                    icon = Lucide.Wallet,
                    title = "Sin cuentas todavía",
                    description = "Crea tu primera cuenta o caja para empezar a registrar movimientos.",
                    ctaLabel = "Crear cuenta",
                    onCta = { navController.navigate(Routes.NEW_ACCOUNT) },
                )
            } else {
                visible.forEach { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (section.name != null) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    section.name,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                val base = state.base
                                if (base != null && section.subtotalMinor != null) {
                                    Text(
                                        fmtMinor(section.subtotalMinor, base.toDisplayLocal()),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        section.accounts.forEach { account ->
                            AccountTypeCard(account) {
                                navController.navigate(Routes.accountDetail(account.id))
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
private fun AccountTypeCard(account: AccountWithBalance, onClick: () -> Unit) {
    // Igual que AccountCard del Inicio pero con la etiqueta de tipo.
    val type = runCatching { AccountType.valueOf(account.type) }.getOrDefault(AccountType.CASH)
    val negative = account.balanceMinor < 0
    com.lolo.changebox.ui.common.ChangeboxCard(onClick = onClick) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            com.lolo.changebox.ui.common.IconChip(
                com.lolo.changebox.ui.theme.getAccountIcon(account.icon, type)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    account.name,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    "${type.labelEs} · ${account.currency.code}",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                fmtMinor(
                    account.balanceMinor,
                    DisplayCurrencyOf(account.currency.code, account.currency.decimalPlaces),
                ),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (negative) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

