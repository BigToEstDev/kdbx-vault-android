package ru.kino.dev.ffi

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import ru.kino.dev.core.CoreException

/**
 * The envelope every native call comes back in.
 *
 * A failing operation is data, not an exception: a wrong password or a name already taken are ordinary
 * outcomes. The bridge only throws when it is itself broken - a panic, or json it could not build - and
 * that arrives as a [RuntimeException] from jni, not through here.
 */
internal object FfiEnvelope {

    /**
     * Lenient on unknown keys on purpose: the core may start returning a field the app does not know yet,
     * and an older app dropping it is better than an older app crashing.
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Reads a successful payload out of the envelope, or throws [CoreException] with the kind and the
     * message the core reported.
     */
    fun <T> unwrap(envelope: String, payload: DeserializationStrategy<T>): T {
        val parsed = json.decodeFromString(Envelope.serializer(), envelope)

        parsed.err?.let { throw CoreException(kind = it.kind, message = it.message) }

        val ok = parsed.ok ?: throw CoreException(
            kind = MALFORMED_ENVELOPE,
            message = "The answer had neither an 'ok' nor an 'err': $envelope",
        )

        return json.decodeFromJsonElement(payload, ok)
    }

    @Serializable
    private data class Envelope(
        val ok: JsonElement? = null,
        val err: Failure? = null,
    )

    @Serializable
    private data class Failure(
        val kind: String,
        val message: String,
    )

    /** Kind reported when the envelope itself does not make sense - a bug in the bridge, not a failure. */
    const val MALFORMED_ENVELOPE: String = "MalformedEnvelope"
}

/** Arguments of `generate_password`, shaped exactly as the core's `PasswordGenerationOptions`. */
@Serializable
internal data class PasswordOptionsDto(
    val length: Int,
    val numbers: Boolean,
    @SerialName("lowercase_letters") val lowercaseLetters: Boolean,
    @SerialName("uppercase_letters") val uppercaseLetters: Boolean,
    val symbols: Boolean,
    val spaces: Boolean,
    @SerialName("exclude_similar_characters") val excludeSimilarCharacters: Boolean,
    val strict: Boolean,
)

/** Result of `generate_password`. */
@Serializable
internal data class GeneratedPasswordDto(val password: String)

/**
 * Arguments of `create_and_write_to_writer`.
 *
 * The only place where the argument shape is the core's own rather than ours: `NewDatabase` keeps its
 * fields private and can only be built through serde, so its json is the contract. Hence
 * `database_file_name` for what everything else calls `db_key`, and the nested kdf object.
 */
@Serializable
internal data class NewDatabaseDto(
    @SerialName("database_file_name") val databaseFileName: String,
    @SerialName("file_name") val fileName: String?,
    @SerialName("database_name") val databaseName: String,
    @SerialName("database_description") val databaseDescription: String?,
    val password: String?,
    @SerialName("key_file_name") val keyFileName: String?,
    val kdf: KdfDto,
    @SerialName("cipher_id") val cipherId: String,
)

/**
 * The key derivation function of a new database.
 *
 * `algorithm` is the tag of the core's enum, the rest are its parameters. An empty salt means "generate
 * one": the core fills it while creating the database.
 */
@Serializable
internal data class KdfDto(
    val algorithm: String,
    val memory: Long,
    val iterations: Long,
    val parallelism: Int,
    val salt: List<Byte> = emptyList(),
)

/** Result of `read_kdbx` and of `create_and_write_to_writer`: an open database. */
@Serializable
internal data class KdbxLoadedDto(
    @SerialName("db_key") val dbKey: String,
    @SerialName("database_name") val databaseName: String,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("key_file_name") val keyFileName: String? = null,
)

/** Result of `save_kdbx_to_writer`. The bytes of the file travel beside the envelope, not inside it. */
@Serializable
internal data class KdbxSavedDto(
    @SerialName("db_key") val dbKey: String,
    @SerialName("database_name") val databaseName: String,
)

/** Arguments of every command that works on an open database. */
@Serializable
internal data class DbKeyDto(@SerialName("db_key") val dbKey: String)

/** Arguments of `read_kdbx`. The bytes of the file are passed beside them. */
@Serializable
internal data class ReadKdbxDto(
    @SerialName("db_key") val dbKey: String,
    val password: String?,
    @SerialName("key_file_name") val keyFileName: String?,
    @SerialName("file_name") val fileName: String?,
)

/** Result of `close_kdbx`. */
@Serializable
internal data class ClosedDto(val closed: Boolean)

/** An empty json object, for commands that take no arguments. */
internal val NO_ARGUMENTS: JsonObject = JsonObject(emptyMap())
