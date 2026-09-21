package com.lolo.changebox.domain

// Lógica pura del saldo derivado, portada 1:1 de balances-core.ts:
//   + INCOME y ADJUSTMENT (con signo) sobre la cuenta
//   − EXPENSE y TRANSFER saliente
//   + TRANSFER entrante (counterAmountMinor, en la moneda de la cuenta destino)

data class OwnKindGroup(
    val accountId: String,
    val kind: String,
    val sumMinor: Long,
)

data class IncomingTransferGroup(
    val accountId: String,
    val sumMinor: Long,
)

fun signedKindMinor(kind: String, sumMinor: Long): Long = when (kind) {
    "INCOME", "ADJUSTMENT" -> sumMinor
    "EXPENSE", "TRANSFER" -> -sumMinor
    // Un kind desconocido corrompería el saldo en silencio: mejor fallar.
    else -> throw IllegalArgumentException("Tipo de transacción desconocido: $kind")
}

/**
 * Saldo por cuenta a partir de sumas agrupadas (GROUP BY) en vez de leer el
 * libro mayor fila a fila: `own` son las sumas por cuenta+kind y `incoming`
 * las transferencias entrantes agregadas por cuenta destino.
 */
fun balancesFromGroups(
    own: List<OwnKindGroup>,
    incoming: List<IncomingTransferGroup>,
): Map<String, Long> {
    val balances = mutableMapOf<String, Long>()
    for (group in own) {
        balances[group.accountId] =
            (balances[group.accountId] ?: 0L) + signedKindMinor(group.kind, group.sumMinor)
    }
    for (group in incoming) {
        balances[group.accountId] = (balances[group.accountId] ?: 0L) + group.sumMinor
    }
    return balances
}

/** Total de una divisa (sin conversión). */
data class CurrencyTotal<C>(val currency: C, val totalMinor: Long)

/**
 * Suma saldos por moneda SIN convertir, en orden de primera aparición. Port
 * de `totalsByCurrency` de balances-core.ts: la usan los chips por divisa de
 * los grupos de cuentas y el gadget de totales por moneda de Inicio.
 */
fun <C> totalsByCurrency(
    accounts: List<Pair<C, Long>>,
    currencyId: (C) -> String,
): List<CurrencyTotal<C>> {
    val byId = LinkedHashMap<String, CurrencyTotal<C>>()
    for ((currency, balanceMinor) in accounts) {
        val id = currencyId(currency)
        val existing = byId[id]
        byId[id] = if (existing != null) {
            existing.copy(totalMinor = existing.totalMinor + balanceMinor)
        } else {
            CurrencyTotal(currency, balanceMinor)
        }
    }
    return byId.values.toList()
}
