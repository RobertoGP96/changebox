package com.lolo.changebox.domain

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// Series de ingresos/gastos por día, semana y mes para el gadget «Resumen de
// ingresos», portadas 1:1 de income-series.ts sobre java.time. Lógica pura:
// la consulta vive en los repos (igual que en la web vive en metrics.ts).

const val INCOME_CARD_DAYS = 14
const val INCOME_CARD_WEEKS = 12
const val INCOME_CARD_MONTHS = 12

/** Referencia de un bucket de la ventana (clave + etiqueta ya formateada). */
data class PeriodRef(val key: String, val label: String)

data class PeriodBucket(
    val key: String,
    val label: String,
    val incomeMinor: Long,
    val expenseMinor: Long,
)

data class IncomeCardSeries(
    val day: List<PeriodBucket>,
    val week: List<PeriodBucket>,
    val month: List<PeriodBucket>,
)

data class IncomeCardSeriesResult(
    val series: IncomeCardSeries,
    val missingRates: Set<String>,
)

private val DAY_LABEL_LOCALE: Locale = Locale.forLanguageTag("es")

/**
 * Etiqueta corta del día ("9 ago"), equivalente al Intl.DateTimeFormat("es",
 * { day: "numeric", month: "short" }) de la web. Se arma a mano (día + mes
 * corto) para no depender del patrón del locale del dispositivo.
 */
private fun dayLabel(date: LocalDate): String =
    "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, DAY_LABEL_LOCALE)}"

/** Clave local yyyy-mm-dd (sin UTC: los buckets siguen al reloj local). */
fun dayKey(date: LocalDate): String =
    "%04d-%02d-%02d".format(date.year, date.monthValue, date.dayOfMonth)

/** Lunes de la semana de `date` (la web resta (getDay() + 6) % 7 días). */
fun weekStart(date: LocalDate): LocalDate =
    date.minusDays((date.dayOfWeek.value - 1).toLong())

/** Últimos `n` días (incluido hoy), del más antiguo al más reciente. */
fun lastDays(n: Int, now: LocalDate = LocalDate.now()): List<PeriodRef> =
    (n - 1 downTo 0).map { i ->
        val date = now.minusDays(i.toLong())
        PeriodRef(dayKey(date), dayLabel(date))
    }

/** Últimas `n` semanas (incluida la actual), etiquetadas por su lunes. */
fun lastWeeks(n: Int, now: LocalDate = LocalDate.now()): List<PeriodRef> {
    val monday = weekStart(now)
    return (n - 1 downTo 0).map { i ->
        val date = monday.minusDays(i.toLong() * 7L)
        PeriodRef(dayKey(date), dayLabel(date))
    }
}

/** Inicio del rango de consulta que cubre las tres series (12 meses). */
fun incomeCardRangeStart(now: LocalDate = LocalDate.now()): LocalDate {
    val parts = lastMonths(INCOME_CARD_MONTHS, now).first().key.split("-").map { it.toInt() }
    return LocalDate.of(parts[0], parts[1], 1)
}

/**
 * Agrega los movimientos en las tres series (día/semana/mes) convertidos a la
 * moneda de despliegue con la tasa vigente. Los montos en monedas sin tasa se
 * excluyen y sus códigos se devuelven en missingRates para avisar en el UI.
 */
fun buildIncomeCardSeries(
    rows: List<RawMetricTx>,
    display: BaseCurrencyInfo,
    rates: Map<String, Long>,
    now: LocalDate = LocalDate.now(),
): IncomeCardSeriesResult {
    data class Acc(var incomeMinor: Long = 0L, var expenseMinor: Long = 0L)

    val dayWindow = lastDays(INCOME_CARD_DAYS, now)
    val weekWindow = lastWeeks(INCOME_CARD_WEEKS, now)
    val monthWindow = lastMonths(INCOME_CARD_MONTHS, now).map { PeriodRef(it.key, it.label) }

    val dayBuckets = dayWindow.associateTo(LinkedHashMap()) { it.key to Acc() }
    val weekBuckets = weekWindow.associateTo(LinkedHashMap()) { it.key to Acc() }
    val monthBuckets = monthWindow.associateTo(LinkedHashMap()) { it.key to Acc() }

    val missingRates = mutableSetOf<String>()

    for (row in rows) {
        if (row.kind != "INCOME" && row.kind != "EXPENSE") continue

        var converted = row.amountMinor
        if (row.currency.id != display.id) {
            val rate = rates[row.currency.id]
            if (rate == null) {
                missingRates.add(row.currency.code)
                continue
            }
            converted = convertMinor(row.amountMinor, row.currency, display, rate)
        }

        // Un mismo movimiento cae a la vez en su día, su semana y su mes; los
        // buckets fuera de ventana simplemente no existen y se ignoran.
        val objetivos = listOfNotNull(
            dayBuckets[dayKey(row.occurredAt)],
            weekBuckets[dayKey(weekStart(row.occurredAt))],
            monthBuckets[monthKey(row.occurredAt)],
        )
        for (bucket in objetivos) {
            if (row.kind == "INCOME") bucket.incomeMinor += converted
            else bucket.expenseMinor += converted
        }
    }

    fun serie(window: List<PeriodRef>, buckets: Map<String, Acc>): List<PeriodBucket> =
        window.map { ref ->
            val acc = buckets.getValue(ref.key)
            PeriodBucket(ref.key, ref.label, acc.incomeMinor, acc.expenseMinor)
        }

    return IncomeCardSeriesResult(
        series = IncomeCardSeries(
            day = serie(dayWindow, dayBuckets),
            week = serie(weekWindow, weekBuckets),
            month = serie(monthWindow, monthBuckets),
        ),
        missingRates = missingRates,
    )
}
