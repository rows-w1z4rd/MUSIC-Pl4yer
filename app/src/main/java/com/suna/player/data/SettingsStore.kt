package com.suna.player.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
    BLACK("Black (OLED)"),
}

data class Appearance(
    val themeMode: ThemeMode = ThemeMode.BLACK,
    /** ARGB accent color, or null to follow the phone's own color palette. */
    val accent: Int? = null,
    val hasWallpaper: Boolean = false,
    /** Changes whenever a new wallpaper image is chosen. */
    val wallpaperStamp: Long = 0,
    /** 0 = sharp, 1 = strongest blur. */
    val blur: Float = 0.5f,
    /** 0 = no darkening, 0.9 = almost black. */
    val dim: Float = 0.45f,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    val appearance: Flow<Appearance> = context.dataStore.data.map { p ->
        Appearance(
            themeMode = p[THEME]?.let { name -> ThemeMode.entries.find { it.name == name } }
                ?: ThemeMode.BLACK,
            accent = p[ACCENT],
            hasWallpaper = p[HAS_WALLPAPER] ?: false,
            wallpaperStamp = p[WALLPAPER_STAMP] ?: 0L,
            blur = p[BLUR] ?: 0.5f,
            dim = p[DIM] ?: 0.45f,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[THEME] = mode.name }
    }

    suspend fun setAccent(argb: Int?) {
        context.dataStore.edit { if (argb == null) it.remove(ACCENT) else it[ACCENT] = argb }
    }

    suspend fun setWallpaper(present: Boolean) {
        context.dataStore.edit {
            it[HAS_WALLPAPER] = present
            it[WALLPAPER_STAMP] = System.currentTimeMillis()
        }
    }

    suspend fun setBlur(value: Float) {
        context.dataStore.edit { it[BLUR] = value.coerceIn(0f, 1f) }
    }

    suspend fun setDim(value: Float) {
        context.dataStore.edit { it[DIM] = value.coerceIn(0f, 0.9f) }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val ACCENT = intPreferencesKey("accent")
        val HAS_WALLPAPER = booleanPreferencesKey("has_wallpaper")
        val WALLPAPER_STAMP = longPreferencesKey("wallpaper_stamp")
        val BLUR = floatPreferencesKey("blur")
        val DIM = floatPreferencesKey("dim")
    }
}
