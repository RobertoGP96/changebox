package com.lolo.changebox.di

import android.content.Context
import com.lolo.changebox.data.local.ChangeboxDatabase
import com.lolo.changebox.data.prefs.UserPrefs
import com.lolo.changebox.data.repo.AccountRepository
import com.lolo.changebox.data.repo.CashCountRepository
import com.lolo.changebox.data.repo.CatalogRepository
import com.lolo.changebox.data.repo.DebtRepository
import com.lolo.changebox.data.repo.LedgerRepository
import com.lolo.changebox.data.repo.MetricsRepository
import com.lolo.changebox.data.repo.PlanRepository
import com.lolo.changebox.data.repo.RateRepository

// Inyección manual: un contenedor único con la BD y los repositorios.
// Sin frameworks de DI — la app es pequeña y el grafo, plano.

class AppContainer(context: Context) {

    val db: ChangeboxDatabase = ChangeboxDatabase.build(context)

    val prefs = UserPrefs(context)

    val catalog = CatalogRepository(db)
    val rates = RateRepository(db)
    val accounts = AccountRepository(db)
    val ledger = LedgerRepository(db)
    val cashCounts = CashCountRepository(db, accounts)
    val debts = DebtRepository(db)
    val plans = PlanRepository(db)
    val metrics = MetricsRepository(db, rates)
}


