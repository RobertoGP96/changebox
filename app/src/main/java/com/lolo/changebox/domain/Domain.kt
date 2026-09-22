package com.lolo.changebox.domain

// Constantes de dominio portadas 1:1 de fantastic-eureka/src/lib/domain.ts.
// Los valores serializados (name) viajan tal cual a la API y a Room; las
// etiquetas en español son las mismas que muestra la web.

enum class AccountType(val labelEs: String) {
    CASH("Efectivo"),
    CASH_BOX("Caja (denominaciones)"),
    BANK("Banco"),
    DIGITAL("Digital");

    companion object {
        fun from(value: String): AccountType = valueOf(value)
    }
}

/** Tipos que admiten arqueo físico con denominaciones. */
val CASH_LIKE_TYPES: Set<AccountType> = setOf(AccountType.CASH, AccountType.CASH_BOX)

fun AccountType.isCashLike(): Boolean = this in CASH_LIKE_TYPES

/**
 * Clasificación de la moneda: las digitales (ej. MLC) no existen en
 * efectivo, así que no pueden tener denominaciones ni cuentas tipo caja.
 */
enum class CurrencyKind(val labelEs: String) {
    CASH("Efectivo"),
    DIGITAL("Digital (sin efectivo)");

    companion object {
        /** Tolerante: una fila con valor desconocido se trata como efectivo. */
        fun from(value: String): CurrencyKind =
            entries.firstOrNull { it.name == value } ?: CASH
    }
}

fun isDigitalCurrencyKind(kind: String): Boolean = kind == CurrencyKind.DIGITAL.name

enum class TransactionKind(val labelEs: String) {
    INCOME("Ingreso"),
    EXPENSE("Gasto"),
    TRANSFER("Transferencia"),
    ADJUSTMENT("Ajuste");
}

enum class CategoryKind(val labelEs: String) {
    EXPENSE("Gasto"),
    INCOME("Ingreso");
}

enum class DenominationKind(val labelEs: String) {
    BILL("Billete"),
    COIN("Moneda");
}

enum class DebtDirection(val labelEs: String) {
    RECEIVABLE("Por cobrar"),
    PAYABLE("Por pagar");
}

enum class DebtStatus(val labelEs: String) {
    OPEN("Abierta"),
    PAID("Saldada"),
    CANCELLED("Cancelada");
}

enum class PlanKind(val labelEs: String) {
    COLLECT("Cobro"),
    PAY("Pago");
}

enum class Frequency(val labelEs: String) {
    ONCE("Única"),
    WEEKLY("Semanal"),
    BIWEEKLY("Quincenal"),
    MONTHLY("Mensual");
}

enum class InstallmentStatus(val labelEs: String) {
    PENDING("Pendiente"),
    PAID("Saldada"),
    SKIPPED("Omitida");
}

