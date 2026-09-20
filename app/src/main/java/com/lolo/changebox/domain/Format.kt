package com.lolo.changebox.domain

import java.text.Normalizer

// Formateo portado 1:1 de fantastic-eureka/src/lib/format.ts. El agrupado de
// miles es MANUAL (espacio fino europeo NO: espacio normal, como la web) —
// nunca NumberFormat, cuyo separador cambia con el locale del dispositivo.

interface DisplayCurrency : MinorCurrency {
    val code: String
}

data class DisplayCurrencyOf(
    override val code: String,
    override val decimalPlaces: Int,
) : DisplayCurrency

private fun groupThousands(digits: String): String {
    val sb = StringBuilder()
    val offset = digits.length % 3
    for (i in digits.indices) {
        if (i != 0 && (i - offset) % 3 == 0) sb.append(' ')
        sb.append(digits[i])
    }
    return sb.toString()
}

/**
 * Miles con espacio + código (ej. "4 750 CUP"). Los decimales se omiten solo
 * cuando son todo ceros ("250 CUP", nunca "250.00 CUP"; "250.50 USD" sí).
 */
fun fmtMinor(minor: Long, currency: DisplayCurrency): String {
    val dec = currency.decimalPlaces
    val negative = minor < 0
    val abs = if (minor < 0) -minor else minor
    val base = pow10(dec)

    val intPart = (abs / base).toString()
    val decValue = abs % base
    val withThousands = groupThousands(intPart)
    val amount = if (dec > 0 && decValue != 0L) {
        "$withThousands.${decValue.toString().padStart(dec, '0')}"
    } else {
        withThousands
    }

    return "${if (negative) "-" else ""}$amount ${currency.code}"
}

/** Como fmtMinor pero con "+" explícito para montos positivos (historiales). */
fun fmtSignedMinor(minor: Long, currency: DisplayCurrency): String =
    "${if (minor > 0) "+" else ""}${fmtMinor(minor, currency)}"

/** Tasa escalada → texto ("435.5", "1.0842"); recorta ceros finales. */
fun fmtRate(rateScaled: Long): String {
    val intPart = (rateScaled / RATE_SCALE).toString()
    val frac = (rateScaled % RATE_SCALE).toString().padStart(4, '0').trimEnd('0')
    val withThousands = groupThousands(intPart)
    return if (frac.isNotEmpty()) "$withThousands.$frac" else withThousands
}

/** Entero menor → texto para inputs ("12.50", "4750"); sin separador de miles. */
fun minorToInput(minor: Long, decimalPlaces: Int): String {
    val negative = minor < 0
    val abs = if (minor < 0) -minor else minor
    val base = pow10(decimalPlaces)
    val intPart = (abs / base).toString()
    val decValue = abs % base
    val text = if (decimalPlaces > 0 && decValue != 0L) {
        "$intPart.${decValue.toString().padStart(decimalPlaces, '0')}"
    } else {
        intPart
    }
    return if (negative) "-$text" else text
}

private val DIACRITICS = Regex("[\\u0300-\\u036f]")

/** Normaliza para búsqueda: minúsculas y sin acentos ("perez" ↔ "Pérez"). */
fun normalizeText(value: String): String =
    DIACRITICS.replace(Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD), "")

