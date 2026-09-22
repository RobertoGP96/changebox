package com.lolo.changebox.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Vectores portados de income-series.test.ts.

// Domingo 9 de agosto de 2026.
private val NOW: LocalDate = LocalDate.of(2026, 8, 9)

private val CUP = MetricCurrency("cup", "CUP", 2)
private val USD = MetricCurrency("usd", "USD", 2)
private val MLC = MetricCurrency("mlc", "MLC", 2)
private val DISPLAY = BaseCurrencyInfo("cup", 2)

private fun tx(
    kind: String,
    amountMinor: Long,
    occurredAt: LocalDate,
    currency: MetricCurrency = CUP,
) = RawMetricTx(kind, amountMinor, occurredAt, currency)

class DayKeyWeekStartTest {
    @Test
    fun `clave local anio-mes-dia`() {
        assertEquals("2026-01-05", dayKey(LocalDate.of(2026, 1, 5)))
    }

    @Test
    fun `weekStart devuelve el lunes de la semana`() {
        // Domingo 9 ago → lunes 3 ago; lunes 3 ago → él mismo.
        assertEquals("2026-08-03", dayKey(weekStart(NOW)))
        assertEquals("2026-08-03", dayKey(weekStart(LocalDate.of(2026, 8, 3))))
    }
}

class LastDaysLastWeeksTest {
    @Test
    fun `del mas antiguo al mas reciente incluyendo hoy`() {
        val days = lastDays(3, NOW)
        assertEquals(
            listOf("2026-08-07", "2026-08-08", "2026-08-09"),
            days.map { it.key },
        )
    }

    @Test
    fun `semanas etiquetadas por su lunes`() {
        val weeks = lastWeeks(2, NOW)
        assertEquals(listOf("2026-07-27", "2026-08-03"), weeks.map { it.key })
    }

    @Test
    fun `el rango de consulta cubre los 12 meses`() {
        assertEquals(LocalDate.of(2025, 9, 1), incomeCardRangeStart(NOW))
    }
}

class BuildIncomeCardSeriesTest {
    // 1 USD = 2 CUP (tasa escalada ×RATE_SCALE).
    private val rates = mapOf("usd" to 20_000L)

    private val rows = listOf(
        tx("INCOME", 100L, LocalDate.of(2026, 8, 9)),
        tx("EXPENSE", 40L, LocalDate.of(2026, 8, 9)),
        tx("INCOME", 50L, LocalDate.of(2026, 8, 4)),
        tx("INCOME", 30L, LocalDate.of(2026, 7, 15)),
        // USD con tasa 2 → se convierte a 200.
        tx("INCOME", 100L, LocalDate.of(2026, 8, 9), USD),
        // Sin tasa → excluida y reportada.
        tx("INCOME", 999L, LocalDate.of(2026, 8, 9), MLC),
        // Otros tipos no cuentan.
        tx("TRANSFER", 777L, LocalDate.of(2026, 8, 9)),
    )

    @Test
    fun `agrega por dia, semana y mes con conversion a la base`() {
        val (series, missingRates) = buildIncomeCardSeries(rows, DISPLAY, rates, NOW)

        assertEquals(INCOME_CARD_DAYS, series.day.size)
        assertEquals(INCOME_CARD_WEEKS, series.week.size)
        assertEquals(INCOME_CARD_MONTHS, series.month.size)

        val today = series.day.last()
        assertEquals(300L, today.incomeMinor)
        assertEquals(40L, today.expenseMinor)
        // 9 ago − 5 días = 4 ago.
        assertEquals(50L, series.day[series.day.size - 6].incomeMinor)

        val thisWeek = series.week.last()
        assertEquals(350L, thisWeek.incomeMinor)
        assertEquals(40L, thisWeek.expenseMinor)
        // 15 jul cae en la semana del lunes 13 jul (3 semanas antes).
        assertEquals(30L, series.week[series.week.size - 4].incomeMinor)

        val thisMonth = series.month.last()
        assertEquals(350L, thisMonth.incomeMinor)
        assertEquals(40L, thisMonth.expenseMinor)
        assertEquals(30L, series.month[series.month.size - 2].incomeMinor)

        assertEquals(setOf("MLC"), missingRates)
    }

    @Test
    fun `los movimientos fuera de ventana no rompen nada`() {
        val (series, _) = buildIncomeCardSeries(
            listOf(tx("INCOME", 500L, LocalDate.of(2020, 1, 1))),
            DISPLAY,
            emptyMap(),
            NOW,
        )
        assertTrue(series.month.all { it.incomeMinor == 0L })
        assertTrue(series.day.all { it.incomeMinor == 0L && it.expenseMinor == 0L })
    }

    @Test
    fun `las claves de la ventana van del mas antiguo al mas reciente`() {
        val (series, _) = buildIncomeCardSeries(emptyList(), DISPLAY, rates, NOW)
        assertEquals("2026-08-09", series.day.last().key)
        assertEquals("2026-08-03", series.week.last().key)
        assertEquals("2026-08", series.month.last().key)
        assertEquals("2025-09", series.month.first().key)
    }
}
