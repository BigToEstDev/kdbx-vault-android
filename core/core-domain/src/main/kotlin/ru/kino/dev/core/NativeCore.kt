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

    /** Generates a password with the core's generator. */
    suspend fun generatePassword(options: PasswordOptions = PasswordOptions()): String
}

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
