package ru.kino.dev.ffi

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.kino.dev.core.NativeCore
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

    override suspend fun generatePassword(options: PasswordOptions): String = withContext(dispatcher) {
        val args = FfiEnvelope.json.encodeToString(
            PasswordOptionsDto.serializer(),
            options.toDto(),
        )
        val envelope = PassFfi.invoke(COMMAND_GENERATE_PASSWORD, args)
        FfiEnvelope.unwrap(envelope, GeneratedPasswordDto.serializer()).password
    }

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
        const val COMMAND_GENERATE_PASSWORD = "generate_password"
    }
}
