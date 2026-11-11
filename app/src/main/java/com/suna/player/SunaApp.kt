package com.suna.player

import android.app.Application
import com.suna.player.data.MediaStoreRepository
import com.suna.player.data.PlaylistStore
import com.suna.player.data.SettingsStore
import com.suna.player.data.StatsStore
import com.suna.player.data.SunaDb
import com.suna.player.data.WallpaperStore
import com.suna.player.playback.PlayerConnection

/** App-wide objects, created on first use. */
class SunaApp : Application() {
    val db by lazy { SunaDb.open(this) }
    val library by lazy { MediaStoreRepository(this) }
    val player by lazy { PlayerConnection(this) }
    val settings by lazy { SettingsStore(this) }
    val wallpapers by lazy { WallpaperStore(this) }
    val playlists by lazy { PlaylistStore(this, db) }
    val stats by lazy { StatsStore(db) }
}
