package com.lolo.changebox.domain

// Lógica pura del conteo de efectivo por denominaciones (arqueo y
// calculadora), portada 1:1 de counting.ts.

data class CountableDenomination(
    val id: String,
    val valueMinor: Long,
)

/** Tope defensivo de piezas por denominación (evita overflow del subtotal). */
const val MAX_QTY = 1_000_000

/** Normaliza una cantidad de piezas: entero, sin negativos, con tope. */
fun clampQty(value: Int): Int = value.coerceIn(0, MAX_QTY)

/** Total contado en unidades menores: Σ valor × cantidad. */
fun countedTotalMinor(
    denominations: List<CountableDenomination>,
    quantities: Map<String, Int>,
): Long = denominations.sumOf { it.valueMinor * (quantities[it.id] ?: 0) }

/** Número total de piezas (billetes + monedas) contadas. */
fun countedPieces(quantities: Map<String, Int>): Int = quantities.values.sum()

/**
 * Signo del desglose de denominaciones de un movimiento respecto a UNA caja:
 * +1 entra (INCOME, destino de TRANSFER), −1 sale (EXPENSE, origen de
 * TRANSFER), 0 sin desglose (ADJUSTMENT los repone el propio arqueo).
 */
fun movementLineSign(kind: String, isOrigin: Boolean): Int = when (kind) {
    "INCOME" -> 1
    "EXPENSE" -> -1
    "TRANSFER" -> if (isOrigin) -1 else 1
    else -> 0
}

data class SuggestibleDenomination(
    val id: String,
    val valueMinor: Long,
    /** Tope de piezas (stock al sacar dinero); null = sin límite. */
    val available: Int? = null,
)

// Presupuesto de pasos del backtracking: corta combinaciones patológicas
// sin bloquear el hilo (con denominaciones reales sobra de largo).
private const val SUGGEST_MAX_STEPS = 200_000

/**
 * Sugiere un desglose exacto de `targetMinor` con las denominaciones dadas,
 * mayor-primero con backtracking (cubre sistemas no canónicos como el
 * billete de 3 CUP) y memoización de estados fallidos. Devuelve id→cantidad
 * (solo > 0), mapa vacío para monto 0 y null si no hay combinación exacta.
 */
fun suggestDistribution(
    denominations: List<SuggestibleDenomination>,
    targetMinor: Long,
): Map<String, Int>? {
    if (targetMinor < 0) return null
    if (targetMinor == 0L) return emptyMap()

    val sorted = denominations
        .filter { it.valueMinor > 0 && (it.available ?: 1) > 0 }
        .sortedByDescending { it.valueMinor }

    val failed = HashSet<String>()
    var steps = 0

    fun solve(index: Int, remaining: Long): MutableMap<String, Int>? {
        if (remaining == 0L) return mutableMapOf()
        if (index >= sorted.size) return null
        if (steps++ > SUGGEST_MAX_STEPS) return null

        val key = "$index:$remaining"
        if (key in failed) return null

        val (id, valueMinor, available) = sorted[index]
        val maxQty = minOf(
            remaining / valueMinor,
            (available ?: Int.MAX_VALUE).toLong(),
        ).toInt()
        for (qty in maxQty downTo 0) {
            val rest = solve(index + 1, remaining - qty.toLong() * valueMinor)
            if (rest != null) {
                if (qty > 0) rest[id] = qty
                return rest
            }
        }

        failed.add(key)
        return null
    }

    return solve(0, targetMinor)
}

