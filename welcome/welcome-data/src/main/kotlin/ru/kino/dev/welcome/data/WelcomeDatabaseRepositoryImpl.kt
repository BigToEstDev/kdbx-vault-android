package ru.kino.dev.welcome.data

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import ru.kino.dev.core.DatabaseFiles
import ru.kino.dev.core.KeyFile
import ru.kino.dev.core.NativeCore
import ru.kino.dev.core.NewDatabase
import ru.kino.dev.core.OpenedDatabase
import ru.kino.dev.core.readKeyFile
import ru.kino.dev.welcome.CurrentDatabase
import ru.kino.dev.welcome.CurrentDatabaseRepository
import ru.kino.dev.welcome.PickedFile
import ru.kino.dev.welcome.WelcomeDatabaseRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [WelcomeDatabaseRepository] over the native core, the picked files and the remembered database.
 *
 * The order is the point of this class. A database is handed up only when everything is fixed: open in the
 * core, access to its files kept, remembered - otherwise "the database is open, but we did not remember
 * it". Each step that fails undoes what came before it: the core closes what it opened, an empty file made
 * for a new database is deleted.
 *
 * The content of a key file is read right before the call that needs it and wiped right after, whatever
 * the call ended with.
 */
@Singleton
internal class WelcomeDatabaseRepositoryImpl @Inject constructor(
    private val core: NativeCore,
    private val files: DatabaseFiles,
    private val current: CurrentDatabaseRepository,
    private val writer: CurrentDatabaseWriter,
) : WelcomeDatabaseRepository {

    override suspend fun open(uri: String, password: String?, keyFileUri: String?): String = welcomeCall {
        val bytes = accessing(PickedFile.Database) { files.read(uri) }
        val fileName = files.displayName(uri)
        val keyFile = keyFileUri?.let { readKeyFile(it) }

        val opened = keyFile.wipedAfter {
            core.openDatabase(dbKey = uri, bytes = bytes, password = password, fileName = fileName, keyFile = it)
        }
        fix(opened, fileName, keyFileUri, keyFile?.name)
    }

    override suspend fun unlock(dbKey: String, password: String?) = welcomeCall {
        // The key file it was opened with - the remembered one, when the remembered database is this one
        val keyFileUri = current.current.first()?.takeIf { it.uri == dbKey }?.keyFileUri
        val keyFile = keyFileUri?.let { readKeyFile(it) }

        keyFile.wipedAfter { core.unlockDatabase(dbKey = dbKey, password = password, keyFile = it) }
        Unit
    }

    override suspend fun create(uri: String, password: String, keyFileUri: String?): String = welcomeCall {
        // Asked before anything else: a file that was empty is ours to delete if creating fails. One that
        // was not is a file the user chose to write over - deleting it would lose what is still in it
        val wasEmpty = accessing(PickedFile.Database) { files.isEmpty(uri) }
        try {
            createIn(uri, password, keyFileUri)
        } catch (e: Throwable) {
            // Cancellation included: the user left the screen, and an abandoned creation leaves nothing.
            // Best effort - a provider that cannot delete leaves the file, and the failure above stays the
            // one reported
            if (wasEmpty) withContext(NonCancellable) { files.delete(uri) }
            throw e
        }
    }

    private suspend fun createIn(uri: String, password: String, keyFileUri: String?): String {
        val fileName = files.displayName(uri)
        val keyFile = keyFileUri?.let { readKeyFile(it) }

        val created = keyFile.wipedAfter {
            core.createDatabase(
                NewDatabase(
                    dbKey = uri,
                    fileName = fileName,
                    databaseName = databaseNameOf(fileName),
                    password = password,
                    keyFile = it,
                ),
            )
        }

        closingOnFailure(uri) {
            // Cancelling halfway through the write would leave a truncated database behind
            withContext(NonCancellable) {
                accessing(PickedFile.Database) { files.writeReplacing(uri, created.bytes) }
            }
        }
        return fix(created.database, fileName, keyFileUri, keyFile?.name)
    }

    /**
     * Keeps access to the files and remembers the database - the steps after which it may be handed up.
     *
     * Access that cannot be kept is "no access", and the database is closed again: remembered without it,
     * it would not open after a restart.
     */
    private suspend fun fix(
        opened: OpenedDatabase,
        fileName: String?,
        keyFileUri: String?,
        keyFileName: String?,
    ): String {
        closingOnFailure(opened.dbKey) {
            accessing(PickedFile.Database) { files.keepAccess(opened.dbKey, forWriting = true) }
            keyFileUri?.let { accessing(PickedFile.KeyFile) { files.keepAccess(it, forWriting = false) } }

            writer.remember(
                CurrentDatabase(
                    uri = opened.dbKey,
                    // The name the app resolved, not the one the core derives from the uri - see the note
                    // about fileName in OpenedDatabase
                    fileName = fileName,
                    databaseName = opened.databaseName,
                    keyFileUri = keyFileUri,
                    keyFileName = keyFileName,
                ),
            )
        }
        return opened.dbKey
    }

    private suspend fun <T> closingOnFailure(dbKey: String, block: suspend () -> T): T =
        try {
            block()
        } catch (e: Throwable) {
            // The failure that brought us here is the one to report; one of closing goes along with it
            withContext(NonCancellable) {
                runCatching { core.closeDatabase(dbKey) }.exceptionOrNull()?.let(e::addSuppressed)
            }
            throw e
        }

    private suspend fun readKeyFile(uri: String): KeyFile = accessing(PickedFile.KeyFile) { files.readKeyFile(uri) }

    // The content is a secret on par with the password: gone as soon as the core has taken it
    private inline fun <T> KeyFile?.wipedAfter(block: (KeyFile?) -> T): T =
        try {
            block(this)
        } finally {
            this?.wipe()
        }

    // The file name without the extension; a provider that tells no name, or a file called just ".kdbx",
    // gets the default rather than a database with no name to show
    private fun databaseNameOf(fileName: String?): String =
        fileName
            ?.let { if (it.endsWith(KDBX, ignoreCase = true)) it.dropLast(KDBX.length) else it }
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_DATABASE_NAME

    private companion object {
        const val KDBX = ".kdbx"

        /** Name of a new database whose file has no name to take it from. */
        const val DEFAULT_DATABASE_NAME = "kdbxvault"
    }
}
