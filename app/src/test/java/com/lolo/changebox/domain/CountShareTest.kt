package com.lolo.changebox.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Vectores portados de count-share.test.ts.

private val CUP = DisplayCurrencyOf("CUP", 2)

private val DENOMINACIONES = listOf(
    ShareableDenomination("b500", 50_000L, "BILL"),
    ShareableDenomination("b100", 10_000L, "BILL"),
    ShareableDenomination("c100", 100L, "COIN"),
)

class BuildCountShareTextTest {
    @Test
    fun `lista solo las denominaciones contadas, con subtotal y total`() {
        val text = buildCountShareText(
            DENOMINACIONES,
            mapOf("b500" to 4, "b100" to 0, "c100" to 3),
            CUP,
        )
        assertEquals(
            listOf(
                "Conteo de efectivo · CUP",
                "500 CUP (Billete) × 4 = 2 000 CUP",
                "1 CUP (Moneda) × 3 = 3 CUP",
                "Total: 2 003 CUP · 7 piezas",
            ).joinToString("\n"),
            text,
        )
    }

    @Test
    fun `con una sola pieza usa el singular`() {
        val text = buildCountShareText(DENOMINACIONES, mapOf("b100" to 1), CUP)
        assertTrue(text.contains("Total: 100 CUP · 1 pieza"))
    }

    @Test
    fun `sin piezas contadas deja solo la cabecera y el total en cero`() {
        val text = buildCountShareText(DENOMINACIONES, emptyMap(), CUP)
        assertEquals(
            listOf(
                "Conteo de efectivo · CUP",
                "Total: 0 CUP · 0 piezas",
            ).joinToString("\n"),
            text,
        )
    }

    @Test
    fun `un tipo desconocido se muestra tal cual`() {
        val text = buildCountShareText(
            listOf(ShareableDenomination("x", 100L, "TOKEN")),
            mapOf("x" to 2),
            CUP,
        )
        assertTrue(text.contains("1 CUP (TOKEN) × 2 = 2 CUP"))
    }
}
