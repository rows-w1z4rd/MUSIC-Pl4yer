package com.suna.player

import com.suna.player.ui.common.TrackArtKeyer
import com.suna.player.ui.common.TrackArtFetcher
import coil3.SingletonImageLoader
import coil3.PlatformContext
import coil3.ImageLoader
import com.suna.player.data.LyricsRepository
import com.suna.player.data.TagEditor
import android.app.Application
import com.suna.player.data.MediaStoreRepository
import com.suna.player.data.PlaylistStore
import com.suna.player.data.SettingsStore
import com.suna.player.data.FavoritesStore
import com.suna.player.data.StatsStore
import com.suna.player.data.SunaDb
import com.suna.player.data.WallpaperStore
import com.suna.player.playback.PlayerConnection

/** App-wide objects, created on first use. */
class SunaApp : Application(), SingletonImageLoader.Factory {
    val db by lazy { SunaDb.open(this) }
    val library by lazy { MediaStoreRepository(this) }
    val player by lazy { PlayerConnection(this) }
    val settings by lazy { SettingsStore(this) }
    val wallpapers by lazy { WallpaperStore(this) }
    val playlists by lazy { PlaylistStore(this, db) }
    val stats by lazy { StatsStore(db) }
    val favorites by lazy { FavoritesStore(db) }
    val tagEditor by lazy { TagEditor(this) }
    val lyrics by lazy { LyricsRepository(this, settings) }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(TrackArtFetcher.Factory())
                add(TrackArtKeyer())
            }
            .build()
}
