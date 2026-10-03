package ru.kino.dev.core

/**
 * The file a database lives in, as the rest of the app sees it.
 *
 * The native core never touches files: it is handed bytes and gives bytes back. Everything about where
 * those bytes come from - a document picked by the user, permissions that have to survive a restart -
 * lives behind this interface, and its implementation is the only place that knows about Android storage.
 */
interface DatabaseFiles {

    /** Reads the whole file. Databases are small enough to hold in memory, and the core needs all of it. */
    suspend fun read(uri: String): ByteArray

    /**
     * Reads at most [maxBytes] of the file, from its start.
     *
     * For files the user picks that are not databases - a key file may be anything, and a video picked by
     * mistake must not end up in memory whole. Asking for one byte more than allowed tells a file of the
     * limit from a larger one.
     */
    suspend fun readAtMost(uri: String, maxBytes: Int): ByteArray

    /**
     * Size of the file in bytes, or null when the provider does not tell.
     *
     * A hint, not a fact: a provider may not know the size of a document it streams, and the file can
     * change between this call and the read. Whatever depends on the size checks the bytes too.
     */
    suspend fun size(uri: String): Long?

    /**
     * Whether the file has nothing in it.
     *
     * "Save as" hands back a new, empty document; one with something in it is an existing file the user
     * picked to write over - possibly the key of another database. Asked of the content rather than of
     * [size], which a provider may not know.
     */
    suspend fun isEmpty(uri: String): Boolean

    /**
     * Deletes the file, as far as the provider allows. Returns whether it is gone.
     *
     * Best effort by design: not every provider can delete, and the caller is cleaning up after a failure
     * it is already reporting - a second failure on top of it would only hide the first.
     */
    suspend fun delete(uri: String): Boolean

    /**
     * Replaces the contents of the file.
     *
     * Truncating, always: the core does not cut a writer short, so a database that got smaller would
     * otherwise keep the tail of the older one after its end.
     */
    suspend fun writeReplacing(uri: String, bytes: ByteArray)

    /**
     * The name to show for the file, or null when the provider does not tell.
     *
     * Asked for explicitly because a uri has no readable name inside it - what looks like a file name in
     * one is percent encoded, and in another it is a document id.
     */
    suspend fun displayName(uri: String): String?

    /**
     * Asks to keep access to the file after a restart.
     *
     * Without it the uri dies with the process that picked it, and the next launch can only offer to pick
     * the file again. So a refusal is a failure, not a note for the log: a database remembered without
     * kept access would not open after a restart.
     *
     * @param forWriting a database is written back, a key file is only read - and a provider may grant no
     * more than reading
     * @throws DatabaseFileException the picker did not grant access that can be kept
     */
    suspend fun keepAccess(uri: String, forWriting: Boolean)
}

/** A file could not be read or written: no permission any more, the document is gone, the provider failed. */
class DatabaseFileException(message: String, cause: Throwable? = null) : Exception(message, cause)
