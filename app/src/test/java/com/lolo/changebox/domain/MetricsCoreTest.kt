package com.lolo.changebox.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Vectores portados de metrics-core.test.ts.

private val BASE = BaseCurrencyInfo("cup", 0)
private val CUP = MetricCurrency("cup", "CUP", 0)
private val USD = MetricCurrency("usd", "USD", 2)

private fun tx(
    kind: String,
    amountMinor: Long,
    occurredAt: LocalDate,
    currency: MetricCurrency = CUP,
) = RawMetricTx(kind, amountMinor, occurredAt, currency)

class MonthKeyLastMonthsTest {
    @Test
    fun `genera claves ano-mes locales`() {
        assertEquals("2026-07", monthKey(LocalDate.of(2026, 7, 15)))
        assertEquals("2026-01", monthKey(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `devuelve los ultimos meses en orden cronologico cruzando el ano`() {
        val months = lastMonths(3, LocalDate.of(2026, 1, 15))
        assertEquals(listOf("2025-11", "2025-12", "2026-01"), months.map { it.key })
    }
}

class BuildMonthlySeriesTest {
    private val months = lastMonths(2, LocalDate.of(2026, 7, 7))
    private val rates = mapOf("usd" to 4_000_000L) // 1 USD = 400 CUP

    @Test
    fun `agrupa ingresos y gastos por mes`() {
        val (series, _) = buildMonthlySeries(
            listOf(
                tx("INCOME", 1000L, LocalDate.of(2026, 7, 1)),
                tx("EXPENSE", 300L, LocalDate.of(2026, 7, 2)),
                tx("EXPENSE", 200L, LocalDate.of(2026, 6, 20)),
            ),
            months,
            BASE,
            rates,
        )
        assertEquals(1000L, series[1].incomeMinor)
        assertEquals(300L, series[1].expenseMinor)
        assertEquals(0L, series[0].incomeMinor)
        assertEquals(200L, series[0].expenseMinor)
    }

    @Test
    fun `convierte a la base con la tasa vigente`() {
        val (series, _) = buildMonthlySeries(
            listOf(tx("INCOME", 500L, LocalDate.of(2026, 7, 3), USD)),
            months,
            BASE,
            rates,
        )
        assertEquals(2000L, series[1].incomeMinor)
    }

    @Test
    fun `excluye monedas sin tasa y las reporta`() {
        val (series, missingRates) = buildMonthlySeries(
            listOf(tx("EXPENSE", 500L, LocalDate.of(2026, 7, 3), USD)),
            months,
            BASE,
            emptyMap(),
        )
        assertEquals(0L, series[1].expenseMinor)
        assertEquals(setOf("USD"), missingRates)
    }

    @Test
    fun `ignora transferencias, ajustes y meses fuera de rango`() {
        val (series, _) = buildMonthlySeries(
            listOf(
                tx("TRANSFER", 900L, LocalDate.of(2026, 7, 1)),
                tx("ADJUSTMENT", 900L, LocalDate.of(2026, 7, 1)),
                tx("INCOME", 900L, LocalDate.of(2025, 7, 1)),
            ),
            months,
            BASE,
            rates,
        )
        assertTrue(series.all { it.incomeMinor == 0L && it.expenseMinor == 0L })
    }
}

class DeltaPctTest {
    @Test
    fun `calcula la variacion porcentual`() {
        assertEquals(20, deltaPct(120L, 100L))
        assertEquals(-20, deltaPct(80L, 100L))
    }

    @Test
    fun `devuelve null sin referencia previa`() {
        assertNull(deltaPct(50L, 0L))
    }
}

