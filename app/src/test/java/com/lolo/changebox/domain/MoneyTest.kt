package com.lolo.changebox.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

// Vectores portados 1:1 de fantastic-eureka/src/lib/money.test.ts — son el
// contrato de paridad entre el dominio Kotlin y el de la web.

private val CUP = MinorCurrencyOf(0)
private val USD = MinorCurrencyOf(2)

class ParseAmountToMinorTest {
    @Test
    fun `convierte enteros con 2 decimales`() {
        assertEquals(123456L, parseAmountToMinor("1234.56", USD))
    }

    @Test
    fun `convierte enteros con 0 decimales`() {
        assertEquals(4750L, parseAmountToMinor("4750", CUP))
    }

    @Test
    fun `acepta coma decimal y espacios de miles`() {
        assertEquals(123456L, parseAmountToMinor("1 234,56", USD))
    }

    @Test
    fun `rellena decimales faltantes`() {
        assertEquals(1250L, parseAmountToMinor("12.5", USD))
        assertEquals(1200L, parseAmountToMinor("12", USD))
    }

    @Test
    fun `redondea half-up los decimales sobrantes`() {
        assertEquals(1235L, parseAmountToMinor("12.345", USD))
        assertEquals(1234L, parseAmountToMinor("12.344", USD))
        assertEquals(100L, parseAmountToMinor("99.9", CUP))
        assertEquals(99L, parseAmountToMinor("99.4", CUP))
    }

    @Test
    fun `acepta negativos`() {
        assertEquals(-1250L, parseAmountToMinor("-12.50", USD))
    }

    @Test
    fun `rechaza formatos invalidos`() {
        assertThrows(MoneyException::class.java) { parseAmountToMinor("", USD) }
        assertThrows(MoneyException::class.java) { parseAmountToMinor("abc", USD) }
        assertThrows(MoneyException::class.java) { parseAmountToMinor("12.34.56", USD) }
        assertThrows(MoneyException::class.java) { parseAmountToMinor("12,34,56", USD) }
    }
}

class MinorToAmountInputTest {
    @Test
    fun `convierte menores a texto con 2 decimales`() {
        assertEquals("1234.56", minorToAmountInput(123456L, USD))
    }

    @Test
    fun `omite los ceros decimales sobrantes`() {
        assertEquals("12.5", minorToAmountInput(1250L, USD))
        assertEquals("12", minorToAmountInput(1200L, USD))
    }

    @Test
    fun `convierte con 0 decimales`() {
        assertEquals("4750", minorToAmountInput(4750L, CUP))
    }

    @Test
    fun `rellena fracciones pequenas con ceros a la izquierda`() {
        assertEquals("0.05", minorToAmountInput(5L, USD))
    }

    @Test
    fun `acepta negativos`() {
        assertEquals("-12.5", minorToAmountInput(-1250L, USD))
    }

    @Test
    fun `es inverso de parseAmountToMinor`() {
        assertEquals(123456L, parseAmountToMinor(minorToAmountInput(123456L, USD), USD))
    }
}

class ConvertMinorTest {
    @Test
    fun `convierte USD a CUP con tasa 435_5`() {
        assertEquals(43550L, convertMinor(10000L, USD, CUP, 4_355_000L))
    }

    @Test
    fun `convierte CUP a USD con tasa diminuta`() {
        assertEquals(10017L, convertMinor(43550L, CUP, USD, 23L))
    }

    @Test
    fun `convierte entre monedas de igual decimales`() {
        assertEquals(9200L, convertMinor(10000L, USD, MinorCurrencyOf(2), 9200L))
    }

    @Test
    fun `redondea half-up`() {
        assertEquals(0L, convertMinor(1L, CUP, USD, 23L))
        assertEquals(1L, convertMinor(3L, CUP, USD, 23L))
    }

    @Test
    fun `maneja montos negativos (ajustes)`() {
        assertEquals(-43550L, convertMinor(-10000L, USD, CUP, 4_355_000L))
    }

    @Test
    fun `rechaza tasas invalidas`() {
        assertThrows(MoneyException::class.java) { convertMinor(100L, USD, CUP, 0L) }
        assertThrows(MoneyException::class.java) { convertMinor(100L, USD, CUP, -5L) }
    }
}

class ConvertMinorInverseTest {
    @Test
    fun `convierte CUP a USD con la tasa citada como CUP por USD`() {
        assertEquals(2500L, convertMinorInverse(10000L, CUP, USD, 4_000_000L))
    }

    @Test
    fun `no pierde precision frente a invertir la tasa`() {
        assertEquals(2597L, convertMinorInverse(10000L, CUP, USD, 3_850_000L))
        assertEquals(2600L, convertMinor(10000L, CUP, USD, invertRateScaled(3_850_000L)))
    }

    @Test
    fun `es inversa de convertMinor con la misma tasa`() {
        assertEquals(43550L, convertMinor(10000L, USD, CUP, 4_355_000L))
        assertEquals(10000L, convertMinorInverse(43550L, CUP, USD, 4_355_000L))
    }

    @Test
    fun `redondea half-up`() {
        assertEquals(0L, convertMinorInverse(1L, CUP, USD, 4_000_000L))
        assertEquals(1L, convertMinorInverse(2L, CUP, USD, 4_000_000L))
    }

    @Test
    fun `rechaza tasas invalidas`() {
        assertThrows(MoneyException::class.java) { convertMinorInverse(100L, CUP, USD, 0L) }
        assertThrows(MoneyException::class.java) { convertMinorInverse(100L, CUP, USD, -5L) }
    }
}

class ImpliedRateScaledTest {
    @Test
    fun `deriva la tasa contraria por 1 unidad principal`() {
        assertEquals(4_000_000L, impliedRateScaled(2500L, USD, 10000L, CUP))
    }

    @Test
    fun `funciona con la direccion debil a fuerte`() {
        assertEquals(25L, impliedRateScaled(10000L, CUP, 2500L, USD))
    }

    @Test
    fun `devuelve null si la tasa no cabe o queda en 0`() {
        assertNull(impliedRateScaled(100_000_000L, CUP, 1L, USD))
        assertNull(impliedRateScaled(1L, USD, 2_000_000_000L, CUP))
    }

    @Test
    fun `devuelve null con montos no positivos`() {
        assertNull(impliedRateScaled(0L, USD, 100L, CUP))
        assertNull(impliedRateScaled(100L, USD, -5L, CUP))
    }
}

class CrossRateScaledTest {
    @Test
    fun `deriva la tasa entre dos monedas via la base`() {
        assertEquals(9266L, crossRateScaled(4_355_000L, 4_700_000L))
    }

    @Test
    fun `es identidad contra si misma`() {
        assertEquals(RATE_SCALE, crossRateScaled(4_355_000L, 4_355_000L))
    }

    @Test
    fun `rechaza tasas no positivas`() {
        assertThrows(MoneyException::class.java) { crossRateScaled(0L, 100L) }
        assertThrows(MoneyException::class.java) { crossRateScaled(100L, 0L) }
    }
}

class InvertRateScaledTest {
    @Test
    fun `invierte tasas en ambos sentidos`() {
        assertEquals(23L, invertRateScaled(4_355_000L))
        assertEquals(4_347_826L, invertRateScaled(23L))
    }

    @Test
    fun `la identidad se invierte a si misma`() {
        assertEquals(RATE_SCALE, invertRateScaled(RATE_SCALE))
    }

    @Test
    fun `rechaza tasas no positivas`() {
        assertThrows(MoneyException::class.java) { invertRateScaled(0L) }
        assertThrows(MoneyException::class.java) { invertRateScaled(-3L) }
    }
}

class ComposeRatesScaledTest {
    @Test
    fun `compone EUR-USD con USD-CUP en EUR-CUP`() {
        assertEquals(4_703_400L, composeRatesScaled(10_800L, 4_355_000L))
    }

    @Test
    fun `componer con la identidad no cambia la tasa`() {
        assertEquals(4_355_000L, composeRatesScaled(4_355_000L, RATE_SCALE))
        assertEquals(4_355_000L, composeRatesScaled(RATE_SCALE, 4_355_000L))
    }

    @Test
    fun `rechaza tasas no positivas`() {
        assertThrows(MoneyException::class.java) { composeRatesScaled(0L, 100L) }
        assertThrows(MoneyException::class.java) { composeRatesScaled(100L, -1L) }
    }
}

class SumMinorPow10Test {
    @Test
    fun `suma listas de montos`() {
        assertEquals(300L, sumMinor(listOf(100L, 250L, -50L)))
        assertEquals(0L, sumMinor(emptyList()))
    }

    @Test
    fun `pow10 cubre el rango soportado`() {
        assertEquals(1L, pow10(0))
        assertEquals(100L, pow10(2))
        assertThrows(MoneyException::class.java) { pow10(9) }
    }
}

