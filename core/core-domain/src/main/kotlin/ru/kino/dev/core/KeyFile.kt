package ru.kino.dev.core

/**
 * A key file, as the core takes it: its content and the name to show.
 *
 * Content rather than a path: on Android a key file is behind SAF, and a path the core could open would
 * mean a copy in the app's own storage - which goes with a reinstall, and the database with it. So the
 * file stays wherever the user keeps it and is read anew each time (Step 29 in pass-docs).
 *
 * The content is a secret on par with the password. The holder wipes it ([wipe]) once the call is done.
 */
class KeyFile(
    /** Name to show - the display name of the document, not a path */
    val name: String,
    val content: ByteArray,
) {
    /** Overwrites the content, once nothing needs it any more. */
    fun wipe() {
        content.fill(0)
    }

    // ByteArray in a class compares by identity, which is never what a caller means
    override fun equals(other: Any?): Boolean =
        this === other || (other is KeyFile && name == other.name && content.contentEquals(other.content))

    override fun hashCode(): Int = 31 * name.hashCode() + content.contentHashCode()

    // Never the content: a key file in a log is a key file given away
    override fun toString(): String = "KeyFile(name=$name, size=${content.size})"
}
