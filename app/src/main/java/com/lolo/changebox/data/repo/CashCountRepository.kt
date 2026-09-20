package com.lolo.changebox.data.repo

import androidx.room.withTransaction
import com.lolo.changebox.data.ActionResult
import com.lolo.changebox.data.fail
import com.lolo.changebox.data.local.ChangeboxDatabase
import com.lolo.changebox.data.local.entity.CashCountEntity
import com.lolo.changebox.data.local.entity.CashCountLineEntity
import com.lolo.changebox.data.local.entity.TransactionEntity
import com.lolo.changebox.data.ok
import com.lolo.changebox.domain.AccountType
import com.lolo.changebox.domain.isCashLike
import com.lolo.changebox.domain.sumMinor

// Núcleo del arqueo, port 1:1 de lib/services/cash-count-service.ts: el saldo
// teórico se relee DENTRO de la transacción para que la diferencia guardada
// sea coherente aunque llegue otro movimiento concurrente.

data class CashCountLineInput(val denominationId: String, val quantity: Int)

class CashCountRepository(
    private val db: ChangeboxDatabase,
    private val accounts: AccountRepository,
) {

    private val cashCountDao = db.cashCountDao()
    private val accountDao = db.accountDao()
    private val catalogDao = db.catalogDao()
    private val txDao = db.transactionDao()

    fun recentCountsFlow(limit: Int = 10) = cashCountDao.recentCountsFlow(limit)

    data class CountOutcome(val id: String, val differenceMinor: Long)

    suspend fun createCashCount(
        accountId: String,
        note: String?,
        createAdjustment: Boolean,
        lines: List<CashCountLineInput>,
    ): ActionResult<CountOutcome> {
        val account = accountDao.accountById(accountId)
        val type = account?.let { runCatching { AccountType.valueOf(it.type) }.getOrNull() }
        if (account == null || account.archived || type == null || !type.isCashLike()) {
            return fail("Solo se pueden arquear Changeboxs de efectivo")
        }
        if (lines.isEmpty()) return fail("Indica las cantidades del conteo")

        val denominations = catalogDao.activeDenominations(account.currencyId)
        val byId = denominations.associateBy { it.id }

        for (line in lines) {
            if (line.denominationId !in byId) {
                return fail("Denominación no válida para esta moneda")
            }
        }

        val totalMinor = sumMinor(
            lines.map { byId.getValue(it.denominationId).valueMinor * it.quantity }
        )
        val storedLines = lines.filter { it.quantity > 0 }

        var differenceMinor = 0L
        val count = CashCountEntity(
            accountId = account.id,
            totalMinor = totalMinor,
            expectedMinor = 0L, // se fija dentro de la transacción
            differenceMinor = 0L,
            note = note?.takeIf { it.isNotBlank() },
        )
        db.withTransaction {
            // Saldo teórico leído dentro de la transacción.
            val expectedMinor = accounts.accountBalanceMinor(account.id)
            differenceMinor = totalMinor - expectedMinor

            cashCountDao.insertCashCount(
                count.copy(expectedMinor = expectedMinor, differenceMinor = differenceMinor)
            )
            cashCountDao.insertLines(
                storedLines.map {
                    CashCountLineEntity(
                        cashCountId = count.id,
                        denominationId = it.denominationId,
                        quantity = it.quantity,
                    )
                }
            )

            if (createAdjustment && differenceMinor != 0L) {
                val adjustment = TransactionEntity(
                    kind = "ADJUSTMENT",
                    accountId = account.id,
                    amountMinor = differenceMinor,
                    currencyId = account.currencyId,
                    note = "Ajuste por arqueo",
                )
                txDao.insertTransaction(adjustment)
                cashCountDao.setAdjustmentTx(count.id, adjustment.id)
            }
        }

        return ok(CountOutcome(count.id, differenceMinor))
    }
}


