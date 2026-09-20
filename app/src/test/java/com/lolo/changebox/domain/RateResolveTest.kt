package com.lolo.changebox.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Vectores portados de rate-resolve.test.ts.
// Pares registrados: EUR→CUP 470 y USD→CUP 435.5 (CUP es la base).

class RateResolveTest {
    private val map = buildPairMap(
        listOf(
            PairRateLite("eur", "cup", 4_700_000L),
            PairRateLite("usd", "cup", 4_355_000L),
        ),
    )

    @Test
    fun `misma moneda es identidad`() {
        assertEquals(RATE_SCALE, resolveRateScaled(map, "cup", "cup", "cup"))
    }

    @Test
    fun `usa el par directo`() {
        assertEquals(4_700_000L, resolveRateScaled(map, "eur", "cup", "cup"))
    }

    @Test
    fun `resuelve el par inverso automaticamente`() {
        assertEquals(21L, resolveRateScaled(map, "cup", "eur", "cup"))
    }

    @Test
    fun `compone via la moneda base cuando no hay par directo ni inverso`() {
        assertEquals(9146L, resolveRateScaled(map, "usd", "eur", "cup"))
    }

    @Test
    fun `devuelve null si no hay camino registrado`() {
        assertNull(resolveRateScaled(map, "usd", "mlc", "cup"))
        assertNull(resolveRateScaled(map, "usd", "eur", null))
    }
}

