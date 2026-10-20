package com.nanobuo.mypodder.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Podcast::class, Episode::class, QueueEntry::class, Chapter::class, ListeningEntry::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun queueDao(): QueueDao
    abstract fun chapterDao(): ChapterDao
    abstract fun listeningDao(): ListeningDao
}
