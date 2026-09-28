package ru.kino.dev.core

/**
 * Databases, as screens work with them.
 *
 * This is where the two halves meet: [NativeCore] holds the database and knows nothing about files,
 * [DatabaseFiles] holds the file and knows nothing about databases. Every method here is one user
 * intention - create, open, save, close - rather than one call to either of them.
 */
interface DatabaseRepository {

    /**
     * Creates a database in an already chosen, empty file.
     *
     * The file is written before this returns, so a failure to write leaves nothing open in the core: a
     * database the app believes in but the file system does not is worse than no database at all.
     */
    suspend fun create(
        uri: String,
        databaseName: String,
        password: String,
    ): OpenedDatabase

    /** Opens the database in the file, reading it through [DatabaseFiles]. */
    suspend fun open(uri: String, password: String): OpenedDatabase

    /** Writes the open database back into its file. */
    suspend fun save(dbKey: String)

    /** Closes the database: its contents and its key leave the process. */
    suspend fun close(dbKey: String)
}
