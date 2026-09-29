package ru.kino.dev.database

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import ru.kino.dev.core.CoreException
import ru.kino.dev.core.DatabaseFiles
import ru.kino.dev.core.DatabaseRepository
import ru.kino.dev.core.MergeSummary
import ru.kino.dev.core.NativeCore
import ru.kino.dev.core.NewDatabase
import ru.kino.dev.core.OpenedDatabase
import ru.kino.dev.core.RecentDatabase
import ru.kino.dev.core.RecentDatabases
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DatabaseRepository] over the native core and the document the database lives in.
 *
 * The order of operations is the whole point of this class. The core produces bytes and forgets about
 * them; whether they reached the file is decided here, and a write that half happened is the one failure
 * that loses a database - so writes are shielded from cancellation.
 */
@Singleton
internal class DatabaseRepositoryImpl @Inject constructor(
    private val core: NativeCore,
    private val files: DatabaseFiles,
    private val recent: RecentDatabases,
) : DatabaseRepository {

    override suspend fun create(
        uri: String,
        databaseName: String,
        password: String,
    ): OpenedDatabase {
        files.keepAccess(uri)
        val fileName = files.displayName(uri)

        val created = core.createDatabase(
            NewDatabase(
                dbKey = uri,
                fileName = fileName,
                databaseName = databaseName,
                password = password,
            ),
        )

        try {
            write(uri, created.bytes)
        } catch (e: Throwable) {
            // The core already holds the new database, but its file does not exist: leaving it open would
            // offer the user a database that cannot be saved anywhere
            core.closeDatabase(uri)
            throw e
        }

        recent.remember(created.database.asRecent(uri))
        return created.database
    }

    override suspend fun open(uri: String, password: String): OpenedDatabase {
        files.keepAccess(uri)
        val fileName = files.displayName(uri)
        val bytes = files.read(uri)

        val opened = core.openDatabase(
            dbKey = uri,
            bytes = bytes,
            password = password,
            fileName = fileName,
        )

        // Only a database that actually opened is remembered: an entry for a file with a forgotten password
        // would be an invitation to fail again
        recent.remember(opened.asRecent(uri))
        return opened
    }

    override suspend fun save(dbKey: String) {
        val bytes = core.saveDatabase(dbKey)
        write(dbKey, bytes)
    }

    override suspend fun close(dbKey: String) {
        core.closeDatabase(dbKey)
    }

    override suspend fun hasChangedElsewhere(uri: String): Boolean =
        try {
            core.verifyFileChecksum(uri, files.read(uri))
            false
        } catch (e: CoreException) {
            // The one kind that is an answer rather than a failure: the file is fine, it is just not ours
            // any more. Anything else - the database is not open, the bytes are not a kdbx - is a failure
            if (e.kind == FILE_CHANGED) true else throw e
        }

    override suspend fun merge(uri: String): MergeSummary = core.mergeDatabase(uri, files.read(uri))

    private companion object {
        /** The core's error variant for a file that no longer matches the checksum it was read with. */
        const val FILE_CHANGED = "DbFileContentChangeDetected"
    }

    private suspend fun OpenedDatabase.asRecent(uri: String) = RecentDatabase(
        uri = uri,
        // The name the app resolved, not the one the core derives from the uri - see the note about
        // file_name in NativeCore
        fileName = files.displayName(uri),
        databaseName = databaseName,
        openedAt = System.currentTimeMillis(),
    )

    // Cancelling the coroutine while the bytes are on their way to the file would leave a truncated
    // database, and the user cancelled a screen, not their data
    private suspend fun write(uri: String, bytes: ByteArray) {
        withContext(NonCancellable) {
            files.writeReplacing(uri, bytes)
        }
    }
}
