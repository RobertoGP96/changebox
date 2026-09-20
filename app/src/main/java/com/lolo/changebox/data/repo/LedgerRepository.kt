package com.lolo.changebox.data.repo

import androidx.room.withTransaction
import com.lolo.changebox.data.ActionResult
import com.lolo.changebox.data.fail
import com.lolo.changebox.data.local.ChangeboxDatabase
import com.lolo.changebox.data.local.dao.TxJoinRow
import com.lolo.changebox.data.local.entity.AccountEntity
import com.lolo.changebox.data.local.entity.TransactionDenominationEntity
import com.lolo.changebox.data.local.entity.TransactionEntity
import com.lolo.changebox.data.ok
import com.lolo.changebox.domain.DisplayCurrencyOf
import com.lolo.changebox.domain.MinorCurrencyOf
import com.lolo.changebox.domain.MoneyException
import com.lolo.changebox.domain.SERVER_INT_MAX
import com.lolo.changebox.domain.TransactionKind
import com.lolo.changebox.domain.convertMinor
import com.lolo.changebox.domain.convertMinorInverse
import com.lolo.changebox.domain.fmtMinor
import com.lolo.changebox.domain.impliedRateScaled
import com.lolo.changebox.domain.parseAmountToMinor
import kotlinx.coroutines.flow.Flow

// Núcleo del libro mayor (ingresos/gastos y transferencias), port 1:1 de
// lib/services/transaction-service.ts, incluida la operación multi-moneda y
// el desglose obligatorio de denominaciones en Changeboxs CASH_BOX.

enum class RateDirection { AMOUNT_TO_ACCOUNT, ACCOUNT_TO_AMOUNT }

data class DenomLineInput(val denominationId: String, val quantity: Int)

data class IncomeExpenseInput(
    val kind: String, // INCOME | EXPENSE
    val accountId: String,
    val amount: String,
    // Operación multi-moneda: el monto viene en otra divisa y se convierte a
    // la de la cuenta con la tasa indicada.
    val amountCurrencyId: String? = null,
    val rate: String? = null,
    val rateDirection: RateDirection? = null,
    val categoryId: String? = null,
    val note: String? = null,
    val occurredAt: Long? = null,
    val denominationLines: List<DenomLineInput>? = null,
)

data class TransferInput(
    val accountId: String,
    val counterAccountId: String,
    val amount: String,
    val counterAmount: String? = null,
    val note: String? = null,
    val occurredAt: Long? = null,
    val denominationLines: List<DenomLineInput>? = null,
    val counterDenominationLines: List<DenomLineInput>? = null,
)

/** Fila del historial desde la perspectiva de la vista actual (tx-rows.ts). */
data class TxRowUi(
    val id: String,
    val kind: String,
    val title: String,
    val subtitle: String,
    val occurredAt: Long,
    val amountMinor: Long,
    val currency: DisplayCurrencyOf,
)

/**
 * Convierte una transacción en fila de historial desde la perspectiva de una
 * cuenta (signo relativo a ella) o global (perspectiva null: signo del
 * movimiento en su cuenta de origen). Port 1:1 de toTxRow.
 */
fun toTxRow(
    tx: TxJoinRow,
    perspectiveAccountId: String? = null,
    perspectiveCurrency: DisplayCurrencyOf? = null,
): TxRowUi {
    val kindLabel = runCatching { TransactionKind.valueOf(tx.kind).labelEs }.getOrDefault(tx.kind)
    val isIncoming = perspectiveAccountId != null &&
        tx.kind == "TRANSFER" && tx.accountId != perspectiveAccountId

    var currency = DisplayCurrencyOf(tx.currencyCode, tx.currencyDecimals)
    val amountMinor: Long
    val title: String

    when {
        tx.kind == "TRANSFER" && isIncoming -> {
            amountMinor = tx.counterAmountMinor ?: 0L
            currency = perspectiveCurrency ?: currency
            title = "Desde ${tx.accountName}"
        }
        tx.kind == "TRANSFER" -> {
            amountMinor = -tx.amountMinor
            title = "Hacia ${tx.counterAccountName ?: "otra cuenta"}"
        }
        tx.kind == "EXPENSE" -> {
            amountMinor = -tx.amountMinor
            title = tx.categoryName ?: kindLabel
        }
        tx.kind == "INCOME" -> {
            amountMinor = tx.amountMinor
            title = tx.categoryName ?: kindLabel
        }
        else -> {
            // ADJUSTMENT lleva el signo en amountMinor
            amountMinor = tx.amountMinor
            title = tx.note ?: kindLabel
        }
    }

    val subtitleParts = mutableListOf<String>()
    if (perspectiveAccountId == null) subtitleParts.add(tx.accountName)
    // Ingreso/gasto multi-moneda: se muestra el monto original de la operación
    // (el principal ya va convertido a la moneda de la cuenta).
    if ((tx.kind == "INCOME" || tx.kind == "EXPENSE") &&
        tx.counterAmountMinor != null && tx.counterCurrencyCode != null
    ) {
        subtitleParts.add(
            fmtMinor(
                tx.counterAmountMinor,
                DisplayCurrencyOf(tx.counterCurrencyCode, tx.counterCurrencyDecimals ?: 2),
            )
        )
    }
    if (tx.note != null && title != tx.note) subtitleParts.add(tx.note)

    return TxRowUi(
        id = tx.id,
        kind = tx.kind,
        title = title,
        subtitle = subtitleParts.joinToString(" · "),
        occurredAt = tx.occurredAt,
        amountMinor = amountMinor,
        currency = currency,
    )
}

private sealed interface LinesCheck {
    data class Ok(val lines: List<DenomLineInput>) : LinesCheck
    data class Bad(val error: String) : LinesCheck
}

class LedgerRepository(private val db: ChangeboxDatabase) {

    private val txDao = db.transactionDao()
    private val accountDao = db.accountDao()
    private val catalogDao = db.catalogDao()

    fun filteredRowsFlow(
        start: Long?,
        end: Long?,
        accountId: String?,
        categoryId: String?,
        kind: String?,
        limit: Int = 200,
    ): Flow<List<TxJoinRow>> =
        txDao.filteredRowsFlow(start, end, accountId, categoryId, kind, limit)

    fun accountRowsFlow(accountId: String, limit: Int = 30): Flow<List<TxJoinRow>> =
        txDao.accountRowsFlow(accountId, limit)

    fun txDetailFlow(id: String) = txDao.txDetailFlow(id)

    fun txDenominationLinesFlow(txId: String) = txDao.txDenominationLinesFlow(txId)

    fun debtLinkFlow(txId: String) = txDao.debtLinkFlow(txId)

    fun planLinkFlow(txId: String) = txDao.planLinkFlow(txId)

    /**
     * Valida el desglose de denominaciones de UN lado del movimiento: la
     * cuenta debe ser CASH_BOX, las denominaciones de su moneda y la suma
     * exactamente igual al monto de ese lado.
     */
    private suspend fun checkDenominationLines(
        account: AccountEntity,
        accountCurrency: DisplayCurrencyOf,
        lines: List<DenomLineInput>?,
        expectedMinor: Long,
    ): LinesCheck {
        if (lines.isNullOrEmpty()) return LinesCheck.Ok(emptyList())

        if (account.type != "CASH_BOX") {
            return LinesCheck.Bad("El desglose de denominaciones solo aplica a cuentas tipo Caja")
        }

        val ids = lines.map { it.denominationId }
        if (ids.toSet().size != ids.size) {
            return LinesCheck.Bad("Hay denominaciones repetidas en el desglose")
        }

        val denominations = catalogDao.denominationsByIds(ids, account.currencyId)
        if (denominations.size != ids.size) {
            return LinesCheck.Bad("Denominación no válida para la moneda de la caja")
        }

        val byId = denominations.associate { it.id to it.valueMinor }
        val totalMinor = lines.sumOf { byId.getValue(it.denominationId) * it.quantity }
        if (totalMinor != expectedMinor) {
            return LinesCheck.Bad(
                "El desglose suma ${fmtMinor(totalMinor, accountCurrency)} y el monto es " +
                    fmtMinor(expectedMinor, accountCurrency)
            )
        }

        return LinesCheck.Ok(lines)
    }

    suspend fun registerIncomeExpense(data: IncomeExpenseInput): ActionResult<String> {
        val account = accountDao.accountById(data.accountId)
        if (account == null || account.archived) return fail("Cuenta no válida")
        val accountCurrency = catalogDao.currencyById(account.currencyId)
            ?: return fail("Cuenta no válida")

        // Operación multi-moneda: el movimiento se GUARDA en la moneda de la
        // cuenta (los saldos no cambian de lógica) y el monto original + la
        // tasa quedan en counterAmountMinor/counterCurrencyId/rateScaled.
        val crossCurrency = data.amountCurrencyId != null &&
            data.amountCurrencyId != account.currencyId

        val amountMinor: Long
        var counterAmountMinor: Long? = null
        var counterCurrencyId: String? = null
        var rateScaled: Long? = null

        try {
            if (crossCurrency) {
                val opCurrency = catalogDao.currencyById(data.amountCurrencyId!!)
                    ?: return fail("Moneda no válida")

                val opMinor = parseAmountToMinor(data.amount, opCurrency.toMinor())
                if (opMinor <= 0) return fail("El monto debe ser mayor que cero")
                if (opMinor > SERVER_INT_MAX) return fail("Monto demasiado grande")

                if (data.rate.isNullOrBlank()) {
                    return fail("Indica la tasa ${opCurrency.code}/${accountCurrency.code}")
                }
                // La tasa se parsea con la misma precisión con que se almacena.
                val enteredRateScaled = parseAmountToMinor(data.rate, MinorCurrencyOf(4))
                if (enteredRateScaled <= 0 || enteredRateScaled > SERVER_INT_MAX) {
                    return fail("Tasa inválida")
                }

                amountMinor = if (data.rateDirection == RateDirection.ACCOUNT_TO_AMOUNT) {
                    convertMinorInverse(
                        opMinor, opCurrency.toMinor(), accountCurrency.toMinor(), enteredRateScaled,
                    )
                } else {
                    convertMinor(
                        opMinor, opCurrency.toMinor(), accountCurrency.toMinor(), enteredRateScaled,
                    )
                }
                if (amountMinor <= 0) {
                    return fail("El monto convertido queda en cero: revisa la tasa")
                }
                if (amountMinor > SERVER_INT_MAX) {
                    return fail("El monto convertido es demasiado grande")
                }

                counterAmountMinor = opMinor
                counterCurrencyId = opCurrency.id
                // Tasa implícita informativa (moneda original por 1 unidad de
                // la moneda de la cuenta).
                rateScaled = impliedRateScaled(
                    amountMinor, accountCurrency.toMinor(), opMinor, opCurrency.toMinor(),
                )
            } else {
                amountMinor = parseAmountToMinor(data.amount, accountCurrency.toMinor())
                if (amountMinor <= 0) return fail("El monto debe ser mayor que cero")
            }
        } catch (e: MoneyException) {
            return fail(e.message ?: "Monto inválido")
        }

        if (data.categoryId != null) {
            val category = catalogDao.categoryById(data.categoryId)
            if (category == null || category.kind != data.kind) {
                return fail("Categoría no válida")
            }
        }

        // El desglose va en la moneda de la cuenta: en operaciones multi-moneda
        // debe cuadrar con el monto YA convertido (amountMinor).
        val linesCheck = checkDenominationLines(
            account, accountCurrency.toDisplay(), data.denominationLines, amountMinor,
        )
        if (linesCheck is LinesCheck.Bad) return fail(linesCheck.error)

        val transaction = TransactionEntity(
            kind = data.kind,
            accountId = account.id,
            amountMinor = amountMinor,
            currencyId = account.currencyId,
            counterAmountMinor = counterAmountMinor,
            counterCurrencyId = counterCurrencyId,
            rateScaled = rateScaled,
            categoryId = data.categoryId,
            note = data.note?.takeIf { it.isNotBlank() },
            occurredAt = data.occurredAt ?: System.currentTimeMillis(),
        )
        db.withTransaction {
            txDao.insertTransaction(transaction)
            val lines = (linesCheck as LinesCheck.Ok).lines
            if (lines.isNotEmpty()) {
                txDao.insertDenominationLines(
                    lines.map {
                        TransactionDenominationEntity(
                            transactionId = transaction.id,
                            accountId = account.id,
                            denominationId = it.denominationId,
                            quantity = it.quantity,
                        )
                    }
                )
            }
        }
        return ok(transaction.id)
    }

    suspend fun registerTransfer(data: TransferInput): ActionResult<String> {
        if (data.accountId == data.counterAccountId) {
            return fail("Elige dos cuentas distintas")
        }

        val from = accountDao.accountById(data.accountId)
        val to = accountDao.accountById(data.counterAccountId)
        if (from == null || from.archived || to == null || to.archived) {
            return fail("Cuenta no válida")
        }
        val fromCurrency = catalogDao.currencyById(from.currencyId)
            ?: return fail("Cuenta no válida")
        val toCurrency = catalogDao.currencyById(to.currencyId)
            ?: return fail("Cuenta no válida")

        val amountMinor: Long
        var counterAmountMinor: Long
        var rateScaled: Long? = null

        try {
            amountMinor = parseAmountToMinor(data.amount, fromCurrency.toMinor())
            if (amountMinor <= 0) return fail("El monto debe ser mayor que cero")

            val sameCurrency = from.currencyId == to.currencyId
            counterAmountMinor = amountMinor

            if (!sameCurrency) {
                if (data.counterAmount.isNullOrBlank()) {
                    return fail("Indica el monto recibido en ${toCurrency.code}")
                }
                counterAmountMinor = parseAmountToMinor(data.counterAmount, toCurrency.toMinor())
                if (counterAmountMinor <= 0) {
                    return fail("El monto recibido debe ser mayor que cero")
                }
                // Tasa implícita (destino por 1 origen), solo informativa.
                rateScaled = impliedRateScaled(
                    amountMinor, fromCurrency.toMinor(), counterAmountMinor, toCurrency.toMinor(),
                )
            }
        } catch (e: MoneyException) {
            return fail(e.message ?: "Monto inválido")
        }

        // Desglose de salida (origen) y de entrada (destino), cada uno en la
        // moneda y monto de su lado.
        val fromLines = checkDenominationLines(
            from, fromCurrency.toDisplay(), data.denominationLines, amountMinor,
        )
        if (fromLines is LinesCheck.Bad) return fail(fromLines.error)
        val toLines = checkDenominationLines(
            to, toCurrency.toDisplay(), data.counterDenominationLines, counterAmountMinor,
        )
        if (toLines is LinesCheck.Bad) return fail(toLines.error)

        val transaction = TransactionEntity(
            kind = "TRANSFER",
            accountId = from.id,
            counterAccountId = to.id,
            amountMinor = amountMinor,
            currencyId = from.currencyId,
            counterAmountMinor = counterAmountMinor,
            counterCurrencyId = to.currencyId,
            rateScaled = rateScaled,
            note = data.note?.takeIf { it.isNotBlank() },
            occurredAt = data.occurredAt ?: System.currentTimeMillis(),
        )
        db.withTransaction {
            txDao.insertTransaction(transaction)
            val lines =
                (fromLines as LinesCheck.Ok).lines.map { line ->
                    TransactionDenominationEntity(
                        transactionId = transaction.id,
                        accountId = from.id,
                        denominationId = line.denominationId,
                        quantity = line.quantity,
                    )
                } + (toLines as LinesCheck.Ok).lines.map { line ->
                    TransactionDenominationEntity(
                        transactionId = transaction.id,
                        accountId = to.id,
                        denominationId = line.denominationId,
                        quantity = line.quantity,
                    )
                }
            if (lines.isNotEmpty()) txDao.insertDenominationLines(lines)
        }
        return ok(transaction.id)
    }

    // ── Export CSV (port de app/api/export/route.ts) ─────────────────────────

    suspend fun exportCsv(
        start: Long?,
        end: Long?,
        accountId: String?,
        categoryId: String?,
        kind: String?,
    ): String {
        val rows = txDao.exportRows(start, end, accountId, categoryId, kind)
        val header = listOf(
            "Fecha", "Tipo", "Cuenta", "Contracuenta", "Categoría", "Monto", "Moneda", "Nota",
        )
        val dateFmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val lines = rows.map { tx ->
            val signed = if (tx.kind == "EXPENSE" || tx.kind == "TRANSFER") {
                -tx.amountMinor
            } else {
                tx.amountMinor
            }
            listOf(
                java.time.Instant.ofEpochMilli(tx.occurredAt)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(dateFmt),
                runCatching { TransactionKind.valueOf(tx.kind).labelEs }.getOrDefault(tx.kind),
                tx.accountName,
                tx.counterAccountName ?: "",
                tx.categoryName ?: "",
                com.lolo.changebox.domain.minorToInput(signed, tx.currencyDecimals),
                tx.currencyCode,
                tx.note ?: "",
            ).joinToString(",") { csvEscape(it) }
        }
        // BOM para que Excel abra el UTF-8 con acentos correctamente.
        return "﻿" + (listOf(header.joinToString(",")) + lines).joinToString("\r\n")
    }

    private fun csvEscape(value: String): String =
        if (Regex("[\",\r\n]").containsMatchIn(value)) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
}


