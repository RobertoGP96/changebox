package com.lolo.changebox.domain

// Texto plano del conteo de efectivo para compartir, portado de la función
// pura `buildCountShareText` de count-share.tsx. Aquí NO hay nada de Android:
// el Intent de compartir y el portapapeles viven en la capa de UI.

data class ShareableDenomination(
    val id: String,
    val valueMinor: Long,
    /** Nombre serializado de DenominationKind ("BILL"/"COIN"). */
    val kind: String,
)

/** Etiqueta en español del tipo; si el valor es desconocido se deja tal cual. */
private fun kindLabel(kind: String): String =
    DenominationKind.entries.firstOrNull { it.name == kind }?.labelEs ?: kind

/**
 * Texto del conteo (denominación × cantidad = subtotal) con un pie de total y
 * piezas, listo para compartir por cualquier app.
 */
fun buildCountShareText(
    denominations: List<ShareableDenomination>,
    quantities: Map<String, Int>,
    currency: DisplayCurrency,
): String {
    val lines = denominations
        .filter { (quantities[it.id] ?: 0) > 0 }
        .map { denom ->
            val qty = quantities[denom.id] ?: 0
            val subtotal = fmtMinor(denom.valueMinor * qty, currency)
            "${fmtMinor(denom.valueMinor, currency)} (${kindLabel(denom.kind)}) × $qty = $subtotal"
        }

    // El total y las piezas se reutilizan de Counting.kt para no duplicar
    // la aritmética del arqueo.
    val totalMinor = countedTotalMinor(
        denominations.map { CountableDenomination(it.id, it.valueMinor) },
        quantities,
    )
    val pieces = countedPieces(quantities)
    val piecesLabel = if (pieces == 1) "1 pieza" else "$pieces piezas"

    return buildList {
        add("Conteo de efectivo · ${currency.code}")
        addAll(lines)
        add("Total: ${fmtMinor(totalMinor, currency)} · $piecesLabel")
    }.joinToString("\n")
}
