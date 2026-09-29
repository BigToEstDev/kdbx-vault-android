package ru.kino.dev.ffi

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.kino.dev.core.CreatedDatabase
import ru.kino.dev.core.MergeSummary
import ru.kino.dev.core.NativeCore
import ru.kino.dev.core.NewDatabase
import ru.kino.dev.core.OpenedDatabase
import ru.kino.dev.core.PasswordOptions
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [NativeCore] on top of the jni bridge.
 *
 * The native side is synchronous by design - there is no async across the jni boundary and no callbacks
 * back into Kotlin - so this is where the work leaves the caller's thread. Argon2 and file io block, and
 * they block a dispatcher thread instead of the main one.
 */
@Singleton
internal class NativeCoreFfi @Inject constructor() : NativeCore {

    // Not injected: a default value on an @Inject constructor generates a second constructor and Dagger
    // refuses that. A qualified dispatcher provider is worth adding once something else needs one too
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO

    override suspend fun buildInfo(): String = withContext(dispatcher) {
        PassFfi.buildInfo()
    }

    override suspend fun createDatabase(database: NewDatabase): CreatedDatabase =
        withContext(dispatcher) {
            val answer = FfiBinaryCall.call(
                command = FfiCommands.CREATE_DATABASE,
                argsJson = encode(NewDatabaseDto.serializer(), database.toDto()),
            )
            val loaded = FfiEnvelope.unwrap(answer.envelope, KdbxLoadedDto.serializer())

            CreatedDatabase(database = loaded.toDomain(), bytes = answer.payload)
        }

    override suspend fun openDatabase(
        dbKey: String,
        bytes: ByteArray,
        password: String?,
        fileName: String?,
        keyFile: String?,
    ): OpenedDatabase = withContext(dispatcher) {
        val args = ReadKdbxDto(
            dbKey = dbKey,
            password = password,
            keyFileName = keyFile,
            fileName = fileName,
        )
        val answer = FfiBinaryCall.call(
            command = FfiCommands.READ_DATABASE,
            argsJson = encode(ReadKdbxDto.serializer(), args),
            input = bytes,
        )

        FfiEnvelope.unwrap(answer.envelope, KdbxLoadedDto.serializer()).toDomain()
    }

    override suspend fun saveDatabase(dbKey: String): ByteArray = withContext(dispatcher) {
        val answer = FfiBinaryCall.call(
            command = FfiCommands.SAVE_DATABASE,
            argsJson = encode(DbKeyDto.serializer(), DbKeyDto(dbKey)),
        )
        // The envelope is read first: a refusal has no bytes behind it, and it has to travel as the
        // failure it is rather than as an empty file
        FfiEnvelope.unwrap(answer.envelope, KdbxSavedDto.serializer())

        answer.payload
    }

    override suspend fun closeDatabase(dbKey: String) {
        withContext(dispatcher) {
            val envelope = PassFfi.invoke(
                FfiCommands.CLOSE_DATABASE,
                encode(DbKeyDto.serializer(), DbKeyDto(dbKey)),
            )
            FfiEnvelope.unwrap(envelope, DoneDto.serializer())
        }
    }

    override suspend fun rootGroupUuid(dbKey: String): String = withContext(dispatcher) {
        val envelope = PassFfi.invoke(
            FfiCommands.GROUPS_SUMMARY_DATA,
            encode(DbKeyDto.serializer(), DbKeyDto(dbKey)),
        )
        FfiEnvelope.unwrap(envelope, GroupTreeDto.serializer()).rootUuid
    }

    override suspend fun addEntry(dbKey: String, parentGroupUuid: String, title: String) {
        withContext(dispatcher) {
            val blank = PassFfi.invoke(
                FfiCommands.NEW_ENTRY_FORM_DATA_BY_ID,
                encode(
                    NewEntryFormDto.serializer(),
                    NewEntryFormDto(
                        dbKey = dbKey,
                        entryTypeUuid = LOGIN_ENTRY_TYPE_UUID,
                        parentGroupUuid = parentGroupUuid,
                    ),
                ),
            )
            // The form comes back complete - uuid, times, the fields of the type - and goes back whole.
            // Only the title is ours to set: everything else is the core's idea of what this type is
            val form = FfiEnvelope.unwrap(blank, EntryFormDataDto.serializer()).copy(title = title)

            val envelope = PassFfi.invoke(
                FfiCommands.INSERT_ENTRY_FROM_FORM_DATA,
                encode(EntryFormArgsDto.serializer(), EntryFormArgsDto(dbKey = dbKey, formData = form)),
            )
            FfiEnvelope.unwrap(envelope, DoneDto.serializer())
        }
    }

    override suspend fun verifyFileChecksum(dbKey: String, bytes: ByteArray) {
        withContext(dispatcher) {
            val answer = FfiBinaryCall.call(
                command = FfiCommands.VERIFY_DB_FILE_CHECKSUM,
                argsJson = encode(DbKeyDto.serializer(), DbKeyDto(dbKey)),
                input = bytes,
            )
            FfiEnvelope.unwrap(answer.envelope, DoneDto.serializer())
        }
    }

    override suspend fun mergeDatabase(dbKey: String, bytes: ByteArray): MergeSummary =
        withContext(dispatcher) {
            val answer = FfiBinaryCall.call(
                command = FfiCommands.MERGE_DATABASE,
                argsJson = encode(DbKeyDto.serializer(), DbKeyDto(dbKey)),
                input = bytes,
            )
            FfiEnvelope.unwrap(answer.envelope, MergeResultDto.serializer()).toDomain()
        }

    override suspend fun generatePassword(options: PasswordOptions): String = withContext(dispatcher) {
        val args = FfiEnvelope.json.encodeToString(
            PasswordOptionsDto.serializer(),
            options.toDto(),
        )
        val envelope = PassFfi.invoke(FfiCommands.GENERATE_PASSWORD, args)
        FfiEnvelope.unwrap(envelope, GeneratedPasswordDto.serializer()).password
    }

    private fun <T> encode(serializer: kotlinx.serialization.SerializationStrategy<T>, value: T): String =
        FfiEnvelope.json.encodeToString(serializer, value)

    private fun NewDatabase.toDto() = NewDatabaseDto(
        databaseFileName = dbKey,
        fileName = fileName,
        databaseName = databaseName,
        databaseDescription = databaseDescription,
        password = password,
        keyFileName = keyFile,
        // Argon2id and AES-256 are what a new KeePass database is expected to be. The parameters are the
        // core's defaults for now; what they should be on a phone is decided after measuring on a device
        kdf = KdfDto(
            algorithm = ARGON2ID,
            memory = ARGON2_MEMORY_BYTES,
            iterations = ARGON2_ITERATIONS,
            parallelism = ARGON2_PARALLELISM,
        ),
        cipherId = AES_256,
    )

    private fun KdbxLoadedDto.toDomain() = OpenedDatabase(
        dbKey = dbKey,
        databaseName = databaseName,
        fileName = fileName,
        keyFile = keyFileName,
    )

    private fun MergeResultDto.toDomain() = MergeSummary(
        addedGroups = addedGroups.size,
        updatedGroups = updatedGroups.size,
        movedGroups = parentChangedGroups.size,
        addedEntries = addedEntries.size,
        updatedEntries = updatedEntries.size,
        movedEntries = parentChangedEntries.size,
        deletedGroups = permanentlyDeletedGroups.size,
        deletedEntries = permanentlyDeletedEntries.size,
        metaDataChanged = metaDataChanged,
        mergeDone = mergeDone,
        differentDatabases = differentDatabases,
    )

    private fun PasswordOptions.toDto() = PasswordOptionsDto(
        length = length,
        numbers = numbers,
        lowercaseLetters = lowercaseLetters,
        uppercaseLetters = uppercaseLetters,
        symbols = symbols,
        spaces = spaces,
        excludeSimilarCharacters = excludeSimilarCharacters,
        strict = strict,
    )

    private companion object {
        // Tags of the core's enums, as its json spells them
        const val ARGON2ID = "Argon2id"
        const val AES_256 = "Aes256"

        // Argon2id parameters of a new database. Measured on a device, not guessed: with the core's
        // defaults (64 MiB, 10 iterations) deriving the key took about 8 seconds - on opening and on
        // every save alike, because KeePass draws a new master seed before each write.
        //
        // Iterations are what came down, memory stayed. Memory is what makes guessing expensive on a
        // gpu or on purpose built hardware; iterations only cost the cpu, and they cost it on the one
        // machine that is waiting - the phone in a hand. 64 MiB with 3 passes is one of the two
        // configurations RFC 9106 recommends.
        //
        // These are the parameters of a database this app creates. A database brought from elsewhere
        // keeps its own, which is why changing them needs the settings screen, not this constant -
        // plan/todo/android/kdf-and-perf.md.
        const val ARGON2_MEMORY_BYTES = 67_108_864L
        const val ARGON2_ITERATIONS = 3L
        const val ARGON2_PARALLELISM = 2

        // The standard "Login" entry type of the core. Its uuid is fixed - the type is built in, not
        // stored in the database - and it is the type an entry gets when nothing else is chosen
        const val LOGIN_ENTRY_TYPE_UUID = "ffef5f51-7efc-4373-9eb5-382d5b501768"
    }
}
