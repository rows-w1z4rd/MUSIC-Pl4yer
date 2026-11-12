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
 * The theme: the background and overall look. Blue is the default; Black keeps unlit pixels
 * off on an OLED screen; From wallpaper takes its colors from the picture.
 */
enum class Palette(val label: String) {
    BLUE("Blue"),
    BLACK("Black"),
    GREEN("Green"),
    WHITE("White"),
    WALLPAPER("From wallpaper"),
}

/**
 * The accent: the highlight color on top of the theme (buttons, progress bar, heart, selected
 * tab). Each is drawn in a shade that suits the theme, brighter on dark and deeper on white.
 */
enum class AccentHue(val label: String) {
    MONO("Mono"),
    BLUE("Blue"),
    SKY("Sky"),
    GREEN("Green"),
    MINT("Mint"),
    PURPLE("Purple"),
    PINK("Pink"),
    RED("Red"),
    ORANGE("Orange"),
    GOLD("Gold"),
    BLACK("Black"),
}

/** Pages that can be shown in the library, in the user's chosen order. */
enum class LibraryTab(val label: String) {
    SONGS("Tracks"),
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
    val palette: Palette = Palette.BLUE,
    /** Null uses the theme's own accent. */
    val accent: AccentHue? = null,
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
            palette = p[PALETTE]?.let(::parsePalette) ?: Palette.BLUE,
            accent = p[ACCENT_HUE]?.let { name -> AccentHue.entries.find { it.name == name } },
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
            it.remove(ACCENT_HUE) // each theme starts with its own accent
        }
    }

    suspend fun setAccent(hue: AccentHue?) {
        context.dataStore.edit {
            it.remove(OLD_ACCENT)
            if (hue == null) it.remove(ACCENT_HUE) else it[ACCENT_HUE] = hue.name
        }
    }

    /** Themes from older versions map onto today's set. */
    private fun parsePalette(name: String): Palette =
        Palette.entries.find { it.name == name } ?: if (name == "OLED") Palette.BLACK else Palette.BLUE

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

    /**
     * Saves everything chosen in the wallpaper preview in one go, so the app redraws once.
     * A new picture also switches to its colors, as picking one always did.
     */
    suspend fun applyWallpaper(newPicture: Boolean, scale: Float, x: Float, y: Float, blur: Float, dim: Float) {
        context.dataStore.edit {
            it[HAS_WALLPAPER] = true
            if (newPicture) {
                it[WALLPAPER_STAMP] = System.currentTimeMillis()
                it[PALETTE] = Palette.WALLPAPER.name
                it.remove(ACCENT_HUE)
            }
            it[WP_SCALE] = scale.coerceIn(MIN_WALLPAPER_SCALE, MAX_WALLPAPER_SCALE)
            it[WP_X] = x.coerceIn(-1f, 1f)
            it[WP_Y] = y.coerceIn(-1f, 1f)
            it[BLUR] = blur.coerceIn(0f, 1f)
            it[DIM] = dim.coerceIn(0f, 0.9f)
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

    /** When the queue runs out, more tracks from the library are added so music never stops. */
    val endlessPlay: Flow<Boolean> =
        context.dataStore.data.map { it[ENDLESS_PLAY] ?: true }.distinctUntilChanged()

    suspend fun setEndlessPlay(on: Boolean) {
        context.dataStore.edit { it[ENDLESS_PLAY] = on }
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
        private val OLD_ACCENT = intPreferencesKey("accent")
        private val ACCENT_HUE = stringPreferencesKey("accent_hue")
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
        private val ENDLESS_PLAY = booleanPreferencesKey("endless_play")
    }
}
