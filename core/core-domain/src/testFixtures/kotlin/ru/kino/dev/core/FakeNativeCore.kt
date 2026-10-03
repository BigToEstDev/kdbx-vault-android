package ru.kino.dev.core

/**
 * [NativeCore] in memory, for tests of the repositories built on it.
 *
 * Every call is written to [journal] as `"<method> <dbKey>"` - share the journal with a [FakeDatabaseFiles]
 * to check the order of calls across both. A failure is set per method in [failures] and thrown each time
 * that method is called, after it is journaled.
 */
class FakeNativeCore(val journal: MutableList<String> = mutableListOf()) : NativeCore {

    /** Method name to the failure it throws: a [CoreException] for the core's own, anything else for a bug. */
    val failures = mutableMapOf<String, Throwable>()

    /** Databases the core holds, by dbKey. */
    val opened = mutableMapOf<String, OpenedDatabase>()

    /** Databases whose file no longer matches the checksum - written by somebody else. */
    val changedElsewhere = mutableSetOf<String>()

    /** The name a database opened or unlocked here reports from inside. */
    var databaseName = "Passwords"

    /** What [createDatabase] and [saveDatabase] give back to be written. */
    var databaseBytes = byteArrayOf(0x03, 0xD9.toByte(), 0xA2.toByte(), 0x9A.toByte())

    var generatedKeyFile = "<KeyFile/>".toByteArray()

    var mergeSummary = MergeSummary(
        addedGroups = 0,
        updatedGroups = 0,
        movedGroups = 0,
        addedEntries = 1,
        updatedEntries = 0,
        movedEntries = 0,
        deletedGroups = 0,
        deletedEntries = 0,
        metaDataChanged = false,
        mergeDone = true,
        differentDatabases = false,
    )

    /** The last [NewDatabase] asked for. */
    var created: NewDatabase? = null

    /** The bytes handed to [openDatabase], [verifyFileChecksum] and [mergeDatabase], last one wins. */
    var bytesReceived: ByteArray? = null

    /** The password of the last open, unlock or create. */
    var passwordReceived: String? = null

    /**
     * Every key file handed in, as the same instances: a test checks after the call that the caller wiped
     * them.
     */
    val keyFilesReceived = mutableListOf<KeyFile>()

    override suspend fun buildInfo(): String = "fake"

    override suspend fun createDatabase(database: NewDatabase): CreatedDatabase {
        step("createDatabase", database.dbKey)
        created = database
        passwordReceived = database.password
        database.keyFile?.let { keyFilesReceived += it }

        val opened = OpenedDatabase(
            dbKey = database.dbKey,
            databaseName = database.databaseName,
            fileName = database.fileName,
            keyFileName = database.keyFile?.name,
        )
        this.opened[database.dbKey] = opened
        return CreatedDatabase(database = opened, bytes = databaseBytes.copyOf())
    }

    override suspend fun openDatabase(
        dbKey: String,
        bytes: ByteArray,
        password: String?,
        fileName: String?,
        keyFile: KeyFile?,
    ): OpenedDatabase {
        step("openDatabase", dbKey)
        bytesReceived = bytes.copyOf()
        passwordReceived = password
        keyFile?.let { keyFilesReceived += it }

        return OpenedDatabase(dbKey, databaseName, fileName, keyFile?.name).also { opened[dbKey] = it }
    }

    override suspend fun unlockDatabase(dbKey: String, password: String?, keyFile: KeyFile?): OpenedDatabase {
        step("unlockDatabase", dbKey)
        passwordReceived = password
        keyFile?.let { keyFilesReceived += it }

        return opened[dbKey] ?: throw CoreException("DbKeyNotFound", "not open: $dbKey")
    }

    override suspend fun saveDatabase(dbKey: String): ByteArray {
        step("saveDatabase", dbKey)
        return databaseBytes.copyOf()
    }

    override suspend fun closeDatabase(dbKey: String) {
        step("closeDatabase", dbKey)
        opened.remove(dbKey)
    }

    override suspend fun verifyFileChecksum(dbKey: String, bytes: ByteArray) {
        step("verifyFileChecksum", dbKey)
        bytesReceived = bytes.copyOf()
        if (dbKey in changedElsewhere) throw CoreException("DbFileContentChangeDetected", "changed: $dbKey")
    }

    override suspend fun mergeDatabase(dbKey: String, bytes: ByteArray): MergeSummary {
        step("mergeDatabase", dbKey)
        bytesReceived = bytes.copyOf()
        return mergeSummary
    }

    override suspend fun generatePassword(options: PasswordOptions): String {
        step("generatePassword", "")
        return "x".repeat(options.length)
    }

    override suspend fun generateKeyFile(): ByteArray {
        step("generateKeyFile", "")
        return generatedKeyFile.copyOf()
    }

    private fun step(method: String, dbKey: String) {
        journal += "$method $dbKey".trimEnd()
        failures[method]?.let { throw it }
    }
}
