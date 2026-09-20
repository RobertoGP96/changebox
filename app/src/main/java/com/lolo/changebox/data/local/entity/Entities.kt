package com.lolo.changebox.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

// Esquema local de Changebox, espejo 1:1 del prisma/schema.prisma de la web SIN
// nada de multi-tenancy ni sincronización: app de un solo usuario, offline.
// Montos SIEMPRE en unidades menores (Long); tasas escaladas ×10 000; fechas
// en epoch millis. Los kind/status/direction son strings validados con las
// constantes de domain/Domain.kt (mismos valores que la web).

fun newId(): String = UUID.randomUUID().toString()

@Entity(
    tableName = "currencies",
    indices = [Index(value = ["code"], unique = true)],
)
data class CurrencyEntity(
    @PrimaryKey val id: String = newId(),
    val code: String,
    val name: String,
    val symbol: String,
    val decimalPlaces: Int = 2,
    val isBase: Boolean = false,
    val active: Boolean = true,
    // CASH (efectivo) | DIGITAL (sin efectivo, ej. MLC). Una moneda digital
    // no lleva denominaciones ni admite cuentas de efectivo/caja.
    // El defaultValue debe coincidir con el DEFAULT de MIGRATION_1_2: Room
    // compara ambos esquemas al abrir la BD migrada.
    @ColumnInfo(defaultValue = "CASH") val kind: String = "CASH",
)

@Entity(
    tableName = "denominations",
    foreignKeys = [
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["id"],
            childColumns = ["currencyId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("currencyId"),
        Index(value = ["currencyId", "valueMinor", "kind"], unique = true),
    ],
)
data class DenominationEntity(
    @PrimaryKey val id: String = newId(),
    val currencyId: String,
    val valueMinor: Long,
    val kind: String, // BILL | COIN
    val active: Boolean = true,
)

// Agrupación libre de cuentas (Negocio, Personal, etc.). Borrar un grupo deja
// sus cuentas "Sin grupo" (SET_NULL, igual que la web).
@Entity(
    tableName = "account_groups",
    indices = [Index(value = ["name"], unique = true)],
)
data class AccountGroupEntity(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "accounts",
    foreignKeys = [
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["id"],
            childColumns = ["currencyId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AccountGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("currencyId"), Index("groupId")],
)
data class AccountEntity(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val type: String, // CASH | CASH_BOX | BANK | DIGITAL
    val currencyId: String,
    val groupId: String? = null,
    val icon: String? = null,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name", "kind"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val kind: String, // EXPENSE | INCOME
    val icon: String? = null,
    val active: Boolean = true,
)

// Libro mayor. amountMinor es positivo salvo en ADJUSTMENT (con signo).
// TRANSFER: accountId = origen, counterAccountId = destino; counterAmountMinor
// va en la moneda del destino. En INCOME/EXPENSE multi-moneda, el monto
// original queda en counterAmountMinor + counterCurrencyId y rateScaled es la
// tasa implícita informativa.
// Los CASCADE de accountId/counterAccountId replican el borrado explícito de
// la web al eliminar una cuenta: caen los movimientos de AMBOS lados.
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["counterAccountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["id"],
            childColumns = ["currencyId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["id"],
            childColumns = ["counterCurrencyId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("accountId"),
        Index("counterAccountId"),
        Index("currencyId"),
        Index("counterCurrencyId"),
        Index("categoryId"),
        Index(value = ["accountId", "occurredAt"]),
        Index("occurredAt"),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String = newId(),
    val kind: String, // INCOME | EXPENSE | TRANSFER | ADJUSTMENT
    val accountId: String,
    val counterAccountId: String? = null,
    val amountMinor: Long,
    val currencyId: String,
    val counterAmountMinor: Long? = null,
    val counterCurrencyId: String? = null,
    val rateScaled: Long? = null,
    val categoryId: String? = null,
    val note: String? = null,
    val occurredAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
)

// Desglose de denominaciones de un movimiento en una caja CASH_BOX.
// accountId identifica el LADO del movimiento (en TRANSFER puede haber
// desglose de origen y de destino); el signo se deriva del kind.
@Entity(
    tableName = "transaction_denominations",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DenominationEntity::class,
            parentColumns = ["id"],
            childColumns = ["denominationId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("transactionId"),
        Index("accountId"),
        Index("denominationId"),
        Index(value = ["transactionId", "accountId", "denominationId"], unique = true),
    ],
)
data class TransactionDenominationEntity(
    @PrimaryKey val id: String = newId(),
    val transactionId: String,
    val accountId: String,
    val denominationId: String,
    val quantity: Int,
)

// Tasa manual entre un PAR de monedas: unidades de `toCurrency` por 1 unidad
// de `fromCurrency`, ×10 000. Histórico puro: nunca se sobreescribe, la
// vigente por par es la de mayor effectiveAt.
@Entity(
    tableName = "exchange_rates",
    foreignKeys = [
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["id"],
            childColumns = ["fromCurrencyId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["id"],
            childColumns = ["toCurrencyId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("fromCurrencyId"),
        Index("toCurrencyId"),
        Index(value = ["fromCurrencyId", "toCurrencyId", "effectiveAt"]),
        Index("effectiveAt"),
    ],
)
data class ExchangeRateEntity(
    @PrimaryKey val id: String = newId(),
    val fromCurrencyId: String,
    val toCurrencyId: String,
    val rateScaled: Long,
    val source: String = "MANUAL",
    val effectiveAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "cash_counts",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["adjustmentTxId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("accountId"),
        Index(value = ["adjustmentTxId"], unique = true),
    ],
)
data class CashCountEntity(
    @PrimaryKey val id: String = newId(),
    val accountId: String,
    val countedAt: Long = System.currentTimeMillis(),
    val totalMinor: Long,
    val expectedMinor: Long,
    val differenceMinor: Long,
    val note: String? = null,
    val adjustmentTxId: String? = null,
)

@Entity(
    tableName = "cash_count_lines",
    foreignKeys = [
        ForeignKey(
            entity = CashCountEntity::class,
            parentColumns = ["id"],
            childColumns = ["cashCountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DenominationEntity::class,
            parentColumns = ["id"],
            childColumns = ["denominationId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("cashCountId"),
        Index("denominationId"),
        Index(value = ["cashCountId", "denominationId"], unique = true),
    ],
)
data class CashCountLineEntity(
    @PrimaryKey val id: String = newId(),
    val cashCountId: String,
    val denominationId: String,
    val quantity: Int,
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val phone: String? = null,
    val note: String? = null,
)

@Entity(
    tableName = "debts",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["id"],
            childColumns = ["currencyId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("contactId"), Index("currencyId"), Index("accountId")],
)
data class DebtEntity(
    @PrimaryKey val id: String = newId(),
    val contactId: String,
    val direction: String, // RECEIVABLE | PAYABLE
    val description: String,
    val totalMinor: Long,
    val currencyId: String,
    // Cuenta preferida para los abonos (misma moneda); editable tras crear.
    val accountId: String? = null,
    val status: String = "OPEN", // OPEN | PAID | CANCELLED
    val createdAt: Long = System.currentTimeMillis(),
)

// Plan de cuotas: ligado a una deuda o standalone (mensualidad tipo renta).
// El CASCADE de debtId replica el borrado explícito de planes al eliminar la
// deuda en la web.
@Entity(
    tableName = "payment_plans",
    foreignKeys = [
        ForeignKey(
            entity = DebtEntity::class,
            parentColumns = ["id"],
            childColumns = ["debtId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["id"],
            childColumns = ["currencyId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("debtId"),
        Index("contactId"),
        Index("currencyId"),
        Index("accountId"),
    ],
)
data class PaymentPlanEntity(
    @PrimaryKey val id: String = newId(),
    val debtId: String? = null,
    val contactId: String? = null,
    val kind: String, // COLLECT | PAY
    val description: String,
    val currencyId: String,
    // Cuenta preferida para pagar/cobrar las cuotas (misma moneda); editable.
    val accountId: String? = null,
    val amountMinor: Long,
    val frequency: String, // ONCE | WEEKLY | BIWEEKLY | MONTHLY
    val dayOfMonth: Int? = null,
    val nextDueAt: Long,
    val endAt: Long? = null,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

// Cuota materializada. VENCIDA es derivado: PENDING con dueAt < ahora.
// Si se elimina el movimiento, la cuota conserva su estado (SET_NULL).
@Entity(
    tableName = "installments",
    foreignKeys = [
        ForeignKey(
            entity = PaymentPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("planId"),
        Index(value = ["transactionId"], unique = true),
        Index(value = ["planId", "dueAt"], unique = true),
    ],
)
data class InstallmentEntity(
    @PrimaryKey val id: String = newId(),
    val planId: String,
    val dueAt: Long,
    val amountMinor: Long,
    val status: String = "PENDING", // PENDING | PAID | SKIPPED
    val transactionId: String? = null,
    val settledAt: Long? = null,
)

// Abono a una deuda, siempre ligado a un movimiento del libro mayor. Si el
// movimiento cae (p. ej. al eliminar la cuenta), el abono cae con él y el
// pendiente de la deuda "reaparece" al ser derivado — igual que la web.
@Entity(
    tableName = "debt_payments",
    foreignKeys = [
        ForeignKey(
            entity = DebtEntity::class,
            parentColumns = ["id"],
            childColumns = ["debtId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("debtId"),
        Index(value = ["transactionId"], unique = true),
    ],
)
data class DebtPaymentEntity(
    @PrimaryKey val id: String = newId(),
    val debtId: String,
    val transactionId: String,
    val amountMinor: Long,
    @ColumnInfo(name = "paidAt") val paidAt: Long = System.currentTimeMillis(),
)

