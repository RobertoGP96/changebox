package com.lolo.changebox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lolo.changebox.data.local.entity.TransactionDenominationEntity
import com.lolo.changebox.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

// Libro mayor: filas de historial con nombres ya resueltos (JOIN plano, sin
// N+1), filtros del listado de movimientos y consultas del detalle.

/** Fila de historial/export: la transacción con sus referencias resueltas. */
data class TxJoinRow(
    val id: String,
    val kind: String,
    val amountMinor: Long,
    val counterAmountMinor: Long?,
    val note: String?,
    val occurredAt: Long,
    val accountId: String,
    val accountName: String,
    val counterAccountName: String?,
    val currencyId: String,
    val currencyCode: String,
    val currencyDecimals: Int,
    val counterCurrencyCode: String?,
    val counterCurrencyDecimals: Int?,
    val categoryName: String?,
)

/** Fila cruda del dashboard (metrics.ts): solo INCOME/EXPENSE. */
data class MetricRow(
    val kind: String,
    val amountMinor: Long,
    val occurredAt: Long,
    val currencyId: String,
    val currencyCode: String,
    val currencyDecimals: Int,
    val categoryName: String?,
)

/** Línea de desglose con su denominación y el lado (cuenta) al que aplica. */
data class TxDenomLineRow(
    val id: String,
    val accountId: String,
    val accountName: String,
    val quantity: Int,
    val valueMinor: Long,
    val kind: String,
)

/** Línea de desglose de una caja con datos del movimiento (stock derivado). */
data class TxLineWithTx(
    val denominationId: String,
    val quantity: Int,
    val transactionId: String,
    val txKind: String,
    val txAccountId: String,
    val occurredAt: Long,
)

/** Detalle completo de un movimiento con todas sus referencias resueltas. */
data class TxDetailRow(
    val id: String,
    val kind: String,
    val amountMinor: Long,
    val counterAmountMinor: Long?,
    val rateScaled: Long?,
    val note: String?,
    val occurredAt: Long,
    val createdAt: Long,
    val accountId: String,
    val accountName: String,
    val counterAccountId: String?,
    val counterAccountName: String?,
    val currencyCode: String,
    val currencyDecimals: Int,
    val counterCurrencyCode: String?,
    val counterCurrencyDecimals: Int?,
    val categoryName: String?,
)

data class DebtLinkRow(
    val debtId: String,
    val description: String,
    val contactName: String,
)

data class PlanLinkRow(
    val planId: String,
    val description: String,
    val debtId: String?,
    val contactName: String?,
    val debtContactName: String?,
    val debtDescription: String?,
)

private const val TX_JOIN = """
    SELECT t.id, t.kind, t.amountMinor, t.counterAmountMinor, t.note, t.occurredAt,
        t.accountId,
        a.name AS accountName,
        ca.name AS counterAccountName,
        t.currencyId,
        c.code AS currencyCode, c.decimalPlaces AS currencyDecimals,
        cc.code AS counterCurrencyCode, cc.decimalPlaces AS counterCurrencyDecimals,
        cat.name AS categoryName
    FROM transactions t
    JOIN accounts a ON a.id = t.accountId
    LEFT JOIN accounts ca ON ca.id = t.counterAccountId
    JOIN currencies c ON c.id = t.currencyId
    LEFT JOIN currencies cc ON cc.id = t.counterCurrencyId
    LEFT JOIN categories cat ON cat.id = t.categoryId
"""

@Dao
interface TransactionDao {

    @Insert
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Insert
    suspend fun insertDenominationLines(lines: List<TransactionDenominationEntity>)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun txById(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun txFlow(id: String): Flow<TransactionEntity?>

    @Query(
        """
        SELECT t.id, t.kind, t.amountMinor, t.counterAmountMinor, t.rateScaled,
            t.note, t.occurredAt, t.createdAt,
            t.accountId, a.name AS accountName,
            t.counterAccountId, ca.name AS counterAccountName,
            c.code AS currencyCode, c.decimalPlaces AS currencyDecimals,
            cc.code AS counterCurrencyCode, cc.decimalPlaces AS counterCurrencyDecimals,
            cat.name AS categoryName
        FROM transactions t
        JOIN accounts a ON a.id = t.accountId
        LEFT JOIN accounts ca ON ca.id = t.counterAccountId
        JOIN currencies c ON c.id = t.currencyId
        LEFT JOIN currencies cc ON cc.id = t.counterCurrencyId
        LEFT JOIN categories cat ON cat.id = t.categoryId
        WHERE t.id = :id
        """
    )
    fun txDetailFlow(id: String): Flow<TxDetailRow?>

    // Filtros del listado /movimientos: mes [start, end), cuenta (lado
    // origen, como buildTxWhere), categoría y tipo. NULL = sin filtro.
    @Query(
        """
        $TX_JOIN
        WHERE (:start IS NULL OR t.occurredAt >= :start)
          AND (:end IS NULL OR t.occurredAt < :end)
          AND (:accountId IS NULL OR t.accountId = :accountId)
          AND (:categoryId IS NULL OR t.categoryId = :categoryId)
          AND (:kind IS NULL OR t.kind = :kind)
        ORDER BY t.occurredAt DESC, t.createdAt DESC
        LIMIT :limit
        """
    )
    fun filteredRowsFlow(
        start: Long?,
        end: Long?,
        accountId: String?,
        categoryId: String?,
        kind: String?,
        limit: Int,
    ): Flow<List<TxJoinRow>>

    // Export CSV: mismos filtros, orden ascendente y sin límite.
    @Query(
        """
        $TX_JOIN
        WHERE (:start IS NULL OR t.occurredAt >= :start)
          AND (:end IS NULL OR t.occurredAt < :end)
          AND (:accountId IS NULL OR t.accountId = :accountId)
          AND (:categoryId IS NULL OR t.categoryId = :categoryId)
          AND (:kind IS NULL OR t.kind = :kind)
        ORDER BY t.occurredAt ASC, t.createdAt ASC
        """
    )
    suspend fun exportRows(
        start: Long?,
        end: Long?,
        accountId: String?,
        categoryId: String?,
        kind: String?,
    ): List<TxJoinRow>

    // Historial de una cuenta (ambos lados), como el detalle de cuenta web.
    @Query(
        """
        $TX_JOIN
        WHERE t.accountId = :accountId OR t.counterAccountId = :accountId
        ORDER BY t.occurredAt DESC, t.createdAt DESC
        LIMIT :limit
        """
    )
    fun accountRowsFlow(accountId: String, limit: Int): Flow<List<TxJoinRow>>

    // Serie del dashboard: INCOME/EXPENSE desde el inicio del rango.
    @Query(
        """
        SELECT t.kind, t.amountMinor, t.occurredAt, t.currencyId,
            c.code AS currencyCode, c.decimalPlaces AS currencyDecimals,
            cat.name AS categoryName
        FROM transactions t
        JOIN currencies c ON c.id = t.currencyId
        LEFT JOIN categories cat ON cat.id = t.categoryId
        WHERE t.kind IN ('INCOME', 'EXPENSE') AND t.occurredAt >= :rangeStart
        """
    )
    fun metricRowsFlow(rangeStart: Long): Flow<List<MetricRow>>

    // Desglose de denominaciones del detalle de movimiento, por lado.
    @Query(
        """
        SELECT l.id, l.accountId, a.name AS accountName, l.quantity,
            d.valueMinor, d.kind
        FROM transaction_denominations l
        JOIN denominations d ON d.id = l.denominationId
        JOIN accounts a ON a.id = l.accountId
        WHERE l.transactionId = :txId
        """
    )
    fun txDenominationLinesFlow(txId: String): Flow<List<TxDenomLineRow>>

    // Desgloses que afectan a una caja (stock derivado de denominations.ts):
    // el filtrado por "posteriores al último arqueo" se hace en el repo.
    @Query(
        """
        SELECT l.denominationId, l.quantity, l.transactionId,
            t.kind AS txKind, t.accountId AS txAccountId, t.occurredAt
        FROM transaction_denominations l
        JOIN transactions t ON t.id = l.transactionId
        WHERE l.accountId = :accountId
        """
    )
    fun txLinesForAccountFlow(accountId: String): Flow<List<TxLineWithTx>>

    // Vínculo del movimiento con una deuda (abono directo).
    @Query(
        """
        SELECT dp.debtId, d.description, ct.name AS contactName
        FROM debt_payments dp
        JOIN debts d ON d.id = dp.debtId
        JOIN contacts ct ON ct.id = d.contactId
        WHERE dp.transactionId = :txId
        """
    )
    fun debtLinkFlow(txId: String): Flow<DebtLinkRow?>

    // Vínculo del movimiento con la cuota de un plan.
    @Query(
        """
        SELECT i.planId, p.description, p.debtId,
            ct.name AS contactName,
            dct.name AS debtContactName,
            d.description AS debtDescription
        FROM installments i
        JOIN payment_plans p ON p.id = i.planId
        LEFT JOIN contacts ct ON ct.id = p.contactId
        LEFT JOIN debts d ON d.id = p.debtId
        LEFT JOIN contacts dct ON dct.id = d.contactId
        WHERE i.transactionId = :txId
        """
    )
    fun planLinkFlow(txId: String): Flow<PlanLinkRow?>
}

