package com.lolo.changebox.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// Lógica pura del dashboard (agrupación mensual y conversión a moneda base),
// portada 1:1 de metrics-core.ts sobre java.time.

data class MetricCurrency(
    val id: String,
    val code: String,
    override val decimalPlaces: Int,
) : MinorCurrency

data class RawMetricTx(
    val kind: String,
    val amountMinor: Long,
    val occurredAt: LocalDate,
    val currency: MetricCurrency,
)

data class MonthBucket(
    val key: String,
    val label: String,
    val incomeMinor: Long,
    val expenseMinor: Long,
)

data class BaseCurrencyInfo(
    val id: String,
    override val decimalPlaces: Int,
) : MinorCurrency

data class MonthRef(val key: String, val label: String)

data class MonthlySeries(
    val series: List<MonthBucket>,
    val missingRates: Set<String>,
)

private val MONTH_LOCALE = Locale.forLanguageTag("es")

fun monthKey(date: LocalDate): String =
    "%04d-%02d".format(date.year, date.monthValue)

fun monthKey(month: YearMonth): String =
    "%04d-%02d".format(month.year, month.monthValue)

/** Últimos `n` meses (incluyendo el actual), del más antiguo al más reciente. */
fun lastMonths(n: Int, now: LocalDate = LocalDate.now()): List<MonthRef> {
    val current = YearMonth.from(now)
    return (n - 1 downTo 0).map { i ->
        val month = current.minusMonths(i.toLong())
        MonthRef(
            key = monthKey(month),
            label = month.month.getDisplayName(TextStyle.SHORT, MONTH_LOCALE),
        )
    }
}

/**
 * Serie mensual de ingresos/gastos convertidos a la moneda base con la tasa
 * vigente. Los montos en monedas sin tasa se excluyen y sus códigos se
 * devuelven en missingRates para avisar en el UI.
 */
fun buildMonthlySeries(
    rows: List<RawMetricTx>,
    months: List<MonthRef>,
    base: BaseCurrencyInfo,
    rates: Map<String, Long>,
): MonthlySeries {
    data class Acc(var incomeMinor: Long = 0, var expenseMinor: Long = 0)

    val buckets = months.associateTo(LinkedHashMap()) { it.key to Acc() }
    val missingRates = mutableSetOf<String>()

    for (row in rows) {
        if (row.kind != "INCOME" && row.kind != "EXPENSE") continue
        val bucket = buckets[monthKey(row.occurredAt)] ?: continue

        var converted = row.amountMinor
        if (row.currency.id != base.id) {
            val rate = rates[row.currency.id]
            if (rate == null) {
                missingRates.add(row.currency.code)
                continue
            }
            converted = convertMinor(row.amountMinor, row.currency, base, rate)
        }

        if (row.kind == "INCOME") bucket.incomeMinor += converted
        else bucket.expenseMinor += converted
    }

    return MonthlySeries(
        series = months.map { m ->
            val acc = buckets.getValue(m.key)
            MonthBucket(m.key, m.label, acc.incomeMinor, acc.expenseMinor)
        },
        missingRates = missingRates,
    )
}

/** Variación porcentual respecto al valor anterior; null si no hay referencia. */
fun deltaPct(current: Long, previous: Long): Int? {
    if (previous <= 0) return null
    return Math.round(((current - previous).toDouble() / previous) * 100).toInt()
}

