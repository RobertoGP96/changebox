package com.lolo.changebox.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

// Vectores portados de balances-core.test.ts.

class SignedKindMinorTest {
    @Test
    fun `suma INCOME y resta EXPENSE y TRANSFER`() {
        assertEquals(5000L, signedKindMinor("INCOME", 5000L))
        assertEquals(-3000L, signedKindMinor("EXPENSE", 3000L))
        assertEquals(-1200L, signedKindMinor("TRANSFER", 1200L))
    }

    @Test
    fun `ADJUSTMENT conserva su signo`() {
        assertEquals(700L, signedKindMinor("ADJUSTMENT", 700L))
        assertEquals(-700L, signedKindMinor("ADJUSTMENT", -700L))
    }

    @Test
    fun `lanza con un kind desconocido`() {
        assertThrows(IllegalArgumentException::class.java) {
            signedKindMinor("LOAN", 100L)
        }
    }
}

class BalancesFromGroupsTest {
    @Test
    fun `combina sumas propias por kind y transferencias entrantes`() {
        val balances = balancesFromGroups(
            listOf(
                OwnKindGroup("a1", "INCOME", 10000L),
                OwnKindGroup("a1", "EXPENSE", 4000L),
                OwnKindGroup("a1", "TRANSFER", 1000L),
                OwnKindGroup("a2", "ADJUSTMENT", -500L),
            ),
            listOf(IncomingTransferGroup("a2", 2500L)),
        )
        assertEquals(5000L, balances["a1"])
        assertEquals(2000L, balances["a2"])
    }

    @Test
    fun `incluye cuentas que solo reciben transferencias`() {
        val balances = balancesFromGroups(
            emptyList(),
            listOf(IncomingTransferGroup("destino", 800L)),
        )
        assertEquals(800L, balances["destino"])
    }

    @Test
    fun `devuelve mapa vacio sin datos`() {
        assertEquals(0, balancesFromGroups(emptyList(), emptyList()).size)
    }

    @Test
    fun `propaga el error de un kind desconocido`() {
        assertThrows(IllegalArgumentException::class.java) {
            balancesFromGroups(listOf(OwnKindGroup("a1", "WAT", 1L)), emptyList())
        }
    }
}

