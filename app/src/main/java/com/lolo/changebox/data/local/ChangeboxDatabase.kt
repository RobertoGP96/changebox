package com.lolo.changebox.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.lolo.changebox.data.local.dao.AccountDao
import com.lolo.changebox.data.local.dao.CashCountDao
import com.lolo.changebox.data.local.dao.CatalogDao
import com.lolo.changebox.data.local.dao.DebtDao
import com.lolo.changebox.data.local.dao.PlanDao
import com.lolo.changebox.data.local.dao.RateDao
import com.lolo.changebox.data.local.dao.TransactionDao
import com.lolo.changebox.data.local.entity.AccountEntity
import com.lolo.changebox.data.local.entity.AccountGroupEntity
import com.lolo.changebox.data.local.entity.CashCountEntity
import com.lolo.changebox.data.local.entity.CashCountLineEntity
import com.lolo.changebox.data.local.entity.CategoryEntity
import com.lolo.changebox.data.local.entity.ContactEntity
import com.lolo.changebox.data.local.entity.CurrencyEntity
import com.lolo.changebox.data.local.entity.DebtEntity
import com.lolo.changebox.data.local.entity.DebtPaymentEntity
import com.lolo.changebox.data.local.entity.DenominationEntity
import com.lolo.changebox.data.local.entity.ExchangeRateEntity
import com.lolo.changebox.data.local.entity.InstallmentEntity
import com.lolo.changebox.data.local.entity.PaymentPlanEntity
import com.lolo.changebox.data.local.entity.TransactionDenominationEntity
import com.lolo.changebox.data.local.entity.TransactionEntity

// Única fuente de verdad de la app: Room, 100% local. El remake offline parte
// de esquema v1 limpio (sin metadatos de sincronización ni usuarios).

@Database(
    entities = [
        CurrencyEntity::class,
        DenominationEntity::class,
        AccountGroupEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TransactionDenominationEntity::class,
        ExchangeRateEntity::class,
        CashCountEntity::class,
        CashCountLineEntity::class,
        ContactEntity::class,
        DebtEntity::class,
        PaymentPlanEntity::class,
        InstallmentEntity::class,
        DebtPaymentEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class ChangeboxDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun cashCountDao(): CashCountDao
    abstract fun debtDao(): DebtDao
    abstract fun planDao(): PlanDao
    abstract fun rateDao(): RateDao

    companion object {
        /**
         * v1 → v2: clasificación de la moneda (`Currency.kind` = CASH |
         * DIGITAL, port de la web). Aditiva y con defecto, así que las
         * monedas ya creadas quedan como efectivo — que es lo que eran.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE currencies ADD COLUMN kind TEXT NOT NULL DEFAULT 'CASH'"
                )
            }
        }

        fun build(context: Context): ChangeboxDatabase =
            // El fichero conserva el nombre histórico: renombrarlo dejaría
            // huérfanos los datos de quien ya tiene la app instalada.
            Room.databaseBuilder(context, ChangeboxDatabase::class.java, "caja.db")
                .addMigrations(MIGRATION_1_2)
                // Red de seguridad para saltos de versión sin migración
                // escrita (BD de prototipos): preferimos arrancar limpios a
                // reventar. Los saltos previstos SÍ llevan migración.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}


