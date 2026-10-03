package ru.kino.dev.welcome.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.kino.dev.core.FakeDatabaseFiles
import ru.kino.dev.core.FakeNativeCore
import ru.kino.dev.core.expectFailure
import ru.kino.dev.welcome.PickedFile
import ru.kino.dev.welcome.WelcomeFailure

class WelcomeFilesRepositoryImplTest {

    private val journal = mutableListOf<String>()
    private val core = FakeNativeCore(journal)
    private val files = FakeDatabaseFiles(journal)
    private val repository = WelcomeFilesRepositoryImpl(core, files)

    @Test
    fun `a picked file shows the name the provider tells`() = runTest {
        files.names[KEY] = "my.keyx"

        assertEquals("my.keyx", repository.displayName(KEY))
        assertNull(repository.displayName("content://keys/nameless"))
    }

    @Test
    fun `a new file from save as would overwrite nothing`() = runTest {
        files.contents[KEY] = ByteArray(0)

        assertFalse(repository.wouldOverwrite(KEY, PickedFile.KeyFile))
    }

    @Test
    fun `an existing file picked in save as would be overwritten`() = runTest {
        files.contents[KEY] = byteArrayOf(1)

        assertTrue(repository.wouldOverwrite(KEY, PickedFile.KeyFile))
    }

    @Test
    fun `a file out of reach when asked about overwriting names the file asked about`() = runTest {
        files.unreachable += DB

        val failure = expectFailure<WelcomeFailure.FileUnreachable> {
            repository.wouldOverwrite(DB, PickedFile.Database)
        }
        assertEquals(PickedFile.Database, failure.file)
    }

    @Test
    fun `a new key file goes from the core straight into the picked file`() = runTest {
        files.contents[KEY] = ByteArray(0)

        repository.writeNewKeyFile(KEY)

        assertArrayEquals(core.generatedKeyFile, files.contents[KEY])
        assertEquals(listOf("generateKeyFile", "writeReplacing $KEY"), journal)
    }

    @Test
    fun `a key file that cannot be written is no access to the key file`() = runTest {
        files.unwritable += KEY

        val failure = expectFailure<WelcomeFailure.FileUnreachable> { repository.writeNewKeyFile(KEY) }
        assertEquals(PickedFile.KeyFile, failure.file)
    }

    private companion object {
        const val DB = "content://docs/passwords.kdbx"
        const val KEY = "content://keys/my.keyx"
    }
}
