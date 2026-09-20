package com.lolo.changebox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lolo.changebox.data.local.entity.ExchangeRateEntity
import kotlinx.coroutines.flow.Flow

// Tasas por PARES: histórico puro, nunca se sobreescribe. La vigente por par
// es la de mayor effectiveAt (series ascendentes → último elemento).

@Dao
interface RateDao {

    @Insert
    suspend fun insertRate(rate: ExchangeRateEntity)

    @Query("SELECT * FROM exchange_rates ORDER BY effectiveAt ASC, createdAt ASC")
    fun ratesAscFlow(): Flow<List<ExchangeRateEntity>>

    @Query("SELECT * FROM exchange_rates ORDER BY effectiveAt ASC, createdAt ASC")
    suspend fun ratesAsc(): List<ExchangeRateEntity>

    @Query(
        """
        SELECT * FROM exchange_rates
        WHERE fromCurrencyId = :fromId AND toCurrencyId = :toId
        ORDER BY effectiveAt ASC, createdAt ASC
        """
    )
    fun pairHistoryFlow(fromId: String, toId: String): Flow<List<ExchangeRateEntity>>
}

