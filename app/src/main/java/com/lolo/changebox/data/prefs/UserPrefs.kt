package com.lolo.changebox.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Preferencias locales del usuario. Sin cuentas ni sesiones: lo único que la
// web guardaba en el perfil y aquí sigue teniendo sentido offline es el nombre
// para personalizar la app.

private val Context.dataStore by preferencesDataStore(name = "caja_prefs")

class UserPrefs(private val context: Context) {

    private val nameKey = stringPreferencesKey("user_name")

    val userNameFlow: Flow<String> =
        context.dataStore.data.map { prefs -> prefs[nameKey] ?: "" }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { prefs -> prefs[nameKey] = name.trim() }
    }
}

