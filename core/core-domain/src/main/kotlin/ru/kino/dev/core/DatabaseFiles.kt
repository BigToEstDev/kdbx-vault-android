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
     * the file again.
     */
    suspend fun keepAccess(uri: String)
}

/** A file could not be read or written: no permission any more, the document is gone, the provider failed. */
class DatabaseFileException(message: String, cause: Throwable? = null) : Exception(message, cause)
