package ru.kino.dev.ffi

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import ru.kino.dev.core.CreatedDatabase
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
            FfiEnvelope.unwrap(envelope, ClosedDto.serializer())
        }
    }

    override suspend fun generatePassword(options: PasswordOptions): String = withContext(dispatcher) {
        val args = FfiEnvelope.json.encodeToString(
            PasswordOptionsDto.serializer(),
            options.toDto(),
        )
        val envelope = PassFfi.invoke(FfiCommands.GENERATE_PASSWORD, args)
        FfiEnvelope.unwrap(envelope, GeneratedPasswordDto.serializer()).password
    }

    override suspend fun probeUnknownCommand(): String = withContext(dispatcher) {
        val envelope = PassFfi.invoke(UNKNOWN_COMMAND, NO_ARGUMENTS.toString())
        // The unwrap throws before it looks at the payload, so what the payload is declared as does not
        // matter; a plain JsonElement says that nothing is expected to come back
        FfiEnvelope.unwrap(envelope, JsonElement.serializer()).toString()
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
        // Deliberately not a command: the dispatcher answers it with the UnknownCommand kind
        const val UNKNOWN_COMMAND = "no_such_command"

        // Tags of the core's enums, as its json spells them
        const val ARGON2ID = "Argon2id"
        const val AES_256 = "Aes256"

        // The core's own defaults, repeated because the arguments have no "use the default" form. 64 MiB
        // and 10 iterations is what KeePass recommends; whether a phone can afford it is measured before
        // release, and the answer becomes a constant here
        const val ARGON2_MEMORY_BYTES = 67_108_864L
        const val ARGON2_ITERATIONS = 10L
        const val ARGON2_PARALLELISM = 2
    }
}
