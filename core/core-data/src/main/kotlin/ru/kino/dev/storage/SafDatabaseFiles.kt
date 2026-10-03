package ru.kino.dev.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.kino.dev.core.DatabaseFileException
import ru.kino.dev.core.DatabaseFiles
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
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

    override suspend fun read(uri: String): ByteArray = reading(uri) { it.readBytes() }

    override suspend fun readAtMost(uri: String, maxBytes: Int): ByteArray =
        reading(uri) { it.readUpTo(maxBytes) }

    override suspend fun size(uri: String): Long? = withContext(dispatcher) {
        try {
            resolver.query(Uri.parse(uri), arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use(::readSize)
        } catch (_: SecurityException) {
            // Unknown rather than a failure: the size is a hint, and the read that follows reports a
            // missing permission properly
            null
        }
    }

    override suspend fun isEmpty(uri: String): Boolean = reading(uri) { it.read() == END_OF_STREAM }

    override suspend fun delete(uri: String): Boolean = withContext(dispatcher) {
        try {
            DocumentsContract.deleteDocument(resolver, Uri.parse(uri))
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: UnsupportedOperationException) {
            // The provider does not delete at all - the file stays, and that is the answer
            false
        } catch (_: IllegalArgumentException) {
            // Not a document uri this provider recognises
            false
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

    override suspend fun keepAccess(uri: String, forWriting: Boolean) {
        withContext(dispatcher) {
            val flags = if (forWriting) {
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            } else {
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            try {
                resolver.takePersistableUriPermission(Uri.parse(uri), flags)
            } catch (e: SecurityException) {
                // The picker did not offer a persistable grant. The file works until the process dies, and
                // a database remembered on top of that would not open after a restart - so this fails
                throw DatabaseFileException("The access to the file cannot be kept: $uri", e)
            }
        }
    }

    // Every way a read can fail is the same failure for the caller: the file is not reachable now
    private suspend fun <T> reading(uri: String, block: (InputStream) -> T): T = withContext(dispatcher) {
        try {
            resolver.openInputStream(Uri.parse(uri))
                ?.use(block)
                ?: throw DatabaseFileException("The file could not be opened for reading: $uri")
        } catch (e: IOException) {
            throw DatabaseFileException("The file could not be read: $uri", e)
        } catch (e: SecurityException) {
            throw DatabaseFileException("There is no permission to read the file any more: $uri", e)
        }
    }

    // InputStream.readNBytes would do, but it is api 33 and the app starts at 29
    private fun InputStream.readUpTo(maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var left = maxBytes
        while (left > 0) {
            val read = read(buffer, 0, minOf(buffer.size, left))
            if (read == END_OF_STREAM) break
            out.write(buffer, 0, read)
            left -= read
        }
        return out.toByteArray()
    }

    private fun readDisplayName(cursor: Cursor): String? =
        if (cursor.moveToFirst()) {
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0) cursor.getString(column) else null
        } else {
            null
        }

    // A provider may have the column and still leave it null when it does not know
    private fun readSize(cursor: Cursor): Long? =
        if (cursor.moveToFirst()) {
            val column = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (column >= 0 && !cursor.isNull(column)) cursor.getLong(column) else null
        } else {
            null
        }

    private companion object {
        const val END_OF_STREAM = -1
    }
}
