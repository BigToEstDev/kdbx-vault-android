package ru.kino.dev.core

/**
 * Limits on the credentials a database is closed with.
 *
 * **Must stay equal to the core's** - `KEY_FILE_MAX_SIZE` and `PASSWORD_MAX_CHARS` in
 * `pass-rust-core/src/db/credential_limits.rs`. The core checks them anyway; they are repeated here so the
 * ui can refuse early - before a picked video is read into memory, while a password is still being typed.
 * Kinds of the core's refusals: [KIND_KEY_FILE_TOO_LARGE], [KIND_PASSWORD_TOO_LONG].
 */
object CredentialLimits {

    /**
     * The largest key file, in bytes. Any file can be a key, on the desktop often a photo of a few
     * megabytes; the limit is there to stop a video picked by mistake.
     */
    const val KEY_FILE_MAX_SIZE: Long = 10L * 1024 * 1024

    /**
     * The longest password that can be **set**, in characters. Only when setting one - creating a database,
     * changing its password. Never when opening: KeePass has no such limit, and a database with a longer
     * password must still open.
     */
    const val PASSWORD_MAX_CHARS: Int = 256

    const val KIND_KEY_FILE_TOO_LARGE = "KeyFileTooLarge"
    const val KIND_PASSWORD_TOO_LONG = "PasswordTooLong"

    fun isKeyFileTooLarge(size: Long): Boolean = size > KEY_FILE_MAX_SIZE

    /**
     * Counted in code points, as the core counts characters. Not [String.length]: that is utf-16 units,
     * an emoji is two of them, and the two sides would disagree right at the limit.
     */
    fun isPasswordTooLong(password: String): Boolean =
        password.codePointCount(0, password.length) > PASSWORD_MAX_CHARS
}
