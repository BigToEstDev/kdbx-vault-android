package ru.kino.dev.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyFileReadingTest {

    private val files = FakeDatabaseFiles()

    @Test
    fun `a key file comes back with its content and the name the provider tells`() = runTest {
        files.contents[URI] = byteArrayOf(1, 2, 3)
        files.names[URI] = "my.keyx"

        val keyFile = files.readKeyFile(URI)

        assertEquals("my.keyx", keyFile.name)
        assertArrayEquals(byteArrayOf(1, 2, 3), keyFile.content)
    }

    @Test
    fun `without a name from the provider the uri is shown`() = runTest {
        files.contents[URI] = byteArrayOf(1)

        assertEquals(URI, files.readKeyFile(URI).name)
    }

    @Test
    fun `a file reported larger than the limit is refused without reading it`() = runTest {
        files.contents[URI] = byteArrayOf(1)
        files.reportedSizes[URI] = CredentialLimits.KEY_FILE_MAX_SIZE + 1

        assertTooLarge(expectFailure { files.readKeyFile(URI) })
        assertTrue(files.journal.toString(), files.journal.none { it.startsWith("read") })
    }

    @Test
    fun `a file of unknown size is refused by its bytes when they are over the limit`() = runTest {
        files.contents[URI] = ByteArray(CredentialLimits.KEY_FILE_MAX_SIZE.toInt() + 2)

        assertTooLarge(expectFailure { files.readKeyFile(URI) })
    }

    @Test
    fun `a file reported small is still refused when its bytes are over the limit`() = runTest {
        files.contents[URI] = ByteArray(CredentialLimits.KEY_FILE_MAX_SIZE.toInt() + 1)
        files.reportedSizes[URI] = 1

        assertTooLarge(expectFailure { files.readKeyFile(URI) })
    }

    @Test
    fun `a file of exactly the limit is accepted`() = runTest {
        files.contents[URI] = ByteArray(CredentialLimits.KEY_FILE_MAX_SIZE.toInt())

        assertEquals(CredentialLimits.KEY_FILE_MAX_SIZE.toInt(), files.readKeyFile(URI).content.size)
    }

    @Test
    fun `a file that cannot be reached fails as a file, not as a key file`() = runTest {
        files.unreachable += URI

        expectFailure<DatabaseFileException> { files.readKeyFile(URI) }
    }

    private fun assertTooLarge(error: CoreException) {
        assertEquals(CredentialLimits.KIND_KEY_FILE_TOO_LARGE, error.kind)
    }

    private companion object {
        const val URI = "content://keys/my.keyx"
    }
}
