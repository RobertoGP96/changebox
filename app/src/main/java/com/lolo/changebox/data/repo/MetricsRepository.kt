package com.lolo.changebox.data.repo

import com.lolo.changebox.data.atStartOfDayMillis
import com.lolo.changebox.data.local.ChangeboxDatabase
import com.lolo.changebox.data.toLocalDate
import com.lolo.changebox.domain.BaseCurrencyInfo
import com.lolo.changebox.domain.DisplayCurrencyOf
import com.lolo.changebox.domain.IncomeCardSeries
import com.lolo.changebox.domain.MetricCurrency
import com.lolo.changebox.domain.MonthBucket
import com.lolo.changebox.domain.RawMetricTx
import com.lolo.changebox.domain.buildIncomeCardSeries
import com.lolo.changebox.domain.buildMonthlySeries
import com.lolo.changebox.domain.convertMinor
import com.lolo.changebox.domain.incomeCardRangeStart
import com.lolo.changebox.domain.lastMonths
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

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

/** Datos del gadget «Resumen de ingresos» (incomeCardData de metrics.ts). */
data class IncomeCardData(
    /** Moneda de despliegue: la de la cuenta filtrada o la base. */
    val currency: DisplayCurrencyOf,
    val series: IncomeCardSeries,
    val missingRates: List<String>,
    /** Nombre de la cuenta filtrada; null = todas las cuentas. */
    val accountName: String?,
)

/** Sin tope real: la ventana de 12 meses ya acota las filas. */
private const val INCOME_CARD_ROW_LIMIT = Int.MAX_VALUE

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

    /**
     * Series día/semana/mes del gadget «Resumen de ingresos». Con cuenta: sus
     * ingresos/gastos (lado origen) en SU moneda, sin conversión; null si la
     * cuenta ya no existe. Sin cuenta: todo convertido a la base con la tasa
     * vigente; null si no hay moneda base. Port de incomeCardData.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun incomeCardDataFlow(accountId: String?): Flow<IncomeCardData?> {
        val rangeStart = incomeCardRangeStart().atStartOfDayMillis()

        if (accountId != null) {
            return db.accountDao().accountFlow(accountId).flatMapLatest { account ->
                if (account == null) {
                    flowOf(null)
                } else {
                    combine(
                        db.catalogDao().currencyFlow(account.currencyId),
                        db.transactionDao().filteredRowsFlow(
                            rangeStart, null, account.id, null, null, INCOME_CARD_ROW_LIMIT,
                        ),
                    ) { currency, rows ->
                        if (currency == null) {
                            null
                        } else {
                            // Los movimientos se guardan en la moneda de la
                            // cuenta: no hay conversión (mapa de tasas vacío).
                            val result = buildIncomeCardSeries(
                                rows
                                    .filter { it.kind == "INCOME" || it.kind == "EXPENSE" }
                                    .map {
                                        RawMetricTx(
                                            kind = it.kind,
                                            amountMinor = it.amountMinor,
                                            occurredAt = it.occurredAt.toLocalDate(),
                                            currency = MetricCurrency(
                                                it.currencyId, it.currencyCode, it.currencyDecimals,
                                            ),
                                        )
                                    },
                                BaseCurrencyInfo(currency.id, currency.decimalPlaces),
                                emptyMap(),
                            )
                            IncomeCardData(
                                currency = DisplayCurrencyOf(currency.code, currency.decimalPlaces),
                                series = result.series,
                                missingRates = result.missingRates.toList(),
                                accountName = account.name,
                            )
                        }
                    }
                }
            }
        }

        return db.catalogDao().baseCurrencyFlow().flatMapLatest { base ->
            if (base == null) {
                flowOf(null)
            } else {
                combine(
                    rates.latestRatesByCurrencyFlow(),
                    db.transactionDao().metricRowsFlow(rangeStart),
                ) { rateMap, rows ->
                    val result = buildIncomeCardSeries(
                        rows.map {
                            RawMetricTx(
                                kind = it.kind,
                                amountMinor = it.amountMinor,
                                occurredAt = it.occurredAt.toLocalDate(),
                                currency = MetricCurrency(
                                    it.currencyId, it.currencyCode, it.currencyDecimals,
                                ),
                            )
                        },
                        BaseCurrencyInfo(base.id, base.decimalPlaces),
                        rateMap.mapValues { it.value.rateScaled },
                    )
                    IncomeCardData(
                        currency = DisplayCurrencyOf(base.code, base.decimalPlaces),
                        series = result.series,
                        missingRates = result.missingRates.toList(),
                        accountName = null,
                    )
                }
            }
        }
    }
}


