package com.lolo.changebox.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import com.lolo.changebox.data.local.entity.CashCountEntity
import com.lolo.changebox.data.local.entity.CashCountLineEntity
import kotlinx.coroutines.flow.Flow

data class CashCountWithAccount(
    @Embedded val count: CashCountEntity,
    val accountName: String,
    val currencyCode: String,
    val currencyDecimals: Int,
)

@Dao
interface CashCountDao {

    @Insert
    suspend fun insertCashCount(count: CashCountEntity)

    @Insert
    suspend fun insertLines(lines: List<CashCountLineEntity>)

    @Query("UPDATE cash_counts SET adjustmentTxId = :txId WHERE id = :id")
    suspend fun setAdjustmentTx(id: String, txId: String)

    // Arqueos recientes con cuenta y moneda (listado /conteo).
    @Query(
        """
        SELECT cc.*, a.name AS accountName,
            c.code AS currencyCode, c.decimalPlaces AS currencyDecimals
        FROM cash_counts cc
        JOIN accounts a ON a.id = cc.accountId
        JOIN currencies c ON c.id = a.currencyId
        ORDER BY cc.countedAt DESC
        LIMIT :limit
        """
    )
    fun recentCountsFlow(limit: Int): Flow<List<CashCountWithAccount>>

    @Query(
        "SELECT * FROM cash_counts WHERE accountId = :accountId ORDER BY countedAt DESC LIMIT 1"
    )
    fun lastCountFlow(accountId: String): Flow<CashCountEntity?>

    @Query(
        "SELECT * FROM cash_counts WHERE accountId = :accountId ORDER BY countedAt DESC LIMIT 1"
    )
    suspend fun lastCount(accountId: String): CashCountEntity?

    @Query("SELECT * FROM cash_count_lines WHERE cashCountId = :countId")
    suspend fun linesFor(countId: String): List<CashCountLineEntity>

    @Query("SELECT * FROM cash_count_lines WHERE cashCountId = :countId")
    fun linesForFlow(countId: String): Flow<List<CashCountLineEntity>>
}

