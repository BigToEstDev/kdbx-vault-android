package ru.kino.dev.core

/**
 * Reads the key file the user picked, refusing one larger than [CredentialLimits.KEY_FILE_MAX_SIZE]
 * before it is in memory.
 *
 * The size the provider reports is checked first, so a video picked by mistake is turned down without
 * reading it. A provider that does not know the size gets one byte more than the limit read, and the
 * bytes decide. The refusal is the core's own kind, [CredentialLimits.KIND_KEY_FILE_TOO_LARGE]: the core
 * and the bridge refuse the same file the same way, and a caller has one failure to handle, not three.
 *
 * The content is a secret on par with the password; the caller wipes the result ([KeyFile.wipe]) once
 * the call that needed it is done.
 *
 * @throws CoreException the file is larger than the limit
 * @throws DatabaseFileException the file cannot be read
 */
suspend fun DatabaseFiles.readKeyFile(uri: String): KeyFile {
    size(uri)?.let { if (CredentialLimits.isKeyFileTooLarge(it)) throw CredentialLimits.keyFileTooLarge() }

    val content = readAtMost(uri, (CredentialLimits.KEY_FILE_MAX_SIZE + 1).toInt())
    if (CredentialLimits.isKeyFileTooLarge(content.size.toLong())) {
        content.fill(0)
        throw CredentialLimits.keyFileTooLarge()
    }

    // The core only shows the name; a uri in its place is ugly, but it is never wrong
    return KeyFile(name = displayName(uri) ?: uri, content = content)
}
