package com.lolo.changebox

import android.app.Application
import com.lolo.changebox.data.local.seedDefaultsIfEmpty
import com.lolo.changebox.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ChangeboxApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Primera ejecución: monedas por defecto (CUP base, USD, EUR, MLC) con
        // sus denominaciones reales y las categorías base — igual que el
        // bootstrapUserDefaults del registro web.
        applicationScope.launch {
            seedDefaultsIfEmpty(container.db)
        }
    }
}

