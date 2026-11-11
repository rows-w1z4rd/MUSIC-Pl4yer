package com.suna.player.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val createdAt: Long,
)

/** One song in one playlist; [position] keeps the user's order. */
@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    indices = [Index("playlistId", "position")],
)
data class PlaylistSongEntity(
    val playlistId: Long,
    val songId: Long,
    val position: Int,
)

/** Running totals per song, so "most played" and "recently played" are instant. */
@Entity(tableName = "play_stats")
data class PlayStatEntity(
    @PrimaryKey val songId: Long,
    val playCount: Int,
    val lastPlayedAt: Long,
)

/** Every counted play, kept for future statistics (top of the month, listening time). */
@Entity(tableName = "play_events", indices = [Index("playedAt")])
data class PlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val playedAt: Long,
    val listenedMs: Long,
)

data class PlaylistSongRow(val playlistId: Long, val songId: Long)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt")
    fun playlists(): Flow<List<PlaylistEntity>>

    @Query("SELECT playlistId, songId FROM playlist_songs ORDER BY playlistId, position")
    fun songs(): Flow<List<PlaylistSongRow>>

    @Query("INSERT INTO playlists (id, name, createdAt) VALUES (:id, :name, :createdAt)")
    suspend fun insert(id: Long, name: String, createdAt: Long)

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :id")
    suspend fun clearSongs(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :id")
    suspend fun lastPosition(id: Long): Int

    /** Ignored if the song is already in the playlist. */
    @Query(
        "INSERT OR IGNORE INTO playlist_songs (playlistId, songId, position) " +
            "VALUES (:id, :songId, :position)",
    )
    suspend fun insertSong(id: Long, songId: Long, position: Int)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :id AND songId = :songId")
    suspend fun removeSong(id: Long, songId: Long)

    @Query("SELECT MAX(id) FROM playlists")
    suspend fun maxId(): Long?

    @Transaction
    suspend fun create(id: Long, name: String, songIds: List<Long>) {
        insert(id, name, System.currentTimeMillis())
        songIds.distinct().forEachIndexed { i, songId -> insertSong(id, songId, i) }
    }

    @Transaction
    suspend fun delete(id: Long) {
        clearSongs(id)
        deletePlaylist(id)
    }

    @Transaction
    suspend fun append(id: Long, songId: Long) {
        insertSong(id, songId, lastPosition(id) + 1)
    }
}

data class SongPlays(val songId: Long, val playCount: Int, val lastPlayedAt: Long)

@Dao
interface StatsDao {
    @Query("SELECT songId, playCount, lastPlayedAt FROM play_stats")
    fun all(): Flow<List<SongPlays>>

    @Query(
        "INSERT INTO play_stats (songId, playCount, lastPlayedAt) VALUES (:songId, 1, :at) " +
            "ON CONFLICT(songId) DO UPDATE SET playCount = playCount + 1, lastPlayedAt = :at",
    )
    suspend fun bump(songId: Long, at: Long)

    @Query("INSERT INTO play_events (songId, playedAt, listenedMs) VALUES (:songId, :at, :listenedMs)")
    suspend fun logEvent(songId: Long, at: Long, listenedMs: Long)

    @Transaction
    suspend fun recordPlay(songId: Long, listenedMs: Long) {
        val now = System.currentTimeMillis()
        bump(songId, now)
        logEvent(songId, now, listenedMs)
    }
}

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        PlayStatEntity::class,
        PlayEventEntity::class,
    ],
    version = 1,
)
abstract class SunaDb : RoomDatabase() {
    abstract fun playlists(): PlaylistDao
    abstract fun stats(): StatsDao

    companion object {
        fun open(context: Context): SunaDb =
            Room.databaseBuilder(context, SunaDb::class.java, "suna.db").build()
    }
}
