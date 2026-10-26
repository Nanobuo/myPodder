package com.nanobuo.mypodder.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Podcast::class, Episode::class, QueueEntry::class, Chapter::class, ListeningEntry::class],
    version = 3,
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
    }

    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun queueDao(): QueueDao
    abstract fun chapterDao(): ChapterDao
    abstract fun listeningDao(): ListeningDao
}
