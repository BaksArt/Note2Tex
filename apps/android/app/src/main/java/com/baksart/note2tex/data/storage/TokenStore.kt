package com.baksart.note2tex.data.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore("note2tex_prefs")

class TokenStore(private val context: Context) {
    companion object {
        private val KEY_TOKEN = stringPreferencesKey("token")
    }

    suspend fun setToken(token: String?) {
        context.dataStore.edit { prefs ->
            if (token.isNullOrBlank()) prefs.remove(KEY_TOKEN) else prefs[KEY_TOKEN] = token
        }
    }
    val tokenFlow: Flow<String?> = context.dataStore.data.map { it[KEY_TOKEN] }

    suspend fun getToken(): String? = tokenFlow.first()

    fun getTokenBlocking(): String? = runBlocking { getToken() }
}
