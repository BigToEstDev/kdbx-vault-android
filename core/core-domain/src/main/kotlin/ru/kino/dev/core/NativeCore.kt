package ru.kino.dev.core

/**
 * The kdbx core, as the rest of the app sees it.
 *
 * The implementation is native (Rust behind jni) and lives in the data layer; nothing above it needs to
 * know that. Calls suspend because the core is synchronous and some of its work is genuinely slow -
 * opening a database runs Argon2 - so the data layer moves it off the main thread.
 *
 * Step 22 declares only what the end to end check needs. The v1 set of operations is Step 23.
 */
interface NativeCore {

    /** Version of the native bridge and the time it was built, for the log on start. */
    suspend fun buildInfo(): String

    /**
     * Creates a new database and returns it together with the bytes to write into the chosen file.
     *
     * The core does not touch the file: the app owns it through SAF, so the contents come back here and
     * are written by the caller. [NewDatabase.dbKey] is the uri of that file and identifies the database
     * in every later call.
     */
    suspend fun createDatabase(database: NewDatabase): CreatedDatabase

    /**
     * Opens a database from the bytes of its file. The database stays open in the native library until
     * [closeDatabase], and every later call refers to it by [dbKey].
     *
     * Reading the file is the caller's: this interface is about the core, not about storage, and on
     * Android the bytes come from SAF.
     *
     * @param dbKey uri of the file, the identity of the database
     * @param bytes contents of the file
     * @param fileName name to show; passed in because a SAF uri holds no readable name
     * @param keyFile path to a key file in the app's own storage, when the database needs one
     */
    suspend fun openDatabase(
        dbKey: String,
        bytes: ByteArray,
        password: String?,
        fileName: String?,
        keyFile: String? = null,
    ): OpenedDatabase

    /**
     * Serialises the open database and returns the bytes to write into its file.
     *
     * Writing is the caller's: the stream has to be opened truncating (`"wt"`), because the core does not
     * cut a writer short and a database that got smaller would keep the tail of the older one.
     */
    suspend fun saveDatabase(dbKey: String): ByteArray

    /** Forgets the database: its contents and its key leave the process. */
    suspend fun closeDatabase(dbKey: String)

    /**
     * Uuid of the root group, the parent every entry ultimately hangs under.
     *
     * A narrow question asked of a wide answer: the core hands back the whole tree, and the callers that
     * need the tree itself get their own method when a screen needs one.
     */
    suspend fun rootGroupUuid(dbKey: String): String

    /**
     * Adds an entry with [title] to the group [parentGroupUuid].
     *
     * The form of an entry belongs to the core - which fields a type has, in which sections - so the only
     * way to make one is to ask for a blank form and hand it back filled. That round trip stays inside
     * the implementation: nothing above this interface has a reason to know the shape of the form.
     */
    suspend fun addEntry(dbKey: String, parentGroupUuid: String, title: String)

    /**
     * Compares [bytes] with the checksum taken when the file was last read or written.
     *
     * Throws [CoreException] with kind `DbFileContentChangeDetected` when they differ - somebody else
     * wrote the file, and that is an ordinary outcome the ui answers with a merge, not a breakage.
     */
    suspend fun verifyFileChecksum(dbKey: String, bytes: ByteArray)

    /**
     * Merges the database in [bytes] into the open one and reports what changed.
     *
     * Works on the stored composite key, so no password is asked for - which is also why a file
     * re-encrypted elsewhere comes back as `MergeFailedCredentialsChanged` rather than as a wrong
     * password. There is no undo: the database in memory changes at once, and the file is only touched
     * when the caller saves afterwards.
     */
    suspend fun mergeDatabase(dbKey: String, bytes: ByteArray): MergeSummary

    /** Generates a password with the core's generator. */
    suspend fun generatePassword(options: PasswordOptions = PasswordOptions()): String
}

/**
 * A database to create.
 *
 * Defaults are the core's: Argon2id and AES-256, which is what a new KeePass database is expected to be.
 * The kdf parameters are deliberately not here - they belong to a decision about how long unlocking may
 * take on a phone, not to a call site.
 */
data class NewDatabase(
    /** Uri of the file the database will live in; also its identity in the core */
    val dbKey: String,
    /** Name to show, taken from the document rather than from the uri */
    val fileName: String?,
    val databaseName: String,
    val databaseDescription: String? = null,
    val password: String?,
    /** Path to a key file in the app's own storage, when the database is to have one */
    val keyFile: String? = null,
)

/** A database the core has just created, and the bytes to write into its file. */
data class CreatedDatabase(
    val database: OpenedDatabase,
    val bytes: ByteArray,
) {
    // ByteArray in a data class compares by identity, which is never what a caller means
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is CreatedDatabase && database == other.database && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * database.hashCode() + bytes.contentHashCode()
}

/** An open database, as the core describes it. */
data class OpenedDatabase(
    val dbKey: String,
    val databaseName: String,
    /**
     * Name of the file. Unreliable after unlocking: the core derives it from [dbKey] there, and a SAF uri
     * has no readable name in it - so the app keeps its own and ignores this one. See the note in
     * pass-docs, plan/todo/core/rust-core-bugs.md.
     */
    val fileName: String?,
    val keyFile: String?,
)

/**
 * What a merge changed, as counts.
 *
 * The core also names every group and entry it touched. The names are left there on purpose: a summary is
 * what a screen shows, and carrying the contents of a database around in a result object is how they end
 * up in a log. A screen that lists the changes asks for them when it exists.
 *
 * [differentDatabases] is only known after the merge has happened, so a warning about having merged two
 * unrelated databases is shown as a fact - the way back is closing without saving.
 */
data class MergeSummary(
    val addedGroups: Int,
    val updatedGroups: Int,
    val movedGroups: Int,
    val addedEntries: Int,
    val updatedEntries: Int,
    val movedEntries: Int,
    val deletedGroups: Int,
    val deletedEntries: Int,
    val metaDataChanged: Boolean,
    val mergeDone: Boolean,
    val differentDatabases: Boolean,
)

/**
 * Options of the password generator. The defaults are the core's own defaults, repeated here so a caller
 * that wants a plain password does not have to know them.
 */
data class PasswordOptions(
    val length: Int = 16,
    val numbers: Boolean = true,
    val lowercaseLetters: Boolean = true,
    val uppercaseLetters: Boolean = true,
    val symbols: Boolean = false,
    val spaces: Boolean = false,
    val excludeSimilarCharacters: Boolean = true,
    /** When true every requested character class must appear in the result. */
    val strict: Boolean = true,
)

/**
 * A failure reported by the core or by the bridge.
 *
 * [kind] is the name of the core's error variant (`NotFound`, `DbKeyNotFound`, …) or one of the bridge's
 * own (`UnknownCommand`, `InvalidArguments`). It is what the ui should branch on; [message] is for the
 * log and for the rare case where there is nothing better to show.
 *
 * This is deliberately an exception and not a sealed result type: almost every caller wants to let the
 * failure travel up to the screen that can show it, and a `Result` at every level would be noise. The
 * screens that do handle a specific failure match on [kind].
 */
class CoreException(
    val kind: String,
    override val message: String,
) : Exception("$kind: $message")
