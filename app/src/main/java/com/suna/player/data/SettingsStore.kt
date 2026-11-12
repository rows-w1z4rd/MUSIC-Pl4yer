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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Color themes, all on pure black. Most are well-known palettes from the Linux desktop world.
 * (OLED is the plain grey one; the name is kept so saved settings still match.)
 */
enum class Palette(val label: String) {
    OLED("Mono"),
    PHOSPHOR("Phosphor"),
    MOCHA("Catppuccin Mocha"),
    NORD("Nord"),
    GRUVBOX("Gruvbox"),
    ROSE_PINE("Rosé Pine"),
    TOKYO_NIGHT("Tokyo Night"),
    GRAPHITE("Graphite"),
    SYSTEM("Phone colors"),
}

/** Pages that can be shown in the library, in the user's chosen order. */
enum class LibraryTab(val label: String) {
    SONGS("Songs"),
    FAVORITES("Favorites"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists"),
    FOLDERS("Folders"),
    MOST_PLAYED("Most played"),
    RECENT("Recently played"),
}

val DefaultTabs = listOf(LibraryTab.SONGS, LibraryTab.FAVORITES, LibraryTab.ALBUMS, LibraryTab.ARTISTS, LibraryTab.PLAYLISTS)

data class Appearance(
    val palette: Palette = Palette.OLED,
    /** ARGB accent color, or null to use the palette's own accent. */
    val accent: Int? = null,
    /** Text shown at the top of the library; empty shows nothing. */
    val headerText: String = "",
    val tabs: List<LibraryTab> = DefaultTabs,
    val hasWallpaper: Boolean = false,
    /** Changes whenever a new wallpaper image is chosen. */
    val wallpaperStamp: Long = 0,
    /** 0 = sharp, 1 = strongest blur. */
    val blur: Float = 0.5f,
    /** 0 = no darkening, 0.9 = almost black. */
    val dim: Float = 0.45f,
    /** Wallpaper zoom; below 1 the picture is smaller than the screen. */
    val wallpaperScale: Float = 1f,
    /** Wallpaper shift as a fraction of the screen's width and height. */
    val wallpaperX: Float = 0f,
    val wallpaperY: Float = 0f,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    val appearance: Flow<Appearance> = context.dataStore.data.map { p ->
        Appearance(
            palette = p[PALETTE]?.let { name -> Palette.entries.find { it.name == name } }
                ?: Palette.OLED,
            accent = p[ACCENT],
            headerText = p[HEADER] ?: "",
            tabs = p[TABS]?.let(::parseTabs) ?: DefaultTabs,
            hasWallpaper = p[HAS_WALLPAPER] ?: false,
            wallpaperStamp = p[WALLPAPER_STAMP] ?: 0L,
            blur = p[BLUR] ?: 0.5f,
            dim = p[DIM] ?: 0.45f,
            wallpaperScale = p[WP_SCALE] ?: 1f,
            wallpaperX = p[WP_X] ?: 0f,
            wallpaperY = p[WP_Y] ?: 0f,
        )
    }

    /** Skipping silence turns off the low-power audio path, so it is off by default. */
    val skipSilence: Flow<Boolean> =
        context.dataStore.data.map { it[SKIP_SILENCE] ?: false }.distinctUntilChanged()

    suspend fun setPalette(palette: Palette) {
        context.dataStore.edit {
            it[PALETTE] = palette.name
            it.remove(ACCENT) // each palette starts with its own accent
        }
    }

    suspend fun setAccent(argb: Int?) {
        context.dataStore.edit { if (argb == null) it.remove(ACCENT) else it[ACCENT] = argb }
    }

    suspend fun setHeaderText(text: String) {
        context.dataStore.edit { it[HEADER] = text.take(40) }
    }

    /** At least one tab always stays. */
    suspend fun setTabs(tabs: List<LibraryTab>) {
        if (tabs.isEmpty()) return
        context.dataStore.edit { it[TABS] = tabs.distinct().joinToString(",") { t -> t.name } }
    }

    suspend fun setWallpaper(present: Boolean) {
        context.dataStore.edit {
            it[HAS_WALLPAPER] = present
            it[WALLPAPER_STAMP] = System.currentTimeMillis()
            it.remove(WP_SCALE)
            it.remove(WP_X)
            it.remove(WP_Y)
        }
    }

    suspend fun setWallpaperPlacement(scale: Float, x: Float, y: Float) {
        context.dataStore.edit {
            it[WP_SCALE] = scale.coerceIn(MIN_WALLPAPER_SCALE, MAX_WALLPAPER_SCALE)
            it[WP_X] = x.coerceIn(-1f, 1f)
            it[WP_Y] = y.coerceIn(-1f, 1f)
        }
    }

    suspend fun setBlur(value: Float) {
        context.dataStore.edit { it[BLUR] = value.coerceIn(0f, 1f) }
    }

    suspend fun setDim(value: Float) {
        context.dataStore.edit { it[DIM] = value.coerceIn(0f, 0.9f) }
    }

    /**
     * Hands decoding to the phone's audio chip while the screen is off, which saves battery.
     * Some phones skip or stutter with it, so it can be turned off.
     */
    val audioOffload: Flow<Boolean> =
        context.dataStore.data.map { it[AUDIO_OFFLOAD] ?: false }.distinctUntilChanged()

    suspend fun setAudioOffload(on: Boolean) {
        context.dataStore.edit { it[AUDIO_OFFLOAD] = on }
    }

    suspend fun setSkipSilence(on: Boolean) {
        context.dataStore.edit { it[SKIP_SILENCE] = on }
    }

    private fun parseTabs(raw: String): List<LibraryTab>? =
        raw.split(',')
            .mapNotNull { name -> LibraryTab.entries.find { it.name == name } }
            .distinct()
            .ifEmpty { null }

    companion object {
        const val MIN_WALLPAPER_SCALE = 0.3f
        const val MAX_WALLPAPER_SCALE = 4f

        private val PALETTE = stringPreferencesKey("palette")
        private val ACCENT = intPreferencesKey("accent")
        private val HEADER = stringPreferencesKey("header_text")
        private val TABS = stringPreferencesKey("tabs")
        private val HAS_WALLPAPER = booleanPreferencesKey("has_wallpaper")
        private val WALLPAPER_STAMP = longPreferencesKey("wallpaper_stamp")
        private val BLUR = floatPreferencesKey("blur")
        private val DIM = floatPreferencesKey("dim")
        private val WP_SCALE = floatPreferencesKey("wallpaper_scale")
        private val WP_X = floatPreferencesKey("wallpaper_x")
        private val WP_Y = floatPreferencesKey("wallpaper_y")
        private val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        private val AUDIO_OFFLOAD = booleanPreferencesKey("audio_offload")
    }
}
