package com.lolo.changebox.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.lolo.changebox.data.local.entity.AccountGroupEntity
import com.lolo.changebox.data.local.entity.CategoryEntity
import com.lolo.changebox.data.local.entity.CurrencyEntity
import com.lolo.changebox.data.local.entity.DenominationEntity
import kotlinx.coroutines.flow.Flow

// Catálogo: monedas, denominaciones, categorías y grupos de cuentas.

data class CurrencyWithCounts(
    @Embedded val currency: CurrencyEntity,
    val accountCount: Int,
    val denominationCount: Int,
)

data class DenominationWithUsage(
    @Embedded val denomination: DenominationEntity,
    val usageCount: Int,
)

data class CategoryWithUsage(
    @Embedded val category: CategoryEntity,
    val usageCount: Int,
)

data class GroupWithCount(
    @Embedded val group: AccountGroupEntity,
    val accountCount: Int,
)

@Dao
interface CatalogDao {

    // ── Monedas ─────────────────────────────────────────────────────────────

    @Query("SELECT * FROM currencies ORDER BY isBase DESC, code ASC")
    fun currenciesFlow(): Flow<List<CurrencyEntity>>

    @Query("SELECT * FROM currencies WHERE active = 1 ORDER BY isBase DESC, code ASC")
    fun activeCurrenciesFlow(): Flow<List<CurrencyEntity>>

    @Query("SELECT * FROM currencies WHERE id = :id")
    suspend fun currencyById(id: String): CurrencyEntity?

    @Query("SELECT * FROM currencies WHERE id = :id")
    fun currencyFlow(id: String): Flow<CurrencyEntity?>

    @Query("SELECT * FROM currencies WHERE code = :code COLLATE NOCASE")
    suspend fun currencyByCode(code: String): CurrencyEntity?

    @Query("SELECT * FROM currencies WHERE isBase = 1 LIMIT 1")
    suspend fun baseCurrency(): CurrencyEntity?

    @Query("SELECT * FROM currencies WHERE isBase = 1 LIMIT 1")
    fun baseCurrencyFlow(): Flow<CurrencyEntity?>

    @Query("SELECT COUNT(*) FROM currencies")
    suspend fun currencyCount(): Int

    @Query(
        """
        SELECT c.*,
            (SELECT COUNT(*) FROM accounts a WHERE a.currencyId = c.id) AS accountCount,
            (SELECT COUNT(*) FROM denominations d WHERE d.currencyId = c.id) AS denominationCount
        FROM currencies c
        ORDER BY c.isBase DESC, c.code ASC
        """
    )
    fun currenciesWithCountsFlow(): Flow<List<CurrencyWithCounts>>

    @Insert
    suspend fun insertCurrency(currency: CurrencyEntity)

    @Update
    suspend fun updateCurrency(currency: CurrencyEntity)

    @Query("UPDATE currencies SET isBase = 0")
    suspend fun clearBase()

    @Query("UPDATE currencies SET isBase = 1, active = 1 WHERE id = :id")
    suspend fun markBase(id: String)

    @Query("UPDATE currencies SET active = :active WHERE id = :id")
    suspend fun setCurrencyActive(id: String, active: Boolean)

    // ── Denominaciones ──────────────────────────────────────────────────────

    @Query(
        "SELECT * FROM denominations WHERE currencyId = :currencyId ORDER BY kind ASC, valueMinor DESC"
    )
    fun denominationsFlow(currencyId: String): Flow<List<DenominationEntity>>

    @Query(
        "SELECT * FROM denominations WHERE currencyId = :currencyId AND active = 1 ORDER BY kind ASC, valueMinor DESC"
    )
    fun activeDenominationsFlow(currencyId: String): Flow<List<DenominationEntity>>

    @Query(
        "SELECT * FROM denominations WHERE currencyId = :currencyId AND active = 1 ORDER BY kind ASC, valueMinor DESC"
    )
    suspend fun activeDenominations(currencyId: String): List<DenominationEntity>

    @Query("SELECT * FROM denominations WHERE id = :id")
    suspend fun denominationById(id: String): DenominationEntity?

    @Query("SELECT * FROM denominations WHERE id IN (:ids) AND currencyId = :currencyId")
    suspend fun denominationsByIds(ids: List<String>, currencyId: String): List<DenominationEntity>

    @Query(
        """
        SELECT (SELECT COUNT(*) FROM cash_count_lines l WHERE l.denominationId = :id) +
               (SELECT COUNT(*) FROM transaction_denominations t WHERE t.denominationId = :id)
        """
    )
    suspend fun denominationUsage(id: String): Int

    @Query(
        """
        SELECT d.*,
            (SELECT COUNT(*) FROM cash_count_lines l WHERE l.denominationId = d.id) +
            (SELECT COUNT(*) FROM transaction_denominations t WHERE t.denominationId = d.id) AS usageCount
        FROM denominations d
        WHERE d.currencyId = :currencyId
        ORDER BY d.kind ASC, d.valueMinor DESC
        """
    )
    fun denominationsWithUsageFlow(currencyId: String): Flow<List<DenominationWithUsage>>

    @Insert
    suspend fun insertDenomination(denomination: DenominationEntity)

    @Insert
    suspend fun insertDenominations(denominations: List<DenominationEntity>)

    @Update
    suspend fun updateDenomination(denomination: DenominationEntity)

    @Query("DELETE FROM denominations WHERE id = :id")
    suspend fun deleteDenomination(id: String)

    // ── Categorías ──────────────────────────────────────────────────────────

    @Query("SELECT * FROM categories WHERE active = 1 ORDER BY name ASC")
    fun activeCategoriesFlow(): Flow<List<CategoryEntity>>

    @Query(
        """
        SELECT c.*,
            (SELECT COUNT(*) FROM transactions t WHERE t.categoryId = c.id) AS usageCount
        FROM categories c
        WHERE c.kind = :kind
        ORDER BY c.active DESC, c.name ASC
        """
    )
    fun categoriesWithUsageFlow(kind: String): Flow<List<CategoryWithUsage>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun categoryById(id: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :id")
    suspend fun categoryUsage(id: String): Int

    @Insert
    suspend fun insertCategory(category: CategoryEntity)

    @Insert
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: String)

    // ── Grupos de cuentas ───────────────────────────────────────────────────

    @Query("SELECT * FROM account_groups ORDER BY name ASC")
    fun groupsFlow(): Flow<List<AccountGroupEntity>>

    @Query(
        """
        SELECT g.*,
            (SELECT COUNT(*) FROM accounts a WHERE a.groupId = g.id) AS accountCount
        FROM account_groups g
        ORDER BY g.name ASC
        """
    )
    fun groupsWithCountsFlow(): Flow<List<GroupWithCount>>

    @Query("SELECT * FROM account_groups WHERE id = :id")
    suspend fun groupById(id: String): AccountGroupEntity?

    @Insert
    suspend fun insertGroup(group: AccountGroupEntity)

    @Update
    suspend fun updateGroup(group: AccountGroupEntity)

    @Query("DELETE FROM account_groups WHERE id = :id")
    suspend fun deleteGroup(id: String)
}

