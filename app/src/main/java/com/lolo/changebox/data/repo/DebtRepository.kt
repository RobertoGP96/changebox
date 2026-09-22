package com.lolo.changebox.data.repo

import androidx.room.withTransaction
import com.lolo.changebox.data.ActionError
import com.lolo.changebox.data.ActionResult
import com.lolo.changebox.data.fail
import com.lolo.changebox.data.guarded
import com.lolo.changebox.data.local.ChangeboxDatabase
import com.lolo.changebox.data.local.entity.ContactEntity
import com.lolo.changebox.data.local.entity.DebtEntity
import com.lolo.changebox.data.local.entity.DebtPaymentEntity
import com.lolo.changebox.data.local.entity.InstallmentEntity
import com.lolo.changebox.data.local.entity.PaymentPlanEntity
import com.lolo.changebox.data.local.entity.TransactionEntity
import com.lolo.changebox.data.atNoonMillis
import com.lolo.changebox.data.ok
import com.lolo.changebox.domain.MoneyException
import com.lolo.changebox.domain.fmtMinor
import com.lolo.changebox.domain.parseAmountToMinor
import java.time.LocalDate

// Núcleo de deudas, port 1:1 de lib/services/debt-service.ts. El pendiente es
// SIEMPRE derivado (totalMinor − Σ abonos) y se relee DENTRO de la
// transacción que registra el abono para evitar sobrepagos por doble envío.

data class CreateDebtInput(
    val contactId: String? = null,
    val contactName: String? = null,
    val direction: String, // RECEIVABLE | PAYABLE
    val description: String,
    val total: String,
    val currencyId: String,
    // Cuenta preferida para los abonos (opcional, misma moneda)
    val accountId: String? = null,
    // Plan de cuotas opcional al crear la deuda
    val frequency: String? = null,
    val installmentAmount: String? = null,
    val firstDueAt: LocalDate? = null,
)

class DebtRepository(private val db: ChangeboxDatabase) {

    private val debtDao = db.debtDao()
    private val planDao = db.planDao()
    private val accountDao = db.accountDao()
    private val catalogDao = db.catalogDao()
    private val txDao = db.transactionDao()

    fun debtsWithMetaFlow(direction: String?) = debtDao.debtsWithMetaFlow(direction)
    fun debtWithMetaFlow(id: String) = debtDao.debtWithMetaFlow(id)
    fun nextDueByDebtFlow() = debtDao.nextDueByDebtFlow()
    fun paymentsFlow(debtId: String) = debtDao.paymentsFlow(debtId)
    fun contactsFlow() = debtDao.contactsFlow()
    fun pendingInstallmentsForDebtFlow(debtId: String) =
        planDao.pendingInstallmentsForDebtFlow(debtId)

    suspend fun debtRemainingMinor(debtId: String): Long {
        val debt = debtDao.debtById(debtId) ?: return 0L
        return debt.totalMinor - debtDao.paidSum(debtId)
    }

    suspend fun createDebt(data: CreateDebtInput): ActionResult<String> = guarded("No se pudo crear la deuda") {
        val planFields = listOf(data.frequency, data.installmentAmount, data.firstDueAt)
        val withPlan = planFields.all { it != null }
        if (!withPlan && planFields.any { it != null }) {
            return@guarded fail("Para el plan de cuotas indica frecuencia, cuota y primera fecha")
        }

        if (data.description.isBlank()) return@guarded fail("Indica la descripción")

        val currency = catalogDao.currencyById(data.currencyId)
        if (currency == null || !currency.active) return@guarded fail("Moneda no válida")

        // Solo validaciones antes de escribir: el contacto nuevo se crea dentro
        // de la transacción para no dejar contactos huérfanos si algo falla.
        if (data.contactId != null) {
            debtDao.contactById(data.contactId) ?: return@guarded fail("Contacto no válido")
        } else if (data.contactName.isNullOrBlank()) {
            return@guarded fail("Indica el contacto")
        }

        var accountId: String? = null
        if (data.accountId != null) {
            val account = accountDao.accountById(data.accountId)
            if (account == null || account.archived) return@guarded fail("Cuenta no válida")
            if (account.currencyId != currency.id) {
                return@guarded fail("La cuenta debe estar en ${currency.code} (la moneda de la deuda)")
            }
            accountId = account.id
        }

        val totalMinor = try {
            parseAmountToMinor(data.total, currency.toMinor())
        } catch (e: MoneyException) {
            return@guarded fail(e.message ?: "Monto inválido")
        }
        if (totalMinor <= 0) return@guarded fail("El total debe ser mayor que cero")

        var installmentMinor = 0L
        if (withPlan) {
            installmentMinor = try {
                parseAmountToMinor(data.installmentAmount!!, currency.toMinor())
            } catch (e: MoneyException) {
                return@guarded fail(e.message ?: "Monto inválido")
            }
            if (installmentMinor <= 0 || installmentMinor > totalMinor) {
                return@guarded fail("La cuota debe ser mayor que cero y no superar el total")
            }
        }

        val debt: DebtEntity = db.withTransaction {
            val contactId = data.contactId ?: ContactEntity(name = data.contactName!!.trim())
                .also { debtDao.insertContact(it) }.id
            val created = DebtEntity(
                contactId = contactId,
                direction = data.direction,
                description = data.description.trim(),
                totalMinor = totalMinor,
                currencyId = currency.id,
                accountId = accountId,
            )
            debtDao.insertDebt(created)

            if (withPlan) {
                val firstDueMillis = data.firstDueAt!!.atNoonMillis()
                val plan = PaymentPlanEntity(
                    debtId = created.id,
                    contactId = contactId,
                    kind = if (data.direction == "RECEIVABLE") "COLLECT" else "PAY",
                    description = created.description,
                    currencyId = currency.id,
                    accountId = accountId,
                    amountMinor = installmentMinor,
                    frequency = data.frequency!!,
                    dayOfMonth = if (data.frequency == "MONTHLY") data.firstDueAt.dayOfMonth else null,
                    nextDueAt = firstDueMillis,
                )
                planDao.insertPlan(plan)
                planDao.insertInstallment(
                    InstallmentEntity(
                        planId = plan.id,
                        dueAt = firstDueMillis,
                        amountMinor = installmentMinor,
                    )
                )
            }
            created
        }

        return@guarded ok(debt.id)
    }

    suspend fun setDebtAccount(debtId: String, accountId: String?): ActionResult<String> = guarded("No se pudo actualizar la cuenta") {
        val debt = debtDao.debtById(debtId) ?: return@guarded fail("Deuda no encontrada")
        val currency = catalogDao.currencyById(debt.currencyId) ?: return@guarded fail("Moneda no válida")

        if (accountId != null) {
            val account = accountDao.accountById(accountId)
            if (account == null || account.archived) return@guarded fail("Cuenta no válida")
            if (account.currencyId != debt.currencyId) {
                return@guarded fail("La cuenta debe estar en ${currency.code} (la moneda de la deuda)")
            }
        }

        debtDao.setDebtAccount(debt.id, accountId)
        return@guarded ok(debt.id)
    }

    data class PaymentOutcome(val id: String, val settled: Boolean)

    suspend fun registerDebtPayment(
        debtId: String,
        accountId: String,
        amount: String,
        note: String? = null,
    ): ActionResult<PaymentOutcome> = guarded("No se pudo registrar el abono") {
        val debt = debtDao.debtById(debtId)
        if (debt == null || debt.status != "OPEN") return@guarded fail("La deuda no está abierta")
        val currency = catalogDao.currencyById(debt.currencyId) ?: return@guarded fail("Moneda no válida")

        val account = accountDao.accountById(accountId)
        if (account == null || account.archived) return@guarded fail("Cuenta no válida")
        if (account.currencyId != debt.currencyId) {
            return@guarded fail("La cuenta debe estar en ${currency.code} (la moneda de la deuda)")
        }

        val amountMinor = try {
            parseAmountToMinor(amount, currency.toMinor())
        } catch (e: MoneyException) {
            return@guarded fail(e.message ?: "Monto inválido")
        }
        if (amountMinor <= 0) return@guarded fail("El abono debe ser mayor que cero")

        return@guarded try {
            var settled = false
            var paymentId = ""
            db.withTransaction {
                // Releído dentro de la transacción: un doble envío concurrente
                // no puede pasar la validación con un pendiente obsoleto.
                val remaining = debt.totalMinor - debtDao.paidSum(debt.id)
                if (amountMinor > remaining) {
                    throw ActionError(
                        "El abono supera el saldo pendiente (${fmtMinor(remaining, currency.toDisplay())})"
                    )
                }
                settled = amountMinor == remaining

                val transaction = TransactionEntity(
                    kind = if (debt.direction == "RECEIVABLE") "INCOME" else "EXPENSE",
                    accountId = account.id,
                    amountMinor = amountMinor,
                    currencyId = debt.currencyId,
                    note = note?.takeIf { it.isNotBlank() } ?: "Abono · ${debt.description}",
                )
                txDao.insertTransaction(transaction)

                val payment = DebtPaymentEntity(
                    debtId = debt.id,
                    transactionId = transaction.id,
                    amountMinor = amountMinor,
                )
                debtDao.insertPayment(payment)
                paymentId = payment.id

                if (settled) {
                    debtDao.setDebtStatus(debt.id, "PAID")
                    // La deuda quedó saldada: sus cuotas pendientes ya no aplican.
                    planDao.skipPendingOfDebt(debt.id)
                    planDao.deactivatePlansOfDebt(debt.id)
                }
            }
            ok(PaymentOutcome(paymentId, settled))
        } catch (e: ActionError) {
            fail(e.message ?: "No se pudo registrar el abono")
        }
    }

    /**
     * Eliminar una deuda quita el SEGUIMIENTO: sus abonos y sus planes de
     * cuotas (las cuotas caen por Cascade). Los movimientos de las cuentas se
     * CONSERVAN (los saldos no cambian; solo pierden el vínculo).
     */
    suspend fun deleteDebt(debtId: String): ActionResult<Unit> = guarded("No se pudo eliminar la deuda") {
        debtDao.debtById(debtId) ?: return@guarded fail("Deuda no encontrada")
        debtDao.deleteDebt(debtId)
        return@guarded ok(Unit)
    }
}


