package ru.kino.dev.vault.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.kino.dev.core.CoreException
import ru.kino.dev.core.DatabaseFileException
import ru.kino.dev.core.FakeDatabaseFiles
import ru.kino.dev.core.FakeNativeCore
import ru.kino.dev.core.expectFailure

class VaultDatabaseRepositoryImplTest {

    private val journal = mutableListOf<String>()
    private val core = FakeNativeCore(journal)
    private val files = FakeDatabaseFiles(journal)
    private val repository = VaultDatabaseRepositoryImpl(core, files)

    @Test
    fun `saving writes the bytes the core gave into the file`() = runTest {
        repository.save(DB)

        assertArrayEquals(core.databaseBytes, files.contents[DB])
        assertEquals(listOf("saveDatabase $DB", "writeReplacing $DB"), journal)
    }

    @Test
    fun `a file that cannot be written fails the save`() = runTest {
        files.unreachable += DB

        expectFailure<DatabaseFileException> { repository.save(DB) }
    }

    @Test
    fun `closing closes the database in the core`() = runTest {
        repository.close(DB)

        assertEquals(listOf("closeDatabase $DB"), journal)
    }

    @Test
    fun `a file the checksum still matches has not changed`() = runTest {
        files.contents[DB] = byteArrayOf(1, 2)

        assertFalse(repository.hasChangedElsewhere(DB))
        assertArrayEquals(byteArrayOf(1, 2), core.bytesReceived)
    }

    @Test
    fun `a file the checksum no longer matches has changed - an answer, not a failure`() = runTest {
        files.contents[DB] = byteArrayOf(1, 2)
        core.changedElsewhere += DB

        assertTrue(repository.hasChangedElsewhere(DB))
    }

    @Test
    fun `any other refusal of the core while checking is a failure`() = runTest {
        files.contents[DB] = byteArrayOf(1, 2)
        core.failures["verifyFileChecksum"] = CoreException("DbKeyNotFound", "not open")

        val error = expectFailure<CoreException> { repository.hasChangedElsewhere(DB) }
        assertEquals("DbKeyNotFound", error.kind)
    }

    @Test
    fun `merging hands the current file to the core and reports its summary`() = runTest {
        files.contents[DB] = byteArrayOf(7)

        val summary = repository.merge(DB)

        assertEquals(core.mergeSummary, summary)
        assertArrayEquals(byteArrayOf(7), core.bytesReceived)
    }

    private companion object {
        const val DB = "content://docs/passwords.kdbx"
    }
}
