package com.lolo.changebox.domain

import org.junit.Assert.assertEquals
import org.junit.Test

// Vectores portados de format.test.ts.

private val CUP = DisplayCurrencyOf("CUP", 0)
private val USD = DisplayCurrencyOf("USD", 2)

class FmtMinorTest {
    @Test
    fun `separa miles con espacio y pone el codigo al final`() {
        assertEquals("4 750 CUP", fmtMinor(4750L, CUP))
        assertEquals("1 234 567 CUP", fmtMinor(1234567L, CUP))
    }

    @Test
    fun `omite decimales cuando son todo ceros`() {
        assertEquals("250 USD", fmtMinor(25000L, USD))
    }

    @Test
    fun `muestra decimales cuando existen`() {
        assertEquals("250.50 USD", fmtMinor(25050L, USD))
        assertEquals("0.01 USD", fmtMinor(1L, USD))
    }

    @Test
    fun `maneja negativos`() {
        assertEquals("-4 750 CUP", fmtMinor(-4750L, CUP))
        assertEquals("-12.50 USD", fmtMinor(-1250L, USD))
    }

    @Test
    fun `maneja cero`() {
        assertEquals("0 CUP", fmtMinor(0L, CUP))
        assertEquals("0 USD", fmtMinor(0L, USD))
    }
}

class FmtSignedMinorTest {
    @Test
    fun `antepone mas a los positivos`() {
        assertEquals("+4 750 CUP", fmtSignedMinor(4750L, CUP))
        assertEquals("-4 750 CUP", fmtSignedMinor(-4750L, CUP))
        assertEquals("0 CUP", fmtSignedMinor(0L, CUP))
    }
}

class FmtRateTest {
    @Test
    fun `recorta ceros finales`() {
        assertEquals("435.5", fmtRate(4_355_000L))
        assertEquals("435", fmtRate(4_350_000L))
    }

    @Test
    fun `conserva decimales significativos`() {
        assertEquals("1.0842", fmtRate(10842L))
        assertEquals("0.0023", fmtRate(23L))
    }

    @Test
    fun `separa miles en la parte entera`() {
        assertEquals("1 234 567.89", fmtRate(12_345_678_900L))
    }
}

class NormalizeTextTest {
    @Test
    fun `quita acentos y pasa a minusculas`() {
        assertEquals("perez", normalizeText("Pérez"))
        assertEquals("alimentacion", normalizeText("Alimentación"))
    }
}

class MinorToInputTest {
    @Test
    fun `formatea para inputs sin separador de miles`() {
        assertEquals("12.50", minorToInput(1250L, 2))
        assertEquals("4750", minorToInput(4750L, 0))
        assertEquals("-3.25", minorToInput(-325L, 2))
    }
}

