package ru.kino.dev.welcome

import ru.kino.dev.core.CredentialLimits

/**
 * Every way opening, unlocking or creating a database can fail, as a screen of `welcome` answers it.
 *
 * Six cases, one per answer of the ui - the table is in pass-docs,
 * `docs/android/screens/welcome/decisions.md`, "Ошибки". The repositories of `welcome` throw nothing else:
 * the string kinds of the core stay below the data layer, and whatever is not one of the first five is
 * [Unexpected]. A processor catches this type in one place and branches with an exhaustive `when`.
 *
 * An exception rather than a result type, the way [ru.kino.dev.core.CoreException] is: `kotlin.Result` in
 * `runCatching` would swallow cancellation along with the failures.
 *
 * The message is for the log; the screen shows its own text for each case.
 */
sealed class WelcomeFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /**
     * The password, the key file or both are wrong. The format does not tell which, and the screen does
     * not pretend to: "check the password and the key file", and the password stays in the field.
     */
    class InvalidCredentials(cause: Throwable? = null) :
        WelcomeFailure("The password or the key file does not open the database", cause)

    /**
     * The file is not a database this app can open, and no password will change that - KeePass 1, KDBX 3,
     * an unsupported cipher or kdf, not a kdbx at all. Only "pick another file" makes sense.
     */
    class UnsupportedFile(cause: Throwable? = null) :
        WelcomeFailure("The file is not a database this app can open", cause)

    /**
     * The file is a kdbx, but damaged - or not all of it is there yet: a sync that has not finished looks
     * exactly like this, which is why the screen offers to retry as well as to pick another file.
     */
    class CorruptedFile(cause: Throwable? = null) : WelcomeFailure("The database file is damaged", cause)

    /**
     * A file could not be reached - gone, no permission any more, the provider offline - or access to it
     * could not be kept for the next launch. The remembered database is not forgotten for this: the
     * provider may be back in a minute.
     *
     * @property file which of the files failed, so "pick another" picks the right one
     */
    class FileUnreachable(val file: PickedFile, cause: Throwable? = null) :
        WelcomeFailure("The ${file.name.lowercase()} file cannot be reached", cause)

    /**
     * The key file is larger than [maxBytes] - most likely a video or a photo picked by mistake. Shown under
     * the key file, with the limit in the text; the password stays.
     */
    class KeyFileTooLarge(cause: Throwable? = null) :
        WelcomeFailure("The key file is larger than ${CredentialLimits.KEY_FILE_MAX_SIZE} bytes", cause) {
        val maxBytes: Long get() = CredentialLimits.KEY_FILE_MAX_SIZE
    }

    /** Ours: a bug, or a state that should not happen. "Something went wrong", and the cause to the log. */
    class Unexpected(cause: Throwable? = null) : WelcomeFailure("Something went wrong", cause)
}
