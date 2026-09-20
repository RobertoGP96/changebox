package com.lolo.changebox.domain

import java.math.BigInteger

// Aritmética monetaria en enteros, portada 1:1 de fantastic-eureka/src/lib/
// money.ts: montos en unidades menores (Long) y tasas escaladas ×RATE_SCALE.
// PROHIBIDO usar Float/Double para dinero.

const val RATE_SCALE = 10_000L

/**
 * Máximo de una columna Int del servidor (4 bytes con signo). El dominio local
 * trabaja en Long; este tope se aplica en la frontera (inputs y push).
 */
const val SERVER_INT_MAX = 2_147_483_647L

/** Equivalente a Number.MAX_SAFE_INTEGER de JS (2^53 − 1): paridad de topes. */
const val SAFE_INT_MAX = 9_007_199_254_740_991L

/** Error de dominio monetario (formato/rango): permanente, no reintentable. */
class MoneyException(message: String) : Exception(message)

interface MinorCurrency {
    val decimalPlaces: Int
}

data class MinorCurrencyOf(override val decimalPlaces: Int) : MinorCurrency

private val POW10 = longArrayOf(1, 10, 100, 1_000, 10_000, 100_000, 1_000_000)

fun pow10(n: Int): Long =
    POW10.getOrNull(n) ?: throw MoneyException("Exponente fuera de rango: $n")

private val AMOUNT_REGEX = Regex("""^-?\d+(\.\d+)?$""")

/**
 * Convierte el texto de un input ("1234.56", "1 234,56") a unidades menores.
 * Redondea half-up los decimales sobrantes. Lanza MoneyException si el
 * formato es inválido.
 */
fun parseAmountToMinor(input: String, currency: MinorCurrency): Long {
    val raw = input.trim().replace(Regex("""\s"""), "").replace(",", ".")
    if (!AMOUNT_REGEX.matches(raw)) throw MoneyException("Monto inválido")

    val negative = raw.startsWith("-")
    val parts = raw.removePrefix("-").split(".")
    val intPart = parts[0]
    val decPart = parts.getOrElse(1) { "" }
    val dec = currency.decimalPlaces

    val decKept = (decPart + "0".repeat(dec)).take(dec)
    val decExtra = decPart.drop(dec)

    var minor = try {
        intPart.toLong().multiplyOrThrow(pow10(dec)) +
            (if (dec > 0) decKept.toLong() else 0L)
    } catch (e: NumberFormatException) {
        throw MoneyException("Monto demasiado grande")
    } catch (e: ArithmeticException) {
        throw MoneyException("Monto demasiado grande")
    }
    if (decExtra.isNotEmpty() && decExtra[0].digitToInt() >= 5) minor += 1

    if (minor > SAFE_INT_MAX) throw MoneyException("Monto demasiado grande")
    return if (negative) -minor else minor
}

private fun Long.multiplyOrThrow(other: Long): Long = Math.multiplyExact(this, other)

/**
 * Convierte unidades menores a texto editable para un input ("1234.5"),
 * sin separadores de miles y sin ceros decimales sobrantes.
 */
fun minorToAmountInput(valueMinor: Long, currency: MinorCurrency): String {
    val dec = currency.decimalPlaces
    val negative = valueMinor < 0
    val abs = if (valueMinor < 0) -valueMinor else valueMinor
    val base = pow10(dec)
    val intPart = (abs / base).toString()
    val frac = if (dec > 0) {
        (abs % base).toString().padStart(dec, '0').trimEnd('0')
    } else {
        ""
    }
    val result = if (frac.isNotEmpty()) "$intPart.$frac" else intPart
    return if (negative) "-$result" else result
}

fun sumMinor(values: List<Long>): Long {
    var total = 0L
    for (v in values) {
        try {
            total = Math.addExact(total, v)
        } catch (e: ArithmeticException) {
            throw MoneyException("Suma fuera de rango")
        }
    }
    if (total > SAFE_INT_MAX || total < -SAFE_INT_MAX) {
        throw MoneyException("Suma fuera de rango")
    }
    return total
}

/**
 * Convierte un monto entre monedas.
 * rateScaled = unidades de la moneda destino por 1 unidad de la de origen,
 * ×RATE_SCALE. BigInteger para no perder precisión; redondeo half-up.
 */
fun convertMinor(
    amountMinor: Long,
    from: MinorCurrency,
    to: MinorCurrency,
    rateScaled: Long,
): Long {
    if (rateScaled <= 0) throw MoneyException("Tasa inválida")
    val numerator = BigInteger.valueOf(amountMinor) *
        BigInteger.valueOf(rateScaled) *
        BigInteger.valueOf(pow10(to.decimalPlaces))
    val denominator = BigInteger.valueOf(RATE_SCALE) *
        BigInteger.valueOf(pow10(from.decimalPlaces))
    val half = if (numerator.signum() < 0) -(denominator / BigInteger.TWO) else denominator / BigInteger.TWO
    val result = (numerator + half) / denominator
    if (result.abs() > BigInteger.valueOf(SAFE_INT_MAX)) {
        throw MoneyException("Conversión fuera de rango")
    }
    return result.toLong()
}

/**
 * Convierte un monto entre monedas con la tasa citada en sentido INVERSO
 * (unidades de `from` por 1 unidad de `to`, ×RATE_SCALE). Divide con
 * BigInteger en vez de invertir la tasa, para no perder precisión.
 */
fun convertMinorInverse(
    amountMinor: Long,
    from: MinorCurrency,
    to: MinorCurrency,
    rateScaled: Long,
): Long {
    if (rateScaled <= 0) throw MoneyException("Tasa inválida")
    val numerator = BigInteger.valueOf(amountMinor) *
        BigInteger.valueOf(RATE_SCALE) *
        BigInteger.valueOf(pow10(to.decimalPlaces))
    val denominator = BigInteger.valueOf(rateScaled) *
        BigInteger.valueOf(pow10(from.decimalPlaces))
    val half = if (numerator.signum() < 0) -(denominator / BigInteger.TWO) else denominator / BigInteger.TWO
    val result = (numerator + half) / denominator
    if (result.abs() > BigInteger.valueOf(SAFE_INT_MAX)) {
        throw MoneyException("Conversión fuera de rango")
    }
    return result.toLong()
}

/**
 * Tasa implícita entre dos montos ya conocidos: unidades de la moneda de
 * `counterMinor` por 1 unidad de la de `amountMinor`, ×RATE_SCALE. Solo
 * informativa; devuelve null si no cabe en el Int del servidor o queda en 0.
 */
fun impliedRateScaled(
    amountMinor: Long,
    from: MinorCurrency,
    counterMinor: Long,
    to: MinorCurrency,
): Long? {
    if (amountMinor <= 0 || counterMinor <= 0) return null
    val numerator = BigInteger.valueOf(counterMinor) *
        BigInteger.valueOf(RATE_SCALE) *
        BigInteger.valueOf(pow10(from.decimalPlaces))
    val denominator = BigInteger.valueOf(amountMinor) *
        BigInteger.valueOf(pow10(to.decimalPlaces))
    val implied = ((numerator + denominator / BigInteger.TWO) / denominator).toLong()
    return if (implied in 1..SERVER_INT_MAX) implied else null
}

/** Invierte una tasa: X→Y escalada ⇒ Y→X escalada (1e8 / tasa, half-up). */
fun invertRateScaled(rateScaled: Long): Long {
    if (rateScaled <= 0) throw MoneyException("Tasa inválida")
    val result = (RATE_SCALE * RATE_SCALE + rateScaled / 2) / rateScaled
    if (result <= 0) throw MoneyException("Tasa resultante fuera de rango")
    return result
}

/** Compone tasas escaladas: a (X→Y) ∘ b (Y→Z) ⇒ X→Z. BigInteger, half-up. */
fun composeRatesScaled(aScaled: Long, bScaled: Long): Long {
    if (aScaled <= 0 || bScaled <= 0) throw MoneyException("Tasa inválida")
    val result = (
        (BigInteger.valueOf(aScaled) * BigInteger.valueOf(bScaled) +
            BigInteger.valueOf(RATE_SCALE / 2)) /
            BigInteger.valueOf(RATE_SCALE)
        )
    if (result.signum() <= 0 || result > BigInteger.valueOf(SAFE_INT_MAX)) {
        throw MoneyException("Tasa resultante fuera de rango")
    }
    return result.toLong()
}

/**
 * Tasa X→Y derivada de las tasas de X e Y contra la moneda base.
 * Ej.: USD→EUR = tasa(USD→base) / tasa(EUR→base).
 */
fun crossRateScaled(rateXtoBaseScaled: Long, rateYtoBaseScaled: Long): Long {
    if (rateXtoBaseScaled <= 0 || rateYtoBaseScaled <= 0) {
        throw MoneyException("Tasa inválida")
    }
    val result = (rateXtoBaseScaled * RATE_SCALE + rateYtoBaseScaled / 2) /
        rateYtoBaseScaled
    if (result <= 0) throw MoneyException("Tasa resultante fuera de rango")
    return result
}

