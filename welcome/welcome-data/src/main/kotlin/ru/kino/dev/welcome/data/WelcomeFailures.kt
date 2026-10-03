package ru.kino.dev.welcome.data

import ru.kino.dev.core.CoreException
import ru.kino.dev.core.CredentialLimits
import ru.kino.dev.core.DatabaseFileException
import ru.kino.dev.welcome.PickedFile
import ru.kino.dev.welcome.WelcomeFailure
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs a call of a `welcome` repository so that it fails with a [WelcomeFailure] and nothing else.
 *
 * The kinds of the core are mapped by the table in pass-docs, `docs/android/screens/welcome/decisions.md`,
 * "Ошибки"; a failure already mapped passes as it is; anything else is [WelcomeFailure.Unexpected].
 * Cancellation passes untouched - it is not a failure of the call, and mapping it would keep a coroutine
 * running that was told to stop.
 */
internal suspend fun <T> welcomeCall(block: suspend () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: WelcomeFailure) {
        throw e
    } catch (e: CoreException) {
        throw e.toWelcomeFailure()
    } catch (e: Exception) {
        throw WelcomeFailure.Unexpected(e)
    }

/**
 * Runs an access to a picked file, turning "cannot reach it" into [WelcomeFailure.FileUnreachable] for
 * that [file].
 *
 * Only the storage can tell this case: the core reads bytes from memory and never sees the file, so
 * nothing it reports means the file is missing.
 */
internal inline fun <T> accessing(file: PickedFile, block: () -> T): T =
    try {
        block()
    } catch (e: DatabaseFileException) {
        throw WelcomeFailure.FileUnreachable(file, e)
    }

internal fun CoreException.toWelcomeFailure(): WelcomeFailure = when (kind) {
    in CREDENTIALS -> WelcomeFailure.InvalidCredentials(this)
    in UNSUPPORTED -> WelcomeFailure.UnsupportedFile(this)
    in CORRUPTED -> WelcomeFailure.CorruptedFile(this)
    in KEY_FILE_TOO_LARGE -> WelcomeFailure.KeyFileTooLarge(this)
    // DbKeyNotFound, Decryption, UnexpectedError, the bridge's own, PasswordTooLong - the password field
    // does not let a longer one through, so the core refusing it is a bug as well
    else -> WelcomeFailure.Unexpected(this)
}

private val CREDENTIALS = setOf("InvalidCredentials")

private val UNSUPPORTED = setOf(
    "InvalidKeePassFile",
    "OldUnsupportedKeePass1",
    "OldUnsupportedKdbxFormat",
    "UnsupportedCipher",
    "UnsupportedKdfAlgorithm",
    "SupportedOnlyArgon2dKdfAlgorithm",
)

// Io is not expected on a read from bytes; if it comes anyway, the bytes are what failed (Step 30)
private val CORRUPTED = setOf("HeaderCorrupted", "ContentCorrupted", "XmlParsingFailed", "Io")

private val KEY_FILE_TOO_LARGE = setOf(CredentialLimits.KIND_KEY_FILE_TOO_LARGE)
