package ru.kino.dev.vault

import ru.kino.dev.core.MergeSummary

/**
 * The open database, as `vault` works with it - from the moment `welcome` hands over its `dbKey`.
 *
 * Opening and creating are not here: they belong to `welcome`, where the database is not open yet. What
 * crosses the border is the `dbKey` alone, the uri of the file and the identity of the database in the
 * core (pass-docs, `docs/android/screens/welcome/decisions.md`, "Граница с vault").
 */
interface VaultDatabaseRepository {

    /** Writes the open database back into its file. */
    suspend fun save(dbKey: String)

    /** Closes the database: its contents and its key leave the process. */
    suspend fun close(dbKey: String)

    /**
     * Whether the file holds something other than what this app last read or wrote.
     *
     * True means somebody else saved over it - another device through the same cloud folder, the desktop
     * KeePass. Saving on top would throw their work away, so this is the question asked before a save and
     * answered with [merge].
     */
    suspend fun hasChangedElsewhere(dbKey: String): Boolean

    /**
     * Merges what the file holds now into the open database and reports what changed.
     *
     * In memory only: the merged database still has to be saved, and until it is, the file is the way the
     * other side left it. There is no undo, so the ui asks before calling this, not after.
     */
    suspend fun merge(dbKey: String): MergeSummary
}
