package com.nanobuo.mypodder.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Podcast::class, Episode::class, QueueEntry::class, Chapter::class, ListeningEntry::class, RemoteListening::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        /** v2: categorie del podcast, usate per i suggerimenti. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE podcasts ADD COLUMN categories TEXT")
            }
        }

        /** v3: istante dell'ultima modifica dello stato di ascolto, per la sincronizzazione. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE episodes ADD COLUMN stateUpdatedAt INTEGER NOT NULL DEFAULT 0")
                // Gli episodi già ascoltati partono come modificati: vanno caricati alla prima sincronizzazione.
                db.execSQL(
                    "UPDATE episodes SET stateUpdatedAt = MAX(lastPlayedAt, COALESCE(completedAt, 0)) " +
                        "WHERE positionMs > 0 OR completed = 1"
                )
            }
        }

        /** v4: statistiche sincronizzate (data di modifica e ascolti degli altri dispositivi). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1: le statistiche già presenti vengono caricate alla prima sincronizzazione.
                db.execSQL("ALTER TABLE listening ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 1")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS remote_listening (deviceId TEXT NOT NULL, day INTEGER NOT NULL, " +
                        "feedUrl TEXT NOT NULL, guid TEXT NOT NULL, listenedMs INTEGER NOT NULL, contentMs INTEGER NOT NULL, " +
                        "completed INTEGER NOT NULL, PRIMARY KEY(deviceId, day, feedUrl, guid))"
                )
            }
        }
    }

    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun queueDao(): QueueDao
    abstract fun chapterDao(): ChapterDao
    abstract fun listeningDao(): ListeningDao
}
