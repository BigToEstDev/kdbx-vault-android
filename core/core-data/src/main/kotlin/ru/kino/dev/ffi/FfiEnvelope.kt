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

/** An empty json object, for commands that take no arguments. */
internal val NO_ARGUMENTS: JsonObject = JsonObject(emptyMap())
