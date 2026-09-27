package ru.kino.dev.database

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import ru.kino.dev.core.DatabaseFiles
import ru.kino.dev.core.DatabaseRepository
import ru.kino.dev.core.NativeCore
import ru.kino.dev.core.NewDatabase
import ru.kino.dev.core.OpenedDatabase
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

        return created.database
    }

    override suspend fun open(uri: String, password: String): OpenedDatabase {
        files.keepAccess(uri)
        val fileName = files.displayName(uri)
        val bytes = files.read(uri)

        return core.openDatabase(
            dbKey = uri,
            bytes = bytes,
            password = password,
            fileName = fileName,
        )
    }

    override suspend fun save(dbKey: String) {
        val bytes = core.saveDatabase(dbKey)
        write(dbKey, bytes)
    }

    override suspend fun close(dbKey: String) {
        core.closeDatabase(dbKey)
    }

    // Cancelling the coroutine while the bytes are on their way to the file would leave a truncated
    // database, and the user cancelled a screen, not their data
    private suspend fun write(uri: String, bytes: ByteArray) {
        withContext(NonCancellable) {
            files.writeReplacing(uri, bytes)
        }
    }
}
