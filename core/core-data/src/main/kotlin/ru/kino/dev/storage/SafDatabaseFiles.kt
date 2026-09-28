package ru.kino.dev.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.kino.dev.core.DatabaseFileException
import ru.kino.dev.core.DatabaseFiles
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DatabaseFiles] on top of the storage access framework.
 *
 * The app never holds a path: the user picks a document, the provider behind it may be local storage, a
 * cloud client or another app entirely, and all of them are reached through the same uri. That is also why
 * every call here is io on a dispatcher - a "file" may be a network round trip.
 */
@Singleton
internal class SafDatabaseFiles @Inject constructor(
    // The use site target is explicit: without it Kotlin warns that a future version would also apply the
    // annotation to the backing field, and Dagger only ever reads it on the parameter
    @param:ApplicationContext private val context: Context,
) : DatabaseFiles {

    private val dispatcher: CoroutineDispatcher = Dispatchers.IO

    private val resolver: ContentResolver get() = context.contentResolver

    override suspend fun read(uri: String): ByteArray = withContext(dispatcher) {
        try {
            resolver.openInputStream(Uri.parse(uri))
                ?.use { it.readBytes() }
                ?: throw DatabaseFileException("The file could not be opened for reading: $uri")
        } catch (e: IOException) {
            throw DatabaseFileException("The file could not be read: $uri", e)
        } catch (e: SecurityException) {
            throw DatabaseFileException("There is no permission to read the file any more: $uri", e)
        }
    }

    override suspend fun writeReplacing(uri: String, bytes: ByteArray) {
        withContext(dispatcher) {
            try {
                // "wt" means truncate: without it a shorter database leaves the tail of the longer one
                // behind, and the file stops being a valid kdbx as soon as it grows shorter once
                resolver.openOutputStream(Uri.parse(uri), "wt")
                    ?.use { it.write(bytes) }
                    ?: throw DatabaseFileException("The file could not be opened for writing: $uri")
            } catch (e: IOException) {
                throw DatabaseFileException("The file could not be written: $uri", e)
            } catch (e: SecurityException) {
                throw DatabaseFileException("There is no permission to write the file any more: $uri", e)
            }
        }
    }

    override suspend fun displayName(uri: String): String? = withContext(dispatcher) {
        try {
            resolver.query(Uri.parse(uri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use(::readDisplayName)
        } catch (_: SecurityException) {
            // A missing name is not worth failing a call over: the screen falls back to the uri
            null
        }
    }

    override suspend fun keepAccess(uri: String) {
        withContext(dispatcher) {
            try {
                resolver.takePersistableUriPermission(
                    Uri.parse(uri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (e: SecurityException) {
                // The picker did not offer a persistable grant. The database still works for as long as
                // this process lives, so this is a note for the log rather than a failure of the call
                throw DatabaseFileException("The access to the file cannot be kept: $uri", e)
            }
        }
    }

    private fun readDisplayName(cursor: Cursor): String? =
        if (cursor.moveToFirst()) {
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0) cursor.getString(column) else null
        } else {
            null
        }
}
