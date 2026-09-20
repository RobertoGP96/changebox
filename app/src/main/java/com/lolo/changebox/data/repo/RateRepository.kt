package com.lolo.changebox.data.repo

import com.lolo.changebox.data.ActionResult
import com.lolo.changebox.data.fail
import com.lolo.changebox.data.guarded
import com.lolo.changebox.data.local.ChangeboxDatabase
import com.lolo.changebox.data.local.entity.ExchangeRateEntity
import com.lolo.changebox.data.ok
import com.lolo.changebox.domain.MoneyException
import com.lolo.changebox.domain.PairRateLite
import com.lolo.changebox.domain.SERVER_INT_MAX
import com.lolo.changebox.domain.invertRateScaled
import com.lolo.changebox.domain.MinorCurrencyOf
import com.lolo.changebox.domain.pairKey
import com.lolo.changebox.domain.parseAmountToMinor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

// Tasas por PARES, port de lib/rates.ts + lib/services/rate-service.ts:
// serie histórica por par, vigente por par y resolución contra la base.

data class PairRatePoint(val rateScaled: Long, val effectiveAt: Long)

class RateRepository(private val db: ChangeboxDatabase) {

    private val rateDao = db.rateDao()
    private val catalogDao = db.catalogDao()

    /** Serie histórica completa por PAR (clave `fromId→toId`), ascendente. */
    fun pairSeriesFlow(): Flow<Map<String, List<PairRatePoint>>> =
        rateDao.ratesAscFlow().map { rates ->
            val series = LinkedHashMap<String, MutableList<PairRatePoint>>()
            for (rate in rates) {
                series.getOrPut(pairKey(rate.fromCurrencyId, rate.toCurrencyId)) { mutableListOf() }
                    .add(PairRatePoint(rate.rateScaled, rate.effectiveAt))
            }
            series
        }

    /** Última tasa vigente por PAR. */
    fun latestPairRatesFlow(): Flow<Map<String, PairRatePoint>> =
        pairSeriesFlow().map { series -> series.mapValues { it.value.last() } }

    /** Vigentes por par en formato plano para rate-resolve. */
    fun latestPairRatesLiteFlow(): Flow<List<PairRateLite>> =
        latestPairRatesFlow().map { latest ->
            latest.map { (key, point) ->
                val (fromId, toId) = key.split("→")
                PairRateLite(fromId, toId, point.rateScaled)
            }
        }

    /**
     * Tasa vigente de cada moneda CONTRA LA BASE, resuelta desde los pares
     * registrados (directo o inverso). Cambiar la moneda base no invalida nada.
     */
    fun latestRatesByCurrencyFlow(): Flow<Map<String, PairRatePoint>> =
        combine(catalogDao.currenciesFlow(), latestPairRatesFlow()) { currencies, pairs ->
            val base = currencies.firstOrNull { it.isBase }
                ?: return@combine emptyMap()
            val map = mutableMapOf<String, PairRatePoint>()
            for (currency in currencies) {
                if (currency.id == base.id) continue
                resolvePairRate(pairs, currency.id, base.id)?.let { map[currency.id] = it }
            }
            map
        }

    fun pairHistoryFlow(fromId: String, toId: String): Flow<List<ExchangeRateEntity>> =
        rateDao.pairHistoryFlow(fromId, toId)

    suspend fun createExchangeRate(
        fromCurrencyId: String,
        toCurrencyId: String,
        rate: String,
        effectiveAt: Long? = null,
    ): ActionResult<String> = guarded("No se pudo guardar la tasa") {
        if (fromCurrencyId == toCurrencyId) return@guarded fail("Elige dos monedas distintas")

        val from = catalogDao.currencyById(fromCurrencyId)
        val to = catalogDao.currencyById(toCurrencyId)
        if (from == null || !from.active || to == null || !to.active) {
            return@guarded fail("Moneda no válida")
        }

        // La tasa se parsea con la misma precisión con que se almacena (×10 000).
        val rateScaled = try {
            parseAmountToMinor(rate, MinorCurrencyOf(4))
        } catch (e: MoneyException) {
            return@guarded fail(e.message ?: "Tasa inválida")
        }
        if (rateScaled <= 0) return@guarded fail("La tasa debe ser mayor que cero")
        if (rateScaled > SERVER_INT_MAX) return@guarded fail("La tasa es demasiado grande")

        val entity = ExchangeRateEntity(
            fromCurrencyId = from.id,
            toCurrencyId = to.id,
            rateScaled = rateScaled,
            effectiveAt = effectiveAt ?: System.currentTimeMillis(),
        )
        rateDao.insertRate(entity)
        return@guarded ok(entity.id)
    }

    companion object {
        /** Tasa from→to con fecha, usando par directo o el inverso. */
        fun resolvePairRate(
            pairs: Map<String, PairRatePoint>,
            fromId: String,
            toId: String,
        ): PairRatePoint? {
            pairs[pairKey(fromId, toId)]?.let { return it }
            val inverse = pairs[pairKey(toId, fromId)] ?: return null
            return try {
                PairRatePoint(invertRateScaled(inverse.rateScaled), inverse.effectiveAt)
            } catch (e: MoneyException) {
                null
            }
        }
    }
}


