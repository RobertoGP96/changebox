package com.lolo.changebox.data.repo

import androidx.room.withTransaction
import com.lolo.changebox.data.ActionResult
import com.lolo.changebox.data.fail
import com.lolo.changebox.data.local.ChangeboxDatabase
import com.lolo.changebox.data.local.entity.AccountEntity
import com.lolo.changebox.data.local.entity.TransactionEntity
import com.lolo.changebox.data.ok
import com.lolo.changebox.domain.IncomingTransferGroup
import com.lolo.changebox.domain.MoneyException
import com.lolo.changebox.domain.OwnKindGroup
import com.lolo.changebox.domain.balancesFromGroups
import com.lolo.changebox.domain.movementLineSign
import com.lolo.changebox.domain.parseAmountToMinor
import com.lolo.changebox.ui.theme.ACCOUNT_ICON_NAMES
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

// Cuentas: alta con saldo inicial, edición, archivado, borrado en cascada y
// saldos/stock DERIVADOS (nunca almacenados). Ports de account-service.ts,
// balances.ts y denominations.ts.

data class AccountCurrency(
    val id: String,
    val code: String,
    val symbol: String,
    val decimalPlaces: Int,
)

data class AccountGroupRef(val id: String, val name: String)

data class AccountWithBalance(
    val id: String,
    val name: String,
    val type: String,
    val icon: String?,
    val archived: Boolean,
    val currency: AccountCurrency,
    val group: AccountGroupRef?,
    val balanceMinor: Long,
)

data class DenominationStockLine(
    val denominationId: String,
    val valueMinor: Long,
    val kind: String,
    val active: Boolean,
    val quantity: Int,
)

data class AccountDenominationStock(
    /** Fecha del arqueo que sirve de base; null si nunca se arqueó. */
    val countedAt: Long?,
    /** Movimientos con desglose aplicados después del arqueo base. */
    val movements: Int,
    /** Todas las denominaciones de la moneda (activas o con stock ≠ 0). */
    val lines: List<DenominationStockLine>,
)

class AccountRepository(private val db: ChangeboxDatabase) {

    private val accountDao = db.accountDao()
    private val catalogDao = db.catalogDao()
    private val cashCountDao = db.cashCountDao()
    private val txDao = db.transactionDao()

    // ── Lecturas ────────────────────────────────────────────────────────────

    /**
     * Cuentas con saldo derivado, compuesto con balances-core a partir de las
     * sumas agrupadas en la BD (3 consultas fijas, como en la web).
     */
    fun accountsWithBalancesFlow(includeArchived: Boolean = false): Flow<List<AccountWithBalance>> =
        combine(
            if (includeArchived) accountDao.allAccountsFlow() else accountDao.activeAccountsFlow(),
            accountDao.ownGroupsFlow(),
            accountDao.incomingGroupsFlow(),
            catalogDao.currenciesFlow(),
            catalogDao.groupsFlow(),
        ) { accounts, own, incoming, currencies, groups ->
            val balances = balancesFromGroups(
                own.map { OwnKindGroup(it.accountId, it.kind, it.sumMinor) },
                incoming.map { IncomingTransferGroup(it.accountId, it.sumMinor) },
            )
            val currencyById = currencies.associateBy { it.id }
            val groupById = groups.associateBy { it.id }
            accounts.map { account ->
                val currency = currencyById.getValue(account.currencyId)
                AccountWithBalance(
                    id = account.id,
                    name = account.name,
                    type = account.type,
                    icon = account.icon,
                    archived = account.archived,
                    currency = AccountCurrency(
                        currency.id, currency.code, currency.symbol, currency.decimalPlaces,
                    ),
                    group = account.groupId?.let { groupId ->
                        groupById[groupId]?.let { AccountGroupRef(it.id, it.name) }
                    },
                    balanceMinor = balances[account.id] ?: 0L,
                )
            }
        }

    fun accountFlow(id: String): Flow<AccountEntity?> = accountDao.accountFlow(id)

    fun activeAccountsFlow(): Flow<List<AccountEntity>> = accountDao.activeAccountsFlow()

    fun activeAccountsInCurrencyFlow(currencyId: String): Flow<List<AccountEntity>> =
        accountDao.activeAccountsInCurrencyFlow(currencyId)

    fun accountBalanceFlow(accountId: String): Flow<Long> =
        combine(
            accountDao.ownGroupsFlow(),
            accountDao.incomingGroupsFlow(),
        ) { own, incoming ->
            val balances = balancesFromGroups(
                own.map { OwnKindGroup(it.accountId, it.kind, it.sumMinor) },
                incoming.map { IncomingTransferGroup(it.accountId, it.sumMinor) },
            )
            balances[accountId] ?: 0L
        }

    /** Saldo puntual (se relee DENTRO de transacciones de escritura). */
    suspend fun accountBalanceMinor(accountId: String): Long =
        accountDao.ownSignedSum(accountId) + accountDao.incomingSum(accountId)

    /**
     * Disponibilidad de denominaciones de una caja CASH_BOX, derivada (nunca
     * almacenada): líneas del ÚLTIMO arqueo ± desgloses de movimientos con
     * occurredAt posterior. Movimientos sin desglose no alteran el stock.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun denominationStockFlow(accountId: String): Flow<AccountDenominationStock> =
        accountDao.accountFlow(accountId).flatMapLatest { account ->
            if (account == null) {
                flowOf(AccountDenominationStock(null, 0, emptyList()))
            } else {
                val lastCountWithLines = cashCountDao.lastCountFlow(accountId)
                    .flatMapLatest { last ->
                        if (last == null) {
                            flowOf(null)
                        } else {
                            cashCountDao.linesForFlow(last.id).map { lines -> last to lines }
                        }
                    }
                combine(
                    lastCountWithLines,
                    catalogDao.denominationsFlow(account.currencyId),
                    txDao.txLinesForAccountFlow(accountId),
                ) { lastPair, denominations, txLines ->
                    val lastCount = lastPair?.first
                    val stock = mutableMapOf<String, Int>()
                    lastPair?.second?.forEach { line ->
                        stock[line.denominationId] = line.quantity
                    }

                    val appliedTx = mutableSetOf<String>()
                    for (line in txLines) {
                        if (lastCount != null && line.occurredAt <= lastCount.countedAt) continue
                        val sign = movementLineSign(line.txKind, line.txAccountId == accountId)
                        if (sign == 0) continue
                        stock[line.denominationId] =
                            (stock[line.denominationId] ?: 0) + sign * line.quantity
                        appliedTx.add(line.transactionId)
                    }

                    AccountDenominationStock(
                        countedAt = lastCount?.countedAt,
                        movements = appliedTx.size,
                        lines = denominations.map { d ->
                            DenominationStockLine(
                                denominationId = d.id,
                                valueMinor = d.valueMinor,
                                kind = d.kind,
                                active = d.active,
                                quantity = stock[d.id] ?: 0,
                            )
                        }.filter { it.active || it.quantity != 0 },
                    )
                }
            }
        }

    // ── Escrituras ──────────────────────────────────────────────────────────

    suspend fun createAccount(
        name: String,
        type: String,
        currencyId: String,
        initialAmount: String?,
        groupId: String?,
        icon: String?,
    ): ActionResult<String> {
        if (name.isBlank()) return fail("El nombre es obligatorio")
        val currency = catalogDao.currencyById(currencyId)
        if (currency == null || !currency.active) return fail("Moneda no válida")

        if (groupId != null && catalogDao.groupById(groupId) == null) {
            return fail("Grupo no válido")
        }
        if (icon != null && icon !in ACCOUNT_ICON_NAMES) return fail("Icono no válido")

        val initialMinor = try {
            initialAmount?.takeIf { it.isNotBlank() }
                ?.let { parseAmountToMinor(it, currency.toMinor()) } ?: 0L
        } catch (e: MoneyException) {
            return fail(e.message ?: "Monto inválido")
        }

        // Cuenta y saldo inicial en una sola transacción: sin cuentas a medias.
        val account = AccountEntity(
            name = name.trim(),
            type = type,
            currencyId = currency.id,
            groupId = groupId,
            icon = icon,
        )
        db.withTransaction {
            accountDao.insertAccount(account)
            if (initialMinor != 0L) {
                txDao.insertTransaction(
                    TransactionEntity(
                        kind = "ADJUSTMENT",
                        accountId = account.id,
                        amountMinor = initialMinor,
                        currencyId = currency.id,
                        note = "Saldo inicial",
                    )
                )
            }
        }
        return ok(account.id)
    }

    suspend fun setAccountIcon(accountId: String, icon: String?): ActionResult<String> {
        if (icon != null && icon !in ACCOUNT_ICON_NAMES) return fail("Icono no válido")
        val account = accountDao.accountById(accountId) ?: return fail("Cuenta no encontrada")
        accountDao.setIcon(account.id, icon)
        return ok(account.id)
    }

    suspend fun updateAccount(
        accountId: String,
        name: String,
        archived: Boolean? = null,
    ): ActionResult<String> {
        if (name.isBlank()) return fail("El nombre es obligatorio")
        val account = accountDao.accountById(accountId) ?: return fail("Cuenta no encontrada")
        accountDao.updateAccount(
            account.copy(name = name.trim(), archived = archived ?: account.archived)
        )
        return ok(account.id)
    }

    /**
     * Eliminar la cuenta borra en cascada TODO su historial: movimientos por
     * ambos lados (incluidas transferencias con otras cuentas), arqueos,
     * desgloses y abonos vinculados a esos movimientos (el pendiente de la
     * deuda reaparece al ser derivado). Las cuotas saldadas conservan su
     * estado (SET_NULL). Archivar sigue siendo la opción que conserva todo.
     */
    suspend fun deleteAccount(accountId: String): ActionResult<Unit> {
        accountDao.accountById(accountId) ?: return fail("Cuenta no encontrada")
        accountDao.deleteAccount(accountId)
        return ok(Unit)
    }
}


