package com.lolo.changebox.data.repo

import com.lolo.changebox.data.atStartOfDayMillis
import com.lolo.changebox.data.local.ChangeboxDatabase
import com.lolo.changebox.data.toLocalDate
import com.lolo.changebox.domain.BaseCurrencyInfo
import com.lolo.changebox.domain.MetricCurrency
import com.lolo.changebox.domain.MonthBucket
import com.lolo.changebox.domain.RawMetricTx
import com.lolo.changebox.domain.buildMonthlySeries
import com.lolo.changebox.domain.convertMinor
import com.lolo.changebox.domain.lastMonths
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

// Métricas del dashboard, port 1:1 de lib/metrics.ts sobre la lógica pura de
// domain/MetricsCore.kt: serie mensual, top de categorías del mes en curso y
// totales de deudas abiertas, todo convertido a la moneda base.

data class CategoryTotal(val name: String, val totalMinor: Long)

data class DashboardMetrics(
    val series: List<MonthBucket>,
    val topCategories: List<CategoryTotal>,
    val receivableMinor: Long,
    val payableMinor: Long,
    val missingRates: Set<String>,
)

class MetricsRepository(
    private val db: ChangeboxDatabase,
    private val rates: RateRepository,
) {

    /** Métricas de los últimos [months] meses para la moneda base dada. */
    fun dashboardMetricsFlow(
        baseId: String,
        baseDecimals: Int,
        months: Int = 6,
    ): Flow<DashboardMetrics> {
        val monthList = lastMonths(months)
        val (year, month) = monthList.first().key.split("-").map { it.toInt() }
        val rangeStart = LocalDate.of(year, month, 1).atStartOfDayMillis()
        val now = LocalDate.now()
        val currentMonthStart = LocalDate.of(now.year, now.monthValue, 1).atStartOfDayMillis()
        val base = BaseCurrencyInfo(baseId, baseDecimals)

        return combine(
            rates.latestRatesByCurrencyFlow(),
            db.transactionDao().metricRowsFlow(rangeStart),
            db.debtDao().openDebtsFlow(),
        ) { rateMap, txRows, openDebts ->
            val rateScaledMap = rateMap.mapValues { it.value.rateScaled }

            val monthly = buildMonthlySeries(
                txRows.map {
                    RawMetricTx(
                        kind = it.kind,
                        amountMinor = it.amountMinor,
                        occurredAt = it.occurredAt.toLocalDate(),
                        currency = MetricCurrency(it.currencyId, it.currencyCode, it.currencyDecimals),
                    )
                },
                monthList,
                base,
                rateScaledMap,
            )
            val missingRates = monthly.missingRates.toMutableSet()

            // Top de categorías de gasto del mes en curso.
            val byCategory = mutableMapOf<String, Long>()
            for (tx in txRows) {
                if (tx.kind != "EXPENSE" || tx.occurredAt < currentMonthStart) continue
                var converted = tx.amountMinor
                if (tx.currencyId != base.id) {
                    val rate = rateScaledMap[tx.currencyId] ?: continue // ya en missingRates
                    converted = convertMinor(
                        tx.amountMinor,
                        MetricCurrency(tx.currencyId, tx.currencyCode, tx.currencyDecimals),
                        base,
                        rate,
                    )
                }
                val name = tx.categoryName ?: "Sin categoría"
                byCategory[name] = (byCategory[name] ?: 0L) + converted
            }
            val topCategories = byCategory.entries
                .sortedByDescending { it.value }
                .take(5)
                .map { CategoryTotal(it.key, it.value) }

            // Totales de deudas abiertas (pendiente) por dirección.
            var receivableMinor = 0L
            var payableMinor = 0L
            for (debt in openDebts) {
                var remaining = debt.totalMinor - debt.paidMinor
                if (remaining <= 0) continue
                if (debt.currencyId != base.id) {
                    val rate = rateScaledMap[debt.currencyId]
                    if (rate == null) {
                        missingRates.add(debt.currencyCode)
                        continue
                    }
                    remaining = convertMinor(
                        remaining,
                        MetricCurrency(debt.currencyId, debt.currencyCode, debt.currencyDecimals),
                        base,
                        rate,
                    )
                }
                if (debt.direction == "RECEIVABLE") receivableMinor += remaining
                else payableMinor += remaining
            }

            DashboardMetrics(
                series = monthly.series,
                topCategories = topCategories,
                receivableMinor = receivableMinor,
                payableMinor = payableMinor,
                missingRates = missingRates,
            )
        }
    }
}


