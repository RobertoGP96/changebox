package com.lolo.changebox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.lolo.changebox.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

// Cuentas y agregados para el saldo derivado (nunca almacenado): las sumas se
// agregan en la BD con GROUP BY y el saldo se compone con balances-core, igual
// que listAccountsWithBalances en la web (3 consultas fijas).

data class OwnGroupRow(
    val accountId: String,
    val kind: String,
    val sumMinor: Long,
)

data class IncomingGroupRow(
    val accountId: String,
    val sumMinor: Long,
)

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY createdAt ASC")
    fun activeAccountsFlow(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY createdAt ASC")
    fun allAccountsFlow(): Flow<List<AccountEntity>>

    @Query(
        "SELECT * FROM accounts WHERE archived = 0 AND currencyId = :currencyId ORDER BY createdAt ASC"
    )
    fun activeAccountsInCurrencyFlow(currencyId: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun accountById(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun accountFlow(id: String): Flow<AccountEntity?>

    @Insert
    suspend fun insertAccount(account: AccountEntity)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("UPDATE accounts SET name = :name WHERE id = :id")
    suspend fun renameAccount(id: String, name: String)

    @Query("UPDATE accounts SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query("UPDATE accounts SET icon = :icon WHERE id = :id")
    suspend fun setIcon(id: String, icon: String?)

    @Query("UPDATE accounts SET groupId = :groupId WHERE id = :id")
    suspend fun setGroup(id: String, groupId: String?)

    // Las FK en CASCADE se llevan movimientos de ambos lados, desgloses,
    // arqueos y abonos vinculados (mismo efecto que el borrado explícito web).
    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccount(id: String)

    // ── Saldos derivados ────────────────────────────────────────────────────

    @Query(
        """
        SELECT accountId, kind, COALESCE(SUM(amountMinor), 0) AS sumMinor
        FROM transactions
        GROUP BY accountId, kind
        """
    )
    fun ownGroupsFlow(): Flow<List<OwnGroupRow>>

    @Query(
        """
        SELECT counterAccountId AS accountId, COALESCE(SUM(counterAmountMinor), 0) AS sumMinor
        FROM transactions
        WHERE kind = 'TRANSFER' AND counterAccountId IS NOT NULL
        GROUP BY counterAccountId
        """
    )
    fun incomingGroupsFlow(): Flow<List<IncomingGroupRow>>

    // Saldo puntual para validaciones dentro de transacciones de escritura.
    @Query(
        """
        SELECT COALESCE(SUM(
            CASE
                WHEN kind IN ('INCOME', 'ADJUSTMENT') THEN amountMinor
                WHEN kind IN ('EXPENSE', 'TRANSFER') THEN -amountMinor
                ELSE 0
            END
        ), 0)
        FROM transactions WHERE accountId = :accountId
        """
    )
    suspend fun ownSignedSum(accountId: String): Long

    @Query(
        """
        SELECT COALESCE(SUM(counterAmountMinor), 0)
        FROM transactions
        WHERE counterAccountId = :accountId AND kind = 'TRANSFER'
        """
    )
    suspend fun incomingSum(accountId: String): Long

    @Query(
        "SELECT COUNT(*) FROM transactions WHERE accountId = :accountId OR counterAccountId = :accountId"
    )
    suspend fun txTouchCount(accountId: String): Int

    @Query("SELECT COUNT(*) FROM cash_counts WHERE accountId = :accountId")
    suspend fun cashCountCount(accountId: String): Int
}

