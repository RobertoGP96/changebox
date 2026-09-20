package com.lolo.changebox.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.lolo.changebox.data.local.entity.InstallmentEntity
import com.lolo.changebox.data.local.entity.PaymentPlanEntity
import kotlinx.coroutines.flow.Flow

// Planes de cuotas y cuotas materializadas. VENCIDA es un estado DERIVADO
// (PENDING con dueAt pasado): no hay cron, igual que la web.

data class PlanWithMeta(
    @Embedded val plan: PaymentPlanEntity,
    val currencyCode: String,
    val currencyDecimals: Int,
    val contactName: String?,
    val nextPendingDueAt: Long?,
)

data class PlanDetailRow(
    @Embedded val plan: PaymentPlanEntity,
    val currencyCode: String,
    val currencyDecimals: Int,
    val contactName: String?,
    val debtContactName: String?,
)

data class PendingInstallmentRow(
    @Embedded val installment: InstallmentEntity,
    val planKind: String,
    val planAccountId: String?,
)

data class UpcomingInstallmentRow(
    val installmentId: String,
    val dueAt: Long,
    val amountMinor: Long,
    val planId: String,
    val debtId: String?,
    val planKind: String,
    val description: String,
    val currencyCode: String,
    val currencyDecimals: Int,
    val contactName: String?,
)

@Dao
interface PlanDao {

    @Query("SELECT * FROM payment_plans WHERE id = :id")
    suspend fun planById(id: String): PaymentPlanEntity?

    @Query(
        """
        SELECT p.*, cur.code AS currencyCode, cur.decimalPlaces AS currencyDecimals,
            ct.name AS contactName,
            dct.name AS debtContactName
        FROM payment_plans p
        JOIN currencies cur ON cur.id = p.currencyId
        LEFT JOIN contacts ct ON ct.id = p.contactId
        LEFT JOIN debts d ON d.id = p.debtId
        LEFT JOIN contacts dct ON dct.id = d.contactId
        WHERE p.id = :id
        """
    )
    fun planDetailFlow(id: String): Flow<PlanDetailRow?>

    // Mensualidades standalone activas (listado /deudas).
    @Query(
        """
        SELECT p.*, cur.code AS currencyCode, cur.decimalPlaces AS currencyDecimals,
            ct.name AS contactName,
            (SELECT MIN(i.dueAt) FROM installments i
                WHERE i.planId = p.id AND i.status = 'PENDING') AS nextPendingDueAt
        FROM payment_plans p
        JOIN currencies cur ON cur.id = p.currencyId
        LEFT JOIN contacts ct ON ct.id = p.contactId
        WHERE p.active = 1 AND p.debtId IS NULL
        ORDER BY p.nextDueAt ASC
        """
    )
    fun standalonePlansFlow(): Flow<List<PlanWithMeta>>

    @Insert
    suspend fun insertPlan(plan: PaymentPlanEntity)

    @Update
    suspend fun updatePlan(plan: PaymentPlanEntity)

    @Query("UPDATE payment_plans SET accountId = :accountId WHERE id = :planId")
    suspend fun setPlanAccount(planId: String, accountId: String?)

    @Query("UPDATE payment_plans SET nextDueAt = :nextDueAt WHERE id = :planId")
    suspend fun setNextDue(planId: String, nextDueAt: Long)

    @Query("UPDATE payment_plans SET active = 0 WHERE id = :planId")
    suspend fun deactivatePlan(planId: String)

    @Query("UPDATE payment_plans SET active = 0 WHERE debtId = :debtId")
    suspend fun deactivatePlansOfDebt(debtId: String)

    // Las cuotas caen por CASCADE.
    @Query("DELETE FROM payment_plans WHERE id = :id")
    suspend fun deletePlan(id: String)

    // ── Cuotas ──────────────────────────────────────────────────────────────

    @Query("SELECT * FROM installments WHERE id = :id")
    suspend fun installmentById(id: String): InstallmentEntity?

    @Query("SELECT * FROM installments WHERE planId = :planId AND dueAt = :dueAt")
    suspend fun installmentByPlanDue(planId: String, dueAt: Long): InstallmentEntity?

    @Query("SELECT * FROM installments WHERE planId = :planId ORDER BY dueAt DESC LIMIT :limit")
    fun installmentsFlow(planId: String, limit: Int): Flow<List<InstallmentEntity>>

    // Cuotas pendientes de los planes ACTIVOS de una deuda.
    @Query(
        """
        SELECT i.*, p.kind AS planKind, p.accountId AS planAccountId
        FROM installments i
        JOIN payment_plans p ON p.id = i.planId
        WHERE p.debtId = :debtId AND p.active = 1 AND i.status = 'PENDING'
        ORDER BY i.dueAt ASC
        """
    )
    fun pendingInstallmentsForDebtFlow(debtId: String): Flow<List<PendingInstallmentRow>>

    // Cuotas PENDING vencidas o por vencer (campana + Inicio).
    @Query(
        """
        SELECT i.id AS installmentId, i.dueAt, i.amountMinor,
            p.id AS planId, p.debtId, p.kind AS planKind, p.description,
            cur.code AS currencyCode, cur.decimalPlaces AS currencyDecimals,
            COALESCE(ct.name, dct.name) AS contactName
        FROM installments i
        JOIN payment_plans p ON p.id = i.planId
        JOIN currencies cur ON cur.id = p.currencyId
        LEFT JOIN contacts ct ON ct.id = p.contactId
        LEFT JOIN debts d ON d.id = p.debtId
        LEFT JOIN contacts dct ON dct.id = d.contactId
        WHERE i.status = 'PENDING' AND i.dueAt <= :limitMillis AND p.active = 1
        ORDER BY i.dueAt ASC
        """
    )
    fun upcomingInstallmentsFlow(limitMillis: Long): Flow<List<UpcomingInstallmentRow>>

    @Insert
    suspend fun insertInstallment(installment: InstallmentEntity)

    @Update
    suspend fun updateInstallment(installment: InstallmentEntity)

    @Query(
        """
        UPDATE installments SET status = 'SKIPPED'
        WHERE status = 'PENDING'
          AND planId IN (SELECT id FROM payment_plans WHERE debtId = :debtId)
        """
    )
    suspend fun skipPendingOfDebt(debtId: String)

    @Query("UPDATE installments SET status = 'SKIPPED' WHERE planId = :planId AND status = 'PENDING'")
    suspend fun skipPendingOfPlan(planId: String)
}

