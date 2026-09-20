package com.lolo.changebox.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.lolo.changebox.data.local.entity.ContactEntity
import com.lolo.changebox.data.local.entity.DebtEntity
import com.lolo.changebox.data.local.entity.DebtPaymentEntity
import kotlinx.coroutines.flow.Flow

// Deudas, contactos y abonos. El pendiente NUNCA se almacena: siempre es
// totalMinor − Σ abonos (paidMinor derivado en la consulta).

data class DebtWithMeta(
    @Embedded val debt: DebtEntity,
    val contactName: String,
    val currencyCode: String,
    val currencyDecimals: Int,
    val paidMinor: Long,
)

data class DebtNextDue(
    val debtId: String,
    val dueAt: Long,
)

data class OpenDebtRow(
    val id: String,
    val direction: String,
    val totalMinor: Long,
    val currencyId: String,
    val currencyCode: String,
    val currencyDecimals: Int,
    val paidMinor: Long,
)

@Dao
interface DebtDao {

    // ── Contactos ───────────────────────────────────────────────────────────

    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun contactsFlow(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun contactById(id: String): ContactEntity?

    @Insert
    suspend fun insertContact(contact: ContactEntity)

    // ── Deudas ──────────────────────────────────────────────────────────────

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun debtById(id: String): DebtEntity?

    @Query(
        """
        SELECT d.*, ct.name AS contactName,
            cur.code AS currencyCode, cur.decimalPlaces AS currencyDecimals,
            COALESCE((SELECT SUM(p.amountMinor) FROM debt_payments p WHERE p.debtId = d.id), 0) AS paidMinor
        FROM debts d
        JOIN contacts ct ON ct.id = d.contactId
        JOIN currencies cur ON cur.id = d.currencyId
        WHERE d.id = :id
        """
    )
    fun debtWithMetaFlow(id: String): Flow<DebtWithMeta?>

    @Query(
        """
        SELECT d.*, ct.name AS contactName,
            cur.code AS currencyCode, cur.decimalPlaces AS currencyDecimals,
            COALESCE((SELECT SUM(p.amountMinor) FROM debt_payments p WHERE p.debtId = d.id), 0) AS paidMinor
        FROM debts d
        JOIN contacts ct ON ct.id = d.contactId
        JOIN currencies cur ON cur.id = d.currencyId
        WHERE d.direction = :direction
        ORDER BY d.createdAt DESC
        """
    )
    fun debtsWithMetaFlow(direction: String): Flow<List<DebtWithMeta>>

    // Próxima cuota pendiente por deuda (solo planes activos).
    @Query(
        """
        SELECT p.debtId AS debtId, MIN(i.dueAt) AS dueAt
        FROM installments i
        JOIN payment_plans p ON p.id = i.planId
        WHERE i.status = 'PENDING' AND p.active = 1 AND p.debtId IS NOT NULL
        GROUP BY p.debtId
        """
    )
    fun nextDueByDebtFlow(): Flow<List<DebtNextDue>>

    // Deudas abiertas con abonado agregado (métricas del dashboard).
    @Query(
        """
        SELECT d.id, d.direction, d.totalMinor, d.currencyId,
            cur.code AS currencyCode, cur.decimalPlaces AS currencyDecimals,
            COALESCE((SELECT SUM(p.amountMinor) FROM debt_payments p WHERE p.debtId = d.id), 0) AS paidMinor
        FROM debts d
        JOIN currencies cur ON cur.id = d.currencyId
        WHERE d.status = 'OPEN'
        """
    )
    fun openDebtsFlow(): Flow<List<OpenDebtRow>>

    @Insert
    suspend fun insertDebt(debt: DebtEntity)

    @Query("UPDATE debts SET accountId = :accountId WHERE id = :debtId")
    suspend fun setDebtAccount(debtId: String, accountId: String?)

    @Query("UPDATE debts SET status = :status WHERE id = :debtId")
    suspend fun setDebtStatus(debtId: String, status: String)

    // El CASCADE se lleva abonos y planes (y sus cuotas); los movimientos de
    // las cuentas se CONSERVAN (solo pierden el vínculo), igual que la web.
    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun deleteDebt(id: String)

    // ── Abonos ──────────────────────────────────────────────────────────────

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM debt_payments WHERE debtId = :debtId")
    suspend fun paidSum(debtId: String): Long

    @Query("SELECT * FROM debt_payments WHERE debtId = :debtId ORDER BY paidAt DESC")
    fun paymentsFlow(debtId: String): Flow<List<DebtPaymentEntity>>

    @Insert
    suspend fun insertPayment(payment: DebtPaymentEntity)

    @Query("SELECT * FROM debt_payments WHERE transactionId = :txId")
    suspend fun paymentByTransaction(txId: String): DebtPaymentEntity?

    @Update
    suspend fun updatePayment(payment: DebtPaymentEntity)

    // Al borrar el abono el pendiente reaparece: una deuda saldada vuelve a
    // estar abierta. Nunca toca una CANCELLED.
    @Query("UPDATE debts SET status = 'OPEN' WHERE id = :debtId AND status = 'PAID'")
    suspend fun reopenDebtIfPaid(debtId: String)
}

