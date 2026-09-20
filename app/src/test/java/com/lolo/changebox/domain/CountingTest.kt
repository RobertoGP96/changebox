package com.lolo.changebox.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

// Vectores portados de counting.test.ts.

class ClampQtyTest {
    @Test
    fun `descarta negativos y aplica el tope`() {
        assertEquals(0, clampQty(-5))
        assertEquals(0, clampQty(0))
        assertEquals(3, clampQty(3))
        assertEquals(MAX_QTY, clampQty(MAX_QTY + 1))
    }
}

class CountedTotalMinorTest {
    private val denominations = listOf(
        CountableDenomination("b500", 50000L),
        CountableDenomination("b100", 10000L),
        CountableDenomination("c25", 25L),
    )

    @Test
    fun `suma valor por cantidad por denominacion`() {
        val total = countedTotalMinor(
            denominations,
            mapOf("b500" to 3, "b100" to 2, "c25" to 4),
        )
        assertEquals(3 * 50000L + 2 * 10000L + 4 * 25L, total)
    }

    @Test
    fun `trata denominaciones sin cantidad como 0`() {
        assertEquals(10000L, countedTotalMinor(denominations, mapOf("b100" to 1)))
        assertEquals(0L, countedTotalMinor(denominations, emptyMap()))
    }

    @Test
    fun `ignora cantidades de denominaciones desconocidas`() {
        assertEquals(0L, countedTotalMinor(denominations, mapOf("otra" to 99)))
    }
}

class CountedPiecesTest {
    @Test
    fun `suma todas las piezas contadas`() {
        assertEquals(10, countedPieces(mapOf("a" to 3, "b" to 0, "c" to 7)))
        assertEquals(0, countedPieces(emptyMap()))
    }
}

class MovementLineSignTest {
    @Test
    fun `entra con INCOME y destino de TRANSFER`() {
        assertEquals(1, movementLineSign("INCOME", true))
        assertEquals(1, movementLineSign("TRANSFER", false))
    }

    @Test
    fun `sale con EXPENSE y origen de TRANSFER`() {
        assertEquals(-1, movementLineSign("EXPENSE", true))
        assertEquals(-1, movementLineSign("TRANSFER", true))
    }

    @Test
    fun `ADJUSTMENT y kinds desconocidos no llevan desglose`() {
        assertEquals(0, movementLineSign("ADJUSTMENT", true))
        assertEquals(0, movementLineSign("OTRO", false))
    }
}

class SuggestDistributionTest {
    private fun d(id: String, valueMinor: Long, available: Int? = null) =
        SuggestibleDenomination(id, valueMinor, available)

    @Test
    fun `reparte mayor-primero en sistemas canonicos`() {
        val result = suggestDistribution(
            listOf(d("b1000", 1000L), d("b500", 500L), d("b200", 200L), d("b100", 100L)),
            1700L,
        )
        assertEquals(mapOf("b1000" to 1, "b500" to 1, "b200" to 1), result)
    }

    @Test
    fun `retrocede cuando el voraz puro falla (billete de 3)`() {
        val result = suggestDistribution(listOf(d("b5", 5L), d("b3", 3L)), 6L)
        assertEquals(mapOf("b3" to 2), result)
    }

    @Test
    fun `respeta el stock disponible`() {
        val result = suggestDistribution(
            listOf(d("b500", 500L, 1), d("b200", 200L, 4)),
            900L,
        )
        assertEquals(mapOf("b500" to 1, "b200" to 2), result)
    }

    @Test
    fun `devuelve null si el stock no alcanza o no hay combinacion exacta`() {
        assertNull(suggestDistribution(listOf(d("b500", 500L, 1), d("b200", 200L, 2)), 1000L))
        assertNull(suggestDistribution(listOf(d("b200", 200L)), 300L))
    }

    @Test
    fun `monto 0 da desglose vacio y negativo da null`() {
        assertEquals(emptyMap<String, Int>(), suggestDistribution(listOf(d("b100", 100L)), 0L))
        assertNull(suggestDistribution(listOf(d("b100", 100L)), -5L))
    }

    @Test
    fun `resuelve montos grandes sin agotar el presupuesto de pasos`() {
        val result = suggestDistribution(
            listOf(d("b1000", 1000L), d("b500", 500L), d("b3", 3L), d("b1", 1L)),
            1_234_567L,
        )
        assertNotNull(result)
        val total = (result!!["b1000"] ?: 0) * 1000L +
            (result["b500"] ?: 0) * 500L +
            (result["b3"] ?: 0) * 3L +
            (result["b1"] ?: 0) * 1L
        assertEquals(1_234_567L, total)
    }
}

