package com.nanobuo.mypodder.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "podcasts", indices = [Index(value = ["feedUrl"], unique = true)])
data class Podcast(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedUrl: String,
    val title: String,
    val author: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val link: String? = null,
    /** Categorie iTunes separate da "|". */
    val categories: String? = null,
    val autoDownload: Boolean = false,
    val subscribedAt: Long = System.currentTimeMillis(),
    val lastRefreshed: Long = 0,
)

/** Podcast con il numero di episodi nuovi, per il badge nella libreria. */
data class PodcastWithNew(
    @Embedded val podcast: Podcast,
    val newCount: Int,
)

object DownloadState {
    const val NONE = 0
    const val QUEUED = 1
    const val DOWNLOADING = 2
    const val DONE = 3
    const val FAILED = 4
}

@Entity(
    tableName = "episodes",
    indices = [
        Index(value = ["podcastId", "guid"], unique = true),
        Index(value = ["pubDate"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = Podcast::class,
            parentColumns = ["id"],
            childColumns = ["podcastId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class Episode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val podcastId: Long,
    val guid: String,
    val title: String,
    val description: String? = null,
    val audioUrl: String,
    val mimeType: String? = null,
    val imageUrl: String? = null,
    val link: String? = null,
    val pubDate: Long = 0,
    val durationMs: Long = 0,
    val sizeBytes: Long = 0,
    val chaptersUrl: String? = null,
    val chaptersLoaded: Boolean = false,
    val isNew: Boolean = false,
    val positionMs: Long = 0,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val lastPlayedAt: Long = 0,
    val downloadState: Int = DownloadState.NONE,
    val downloadProgress: Int = 0,
    val localPath: String? = null,
    val downloadedBytes: Long = 0,
)

/** Episodio con i dati del podcast necessari per mostrarlo in una lista. */
data class EpisodeItem(
    @Embedded val episode: Episode,
    val podcastTitle: String,
    val podcastImage: String?,
) {
    val artwork: String? get() = episode.imageUrl ?: podcastImage
}

@Entity(
    tableName = "queue",
    foreignKeys = [
        ForeignKey(
            entity = Episode::class,
            parentColumns = ["id"],
            childColumns = ["episodeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class QueueEntry(
    @PrimaryKey val episodeId: Long,
    val position: Int,
)

@Entity(
    tableName = "chapters",
    primaryKeys = ["episodeId", "startMs"],
    foreignKeys = [
        ForeignKey(
            entity = Episode::class,
            parentColumns = ["id"],
            childColumns = ["episodeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class Chapter(
    val episodeId: Long,
    val startMs: Long,
    val title: String,
    val imageUrl: String? = null,
    val url: String? = null,
)

/** Tempo ascoltato per giorno ed episodio: è la base delle statistiche. */
@Entity(tableName = "listening", primaryKeys = ["day", "episodeId"], indices = [Index("podcastId")])
data class ListeningEntry(
    /** Giorno come epoch day locale. */
    val day: Long,
    val episodeId: Long,
    val podcastId: Long,
    /** Tempo reale trascorso ad ascoltare. */
    val listenedMs: Long,
    /** Durata di contenuto riprodotto: con velocità > 1 è maggiore di listenedMs. */
    val contentMs: Long,
    val completed: Boolean = false,
)

data class DayTotal(val day: Long, val listenedMs: Long)

data class PodcastTotal(
    val podcastId: Long,
    val title: String,
    val imageUrl: String?,
    val listenedMs: Long,
)

data class StatsTotals(
    val listenedMs: Long,
    val contentMs: Long,
    val completed: Int,
)
