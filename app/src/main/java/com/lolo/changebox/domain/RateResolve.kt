package com.lolo.changebox.domain

// Resolución pura de tasas por pares, portada 1:1 de rate-resolve.ts:
// 1) par directo, 2) par inverso, 3) composición vía la moneda base.

data class PairRateLite(
    val fromId: String,
    val toId: String,
    val rateScaled: Long,
)

fun pairKey(fromId: String, toId: String): String = "$fromId→$toId"

fun buildPairMap(pairs: List<PairRateLite>): Map<String, Long> =
    pairs.associate { pairKey(it.fromId, it.toId) to it.rateScaled }

private fun directOrInverse(
    map: Map<String, Long>,
    fromId: String,
    toId: String,
): Long? {
    map[pairKey(fromId, toId)]?.let { return it }
    val inverse = map[pairKey(toId, fromId)] ?: return null
    return try {
        invertRateScaled(inverse)
    } catch (e: MoneyException) {
        null
    }
}

/** Tasa from→to escalada, o null si no hay camino registrado. */
fun resolveRateScaled(
    map: Map<String, Long>,
    fromId: String,
    toId: String,
    baseId: String? = null,
): Long? {
    if (fromId == toId) return RATE_SCALE

    directOrInverse(map, fromId, toId)?.let { return it }

    if (baseId != null && baseId != fromId && baseId != toId) {
        val toBase = directOrInverse(map, fromId, baseId)
        val fromBase = directOrInverse(map, baseId, toId)
        if (toBase != null && fromBase != null) {
            return try {
                composeRatesScaled(toBase, fromBase)
            } catch (e: MoneyException) {
                null
            }
        }
    }

    return null
}

