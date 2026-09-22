package com.lolo.changebox.data.repo

import androidx.room.withTransaction
import com.lolo.changebox.data.ActionError
import com.lolo.changebox.data.ActionResult
import com.lolo.changebox.data.atNoonMillis
import com.lolo.changebox.data.fail
import com.lolo.changebox.data.guarded
import com.lolo.changebox.data.local.ChangeboxDatabase
import com.lolo.changebox.data.local.entity.InstallmentEntity
import com.lolo.changebox.data.local.entity.PaymentPlanEntity
import com.lolo.changebox.data.local.entity.TransactionEntity
import com.lolo.changebox.data.ok
import com.lolo.changebox.data.toLocalDate
import com.lolo.changebox.domain.Frequency
import com.lolo.changebox.domain.MoneyException
import com.lolo.changebox.domain.fmtMinor
import com.lolo.changebox.domain.nextDueDate
import com.lolo.changebox.domain.parseAmountToMinor
import java.time.LocalDate

// Núcleo de planes de cuotas, port 1:1 de lib/services/plan-service.ts:
// saldar/omitir una cuota genera la siguiente vía advancePlan (recurrencia en
// domain/Dates.kt con clamp de fin de mes) o desactiva el plan.

data class CreatePlanInput(
    val debtId: String? = null,
    val contactId: String? = null,
    val kind: String, // COLLECT | PAY
    val description: String,
    val currencyId: String,
    val accountId: String? = null,
    val amount: String,
    val frequency: String,
    val firstDueAt: LocalDate,
    val endAt: LocalDate? = null,
)

class PlanRepository(private val db: ChangeboxDatabase) {

    private val planDao = db.planDao()
    private val debtDao = db.debtDao()
    private val accountDao = db.accountDao()
    private val catalogDao = db.catalogDao()
    private val txDao = db.transactionDao()

    fun standalonePlansFlow() = planDao.standalonePlansFlow()
    fun planDetailFlow(id: String) = planDao.planDetailFlow(id)
    fun allPlansFlow() = planDao.allPlansFlow()
    fun installmentsFlow(planId: String, limit: Int = 24) =
        planDao.installmentsFlow(planId, limit)
    fun upcomingInstallmentsFlow(limitMillis: Long) =
        planDao.upcomingInstallmentsFlow(limitMillis)

    suspend fun createPlan(data: CreatePlanInput): ActionResult<String> = guarded("No se pudo crear el plan") {
        if (data.description.isBlank()) return@guarded fail("Indica la descripción")

        val currency = catalogDao.currencyById(data.currencyId)
        if (currency == null || !currency.active) return@guarded fail("Moneda no válida")

        var accountId: String? = null
        if (data.accountId != null) {
            val account = accountDao.accountById(data.accountId)
            if (account == null || account.archived) return@guarded fail("Cuenta no válida")
            if (account.currencyId != currency.id) {
                return@guarded fail("La cuenta debe estar en ${currency.code} (la moneda del plan)")
            }
            accountId = account.id
        }

        val amountMinor = try {
            parseAmountToMinor(data.amount, currency.toMinor())
        } catch (e: MoneyException) {
            return@guarded fail(e.message ?: "Monto inválido")
        }
        if (amountMinor <= 0) return@guarded fail("La cuota debe ser mayor que cero")
        if (data.endAt != null && data.endAt.isBefore(data.firstDueAt)) {
            return@guarded fail("El fin no puede ser antes del inicio")
        }

        val firstDueMillis = data.firstDueAt.atNoonMillis()
        val plan = PaymentPlanEntity(
            debtId = data.debtId,
            contactId = data.contactId,
            kind = data.kind,
            description = data.description.trim(),
            currencyId = currency.id,
            accountId = accountId,
            amountMinor = amountMinor,
            frequency = data.frequency,
            dayOfMonth = if (data.frequency == "MONTHLY") data.firstDueAt.dayOfMonth else null,
            nextDueAt = firstDueMillis,
            endAt = data.endAt?.atNoonMillis(),
        )
        db.withTransaction {
            planDao.insertPlan(plan)
            planDao.insertInstallment(
                InstallmentEntity(
                    planId = plan.id,
                    dueAt = firstDueMillis,
                    amountMinor = amountMinor,
                )
            )
        }
        return@guarded ok(plan.id)
    }

    suspend fun setPlanAccount(planId: String, accountId: String?): ActionResult<String> = guarded("No se pudo actualizar la cuenta") {
        val plan = planDao.planById(planId) ?: return@guarded fail("Plan no encontrado")
        val currency = catalogDao.currencyById(plan.currencyId) ?: return@guarded fail("Moneda no válida")

        if (accountId != null) {
            val account = accountDao.accountById(accountId)
            if (account == null || account.archived) return@guarded fail("Cuenta no válida")
            if (account.currencyId != plan.currencyId) {
                return@guarded fail("La cuenta debe estar en ${currency.code} (la moneda del plan)")
            }
        }

        planDao.setPlanAccount(plan.id, accountId)
        return@guarded ok(plan.id)
    }

    /**
     * Genera la siguiente cuota tras saldar/omitir la actual, o desactiva el
     * plan si la frecuencia es única, se alcanzó endAt o la deuda se saldó.
     */
    private suspend fun advancePlan(
        plan: PaymentPlanEntity,
        currentDueAt: Long,
        debtSettled: Boolean,
    ) {
        val next = nextDueDate(
            currentDueAt.toLocalDate(),
            Frequency.valueOf(plan.frequency),
            plan.dayOfMonth,
        )
        val nextMillis = next?.atNoonMillis()

        val shouldContinue = !debtSettled && nextMillis != null &&
            (plan.endAt == null || nextMillis <= plan.endAt)

        if (shouldContinue) {
            // Upsert por (planId, dueAt): si la cuota ya existe, no se duplica.
            if (planDao.installmentByPlanDue(plan.id, nextMillis!!) == null) {
                planDao.insertInstallment(
                    InstallmentEntity(
                        planId = plan.id,
                        dueAt = nextMillis,
                        amountMinor = plan.amountMinor,
                    )
                )
            }
            planDao.setNextDue(plan.id, nextMillis)
        } else {
            planDao.deactivatePlan(plan.id)
        }
    }

    data class SettleOutcome(
        val id: String,
        val planId: String,
        val debtId: String?,
        val accountId: String,
    )

    suspend fun settleInstallment(
        installmentId: String,
        accountId: String,
        amount: String? = null,
        note: String? = null,
    ): ActionResult<SettleOutcome> = guarded("No se pudo registrar el pago de la cuota") {
        val installment = planDao.installmentById(installmentId)
        if (installment == null || installment.status != "PENDING") {
            return@guarded fail("La cuota no está pendiente")
        }
        val plan = planDao.planById(installment.planId) ?: return@guarded fail("Plan no encontrado")
        val currency = catalogDao.currencyById(plan.currencyId) ?: return@guarded fail("Moneda no válida")

        val account = accountDao.accountById(accountId)
        if (account == null || account.archived) return@guarded fail("Cuenta no válida")
        if (account.currencyId != plan.currencyId) {
            return@guarded fail("La cuenta debe estar en ${currency.code}")
        }

        var amountMinor = installment.amountMinor
        if (!amount.isNullOrBlank()) {
            amountMinor = try {
                parseAmountToMinor(amount, currency.toMinor())
            } catch (e: MoneyException) {
                return@guarded fail(e.message ?: "Monto inválido")
            }
        }
        if (amountMinor <= 0) return@guarded fail("El monto debe ser mayor que cero")

        return@guarded try {
            db.withTransaction {
                // Releído dentro de la transacción: evita sobrepagos por doble envío.
                var debtSettled = false
                if (plan.debtId != null) {
                    val debt = debtDao.debtById(plan.debtId)
                        ?: throw ActionError("Deuda no encontrada")
                    val remaining = debt.totalMinor - debtDao.paidSum(plan.debtId)
                    if (amountMinor > remaining) {
                        throw ActionError(
                            "El monto supera el saldo pendiente de la deuda " +
                                "(${fmtMinor(remaining, currency.toDisplay())})"
                        )
                    }
                    debtSettled = amountMinor == remaining
                }

                val transaction = TransactionEntity(
                    kind = if (plan.kind == "COLLECT") "INCOME" else "EXPENSE",
                    accountId = account.id,
                    amountMinor = amountMinor,
                    currencyId = plan.currencyId,
                    note = note?.takeIf { it.isNotBlank() } ?: "${plan.description} · cuota",
                )
                txDao.insertTransaction(transaction)

                planDao.updateInstallment(
                    installment.copy(
                        status = "PAID",
                        transactionId = transaction.id,
                        settledAt = System.currentTimeMillis(),
                    )
                )

                if (plan.debtId != null) {
                    debtDao.insertPayment(
                        com.lolo.changebox.data.local.entity.DebtPaymentEntity(
                            debtId = plan.debtId,
                            transactionId = transaction.id,
                            amountMinor = amountMinor,
                        )
                    )
                    if (debtSettled) {
                        debtDao.setDebtStatus(plan.debtId, "PAID")
                    }
                }

                advancePlan(plan, installment.dueAt, debtSettled)
            }
            ok(SettleOutcome(installment.id, plan.id, plan.debtId, account.id))
        } catch (e: ActionError) {
            fail(e.message ?: "No se pudo registrar la cuota")
        }
    }

    data class SkipOutcome(val id: String, val planId: String, val debtId: String?)

    suspend fun skipInstallment(installmentId: String): ActionResult<SkipOutcome> = guarded("No se pudo omitir la cuota") {
        val installment = planDao.installmentById(installmentId)
        if (installment == null || installment.status != "PENDING") {
            return@guarded fail("La cuota no está pendiente")
        }
        val plan = planDao.planById(installment.planId) ?: return@guarded fail("Plan no encontrado")

        db.withTransaction {
            planDao.updateInstallment(installment.copy(status = "SKIPPED"))
            advancePlan(plan, installment.dueAt, false)
        }
        return@guarded ok(SkipOutcome(installment.id, plan.id, plan.debtId))
    }

    suspend fun deactivatePlan(planId: String): ActionResult<String> = guarded("No se pudo desactivar el plan") {
        val plan = planDao.planById(planId) ?: return@guarded fail("Plan no encontrado")
        db.withTransaction {
            planDao.deactivatePlan(plan.id)
            planDao.skipPendingOfPlan(plan.id)
        }
        return@guarded ok(plan.id)
    }

    /**
     * Eliminar un plan borra también todas sus cuotas (Cascade). Los
     * movimientos de cuotas ya saldadas se CONSERVAN.
     */
    suspend fun deletePlan(planId: String): ActionResult<String?> = guarded("No se pudo eliminar el plan") {
        val plan = planDao.planById(planId) ?: return@guarded fail("Plan no encontrado")
        planDao.deletePlan(plan.id)
        return@guarded ok(plan.debtId)
    }
}


