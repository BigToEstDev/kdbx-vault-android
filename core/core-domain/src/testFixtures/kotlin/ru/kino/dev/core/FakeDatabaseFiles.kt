package ru.kino.dev.core

/**
 * [DatabaseFiles] in memory, for tests of what is built on top of it.
 *
 * Every call is written to [journal] as `"<method> <uri>"`. The journal can be shared with other fakes,
 * which is how a test checks the order of calls across them - opened in the core, then access kept, then
 * remembered.
 */
class FakeDatabaseFiles(val journal: MutableList<String> = mutableListOf()) : DatabaseFiles {

    /** Contents of the files that exist. */
    val contents = mutableMapOf<String, ByteArray>()

    /** Names the provider tells; a uri without one has no name to show. */
    val names = mutableMapOf<String, String>()

    /** Sizes the provider reports; a uri without one is a file of unknown size. */
    val reportedSizes = mutableMapOf<String, Long>()

    /** Files that cannot be reached at all - gone, no permission, provider offline. */
    val unreachable = mutableSetOf<String>()

    /** Files the picker granted no access to keep. */
    val notKeepable = mutableSetOf<String>()

    /** Files the provider refuses to delete. */
    val undeletable = mutableSetOf<String>()

    /** Access kept so far: uri to whether it was kept for writing. */
    val kept = mutableMapOf<String, Boolean>()

    override suspend fun read(uri: String): ByteArray = reachable("read", uri).copyOf()

    override suspend fun readAtMost(uri: String, maxBytes: Int): ByteArray =
        reachable("readAtMost", uri).copyOf().take(maxBytes).toByteArray()

    override suspend fun writeReplacing(uri: String, bytes: ByteArray) {
        log("writeReplacing", uri)
        if (uri in unreachable) throw DatabaseFileException("unreachable: $uri")
        contents[uri] = bytes.copyOf()
    }

    override suspend fun displayName(uri: String): String? {
        log("displayName", uri)
        return names[uri]
    }

    override suspend fun size(uri: String): Long? {
        log("size", uri)
        return reportedSizes[uri]
    }

    override suspend fun isEmpty(uri: String): Boolean = reachable("isEmpty", uri).isEmpty()

    override suspend fun delete(uri: String): Boolean {
        log("delete", uri)
        if (uri in undeletable) return false
        return contents.remove(uri) != null
    }

    override suspend fun keepAccess(uri: String, forWriting: Boolean) {
        log("keepAccess", uri)
        if (uri in notKeepable) throw DatabaseFileException("no persistable grant: $uri")
        kept[uri] = forWriting
    }

    private fun reachable(method: String, uri: String): ByteArray {
        log(method, uri)
        if (uri in unreachable) throw DatabaseFileException("unreachable: $uri")
        return contents[uri] ?: throw DatabaseFileException("no such file: $uri")
    }

    private fun log(method: String, uri: String) {
        journal += "$method $uri"
    }
}
