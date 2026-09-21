package com.lolo.changebox.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lolo.changebox.data.ActionResult
import com.lolo.changebox.data.guarded
import com.lolo.changebox.data.ok
import com.lolo.changebox.domain.DashboardPrefs
import com.lolo.changebox.domain.WidgetType
import com.lolo.changebox.domain.parseDashboardPrefs
import com.lolo.changebox.domain.serializeDashboardPrefs
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

// Preferencias locales del usuario. Sin cuentas ni sesiones: lo que la web
// guardaba en el perfil y aquí sigue teniendo sentido offline es el nombre
// para personalizar la app y las preferencias del dashboard (en la web,
// User.dashboardPrefs: JSON serializado, null = defaults).

private val Context.dataStore by preferencesDataStore(name = "caja_prefs")

class UserPrefs(private val context: Context) {

    private val nameKey = stringPreferencesKey("user_name")
    private val dashboardKey = stringPreferencesKey("dashboard_prefs")

    val userNameFlow: Flow<String> =
        context.dataStore.data.map { prefs -> prefs[nameKey] ?: "" }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { prefs -> prefs[nameKey] = name.trim() }
    }

    /**
     * Preferencias del Inicio YA normalizadas (parseDashboardPrefs pasa por
     * normalizeDashboardPrefs y nunca lanza). Un fallo de lectura del
     * DataStore equivale a "sin preferencias" → defaults.
     */
    val dashboardPrefsFlow: Flow<DashboardPrefs> =
        context.dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> parseDashboardPrefs(prefs[dashboardKey]) }

    /**
     * Port de saveDashboardPrefs: normaliza, descarta EN SILENCIO los gadgets
     * y cuentas fijadas que referencian cuentas/monedas que ya no existen
     * (al renderizar tampoco existirían) y guarda el JSON serializado.
     */
    suspend fun saveDashboardPrefs(
        input: DashboardPrefs,
        existingAccountIds: Set<String>,
        existingCurrencyIds: Set<String>,
    ): ActionResult<Unit> = guarded("No se pudieron guardar las preferencias") {
        val prefs = renormalize(input)

        val widgets = prefs.widgets.filter { widget ->
            val accountId = widget.accountId
            if (accountId != null && accountId !in existingAccountIds) {
                false
            } else if (widget.type == WidgetType.RATE_PAIR) {
                val fromId = widget.fromCurrencyId
                val toId = widget.toCurrencyId
                fromId != null && toId != null &&
                    fromId in existingCurrencyIds && toId in existingCurrencyIds
            } else {
                true
            }
        }
        val cleaned = renormalize(
            DashboardPrefs(
                sections = prefs.sections,
                widgets = widgets,
                accountIds = prefs.accountIds?.filter { it in existingAccountIds },
            )
        )

        context.dataStore.edit { stored ->
            stored[dashboardKey] = serializeDashboardPrefs(cleaned)
        }
        ok(Unit)
    }

    /** normalizeDashboardPrefs sobre un objeto ya tipado (ida y vuelta JSON). */
    private fun renormalize(prefs: DashboardPrefs): DashboardPrefs =
        parseDashboardPrefs(serializeDashboardPrefs(prefs))
}
