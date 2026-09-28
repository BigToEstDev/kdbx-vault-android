package ru.kino.dev.core

import kotlinx.coroutines.flow.Flow

/**
 * The databases the user has opened before.
 *
 * Needed because a picked document is reachable only through its uri, and a uri is not something a person
 * can retype: without this list every launch would start with the file picker. The access behind each uri
 * is kept alive by [DatabaseFiles.keepAccess], and it can still be lost - the file may be deleted, a card
 * removed, the provider uninstalled - so an entry here is a good guess, not a promise.
 */
interface RecentDatabases {

    /** Most recently opened first. Emits again whenever the list changes. */
    val all: Flow<List<RecentDatabase>>

    /** Adds the database or moves it to the front. */
    suspend fun remember(database: RecentDatabase)

    /** Drops the entry, for when its file turns out to be gone. */
    suspend fun forget(uri: String)
}

/**
 * One remembered database.
 *
 * @param uri the document, and the identity of the database in the core
 * @param fileName name to show; the uri itself is not readable
 * @param databaseName name from inside the database, as the core reported it when it was last open
 * @param openedAt when it was last opened, epoch milliseconds - the list is sorted by it
 */
data class RecentDatabase(
    val uri: String,
    val fileName: String?,
    val databaseName: String?,
    val openedAt: Long,
)
