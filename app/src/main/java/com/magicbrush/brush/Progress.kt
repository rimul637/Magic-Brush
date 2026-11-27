package com.magicbrush.brush

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.progressStore by preferencesDataStore("magic_brush")
private val unlockedKey = intPreferencesKey("unlocked")
private val musicKey = booleanPreferencesKey("music")
private val effectsKey = booleanPreferencesKey("effects")
private val totalKey = intPreferencesKey("total")
private fun starsKey(level: Int) = intPreferencesKey("stars_$level")
private fun inkKey(level: Int) = floatPreferencesKey("ink_$level")

data class Progress(val unlocked: Int = 1, val stars: List<Int> = List(60) { 0 },
                    val total: Int = 0, val music: Boolean = true, val effects: Boolean = true)

class ProgressRepository(context: Context) {
    private val store = context.applicationContext.progressStore
    val state = store.data.catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { data: Preferences ->
            val stars = (1..60).map { data[starsKey(it)] ?: 0 }
            Progress(data[unlockedKey] ?: 1, stars, data[totalKey] ?: stars.sum(),
                data[musicKey] ?: true, data[effectsKey] ?: true)
        }

    suspend fun complete(number: Int, stars: Int, used: Float) = store.edit { data ->
        val key = starsKey(number)
        data[key] = maxOf(data[key] ?: 0, stars)
        val ink = inkKey(number)
        data[ink] = minOf(data[ink] ?: used, used)
        data[totalKey] = (1..60).sumOf { data[starsKey(it)] ?: 0 }
        data[unlockedKey] = maxOf(data[unlockedKey] ?: 1, (number + 1).coerceAtMost(60))
    }

    suspend fun music(enabled: Boolean) = store.edit { it[musicKey] = enabled }
    suspend fun effects(enabled: Boolean) = store.edit { it[effectsKey] = enabled }
}
