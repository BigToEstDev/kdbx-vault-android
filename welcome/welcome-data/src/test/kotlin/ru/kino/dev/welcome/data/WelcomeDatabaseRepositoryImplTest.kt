package ru.kino.dev.welcome.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.kino.dev.core.CoreException
import ru.kino.dev.core.CredentialLimits
import ru.kino.dev.core.FakeDatabaseFiles
import ru.kino.dev.core.FakeNativeCore
import ru.kino.dev.core.KeyFile
import ru.kino.dev.core.OpenedDatabase
import ru.kino.dev.core.expectFailure
import ru.kino.dev.welcome.CurrentDatabase
import ru.kino.dev.welcome.PickedFile
import ru.kino.dev.welcome.WelcomeFailure
import kotlin.coroutines.cancellation.CancellationException

class WelcomeDatabaseRepositoryImplTest {

    private val journal = mutableListOf<String>()
    private val core = FakeNativeCore(journal)
    private val files = FakeDatabaseFiles(journal)
    private val store = FakeCurrentDatabaseStore(journal)
    private val repository = WelcomeDatabaseRepositoryImpl(core, files, store, store)

    // --- open ---------------------------------------------------------------------------------------------

    @Test
    fun `opening fixes everything before handing the dbKey up - core, access, remembered`() = runTest {
        givenDatabase()
        givenKeyFile()

        val dbKey = repository.open(DB, "pw", KEY)

        assertEquals(DB, dbKey)
        assertInOrder("openDatabase $DB", "keepAccess $DB", "keepAccess $KEY", "remember $DB")
    }

    @Test
    fun `the remembered database has the names it was opened with`() = runTest {
        givenDatabase()
        givenKeyFile()

        repository.open(DB, "pw", KEY)

        assertEquals(
            CurrentDatabase(
                uri = DB,
                fileName = "passwords.kdbx",
                databaseName = core.databaseName,
                keyFileUri = KEY,
                keyFileName = "my.keyx",
            ),
            store.current.value,
        )
    }

    @Test
    fun `the database is kept for writing and the key file for reading only`() = runTest {
        givenDatabase()
        givenKeyFile()

        repository.open(DB, "pw", KEY)

        assertEquals(mapOf(DB to true, KEY to false), files.kept)
    }

    @Test
    fun `the core gets the bytes of the file, the password and the key file`() = runTest {
        givenDatabase()
        givenKeyFile()

        repository.open(DB, "pw", KEY)

        assertArrayEquals(DB_BYTES, core.bytesReceived)
        assertEquals("pw", core.passwordReceived)
        assertEquals("my.keyx", core.keyFilesReceived.single().name)
    }

    @Test
    fun `a database closed with a key file alone is opened without a password`() = runTest {
        givenDatabase()
        givenKeyFile()

        repository.open(DB, null, KEY)

        assertNull(core.passwordReceived)
    }

    @Test
    fun `the key file is wiped once the core has it - after success`() = runTest {
        givenDatabase()
        givenKeyFile()

        repository.open(DB, "pw", KEY)

        assertWiped(core.keyFilesReceived.single())
    }

    @Test
    fun `the key file is wiped once the core has it - after failure`() = runTest {
        givenDatabase()
        givenKeyFile()
        core.failures["openDatabase"] = CoreException("InvalidCredentials", "no")

        expectFailure<WelcomeFailure.InvalidCredentials> { repository.open(DB, "wrong", KEY) }

        assertWiped(core.keyFilesReceived.single())
    }

    @Test
    fun `wrong credentials leave the remembered database as it was`() = runTest {
        givenDatabase()
        val before = remembered()
        core.failures["openDatabase"] = CoreException("InvalidCredentials", "no")

        expectFailure<WelcomeFailure.InvalidCredentials> { repository.open(DB, "wrong", null) }

        assertSame(before, store.current.value)
        assertFalse(journal.toString(), journal.any { it.startsWith("keepAccess") })
    }

    @Test
    fun `a database file out of reach is no access to the database - remembered one stays`() = runTest {
        val before = remembered()
        files.unreachable += DB

        val failure = expectFailure<WelcomeFailure.FileUnreachable> { repository.open(DB, "pw", null) }

        assertEquals(PickedFile.Database, failure.file)
        assertSame(before, store.current.value)
        assertFalse(journal.toString(), journal.any { it.startsWith("openDatabase") })
    }

    @Test
    fun `a key file out of reach is no access to the key file`() = runTest {
        givenDatabase()
        files.unreachable += KEY

        val failure = expectFailure<WelcomeFailure.FileUnreachable> { repository.open(DB, "pw", KEY) }

        assertEquals(PickedFile.KeyFile, failure.file)
    }

    @Test
    fun `a key file over the limit is refused before the core sees it`() = runTest {
        givenDatabase()
        givenKeyFile()
        files.reportedSizes[KEY] = CredentialLimits.KEY_FILE_MAX_SIZE + 1

        expectFailure<WelcomeFailure.KeyFileTooLarge> { repository.open(DB, "pw", KEY) }

        assertFalse(journal.toString(), journal.any { it.startsWith("openDatabase") })
    }

    @Test
    fun `access that cannot be kept is no access, and the database is closed again`() = runTest {
        givenDatabase()
        files.notKeepable += DB

        val failure = expectFailure<WelcomeFailure.FileUnreachable> { repository.open(DB, "pw", null) }

        assertEquals(PickedFile.Database, failure.file)
        assertTrue(journal.toString(), "closeDatabase $DB" in journal)
        assertFalse(DB in core.opened)
        assertNull(store.current.value)
    }

    @Test
    fun `key file access that cannot be kept is no access to the key file, and the database is closed`() =
        runTest {
            givenDatabase()
            givenKeyFile()
            files.notKeepable += KEY

            val failure = expectFailure<WelcomeFailure.FileUnreachable> { repository.open(DB, "pw", KEY) }

            assertEquals(PickedFile.KeyFile, failure.file)
            assertFalse(DB in core.opened)
            assertNull(store.current.value)
        }

    @Test
    fun `a store that cannot be written is ours, and the database is closed`() = runTest {
        givenDatabase()
        store.failure = IllegalStateException("disk full")

        expectFailure<WelcomeFailure.Unexpected> { repository.open(DB, "pw", null) }

        assertFalse(DB in core.opened)
    }

    @Test
    fun `cancelling an opening is not turned into a failure`() = runTest {
        givenDatabase()
        core.failures["openDatabase"] = CancellationException("left the screen")

        expectFailure<CancellationException> { repository.open(DB, "pw", null) }
    }

    @Test
    fun `a kind of the core reaches the screen as its case`() = runTest {
        givenDatabase()
        core.failures["openDatabase"] = CoreException("ContentCorrupted", "cut short")

        expectFailure<WelcomeFailure.CorruptedFile> { repository.open(DB, "pw", null) }
    }

    // --- unlock -------------------------------------------------------------------------------------------

    @Test
    fun `unlocking uses the key file the remembered database was opened with`() = runTest {
        givenKeyFile()
        givenOpen()
        store.current.value = CurrentDatabase(DB, "passwords.kdbx", "Passwords", KEY, "my.keyx")

        repository.unlock(DB, "pw")

        val keyFile = core.keyFilesReceived.single()
        assertEquals("my.keyx", keyFile.name)
        assertWiped(keyFile)
        assertEquals("pw", core.passwordReceived)
    }

    @Test
    fun `unlocking another database than the remembered one takes no key file from it`() = runTest {
        givenKeyFile()
        givenOpen()
        store.current.value = CurrentDatabase("content://docs/other.kdbx", null, null, KEY, "my.keyx")

        repository.unlock(DB, "pw")

        assertTrue(core.keyFilesReceived.isEmpty())
    }

    @Test
    fun `unlocking with wrong credentials is wrong credentials`() = runTest {
        givenOpen()
        core.failures["unlockDatabase"] = CoreException("InvalidCredentials", "no")

        expectFailure<WelcomeFailure.InvalidCredentials> { repository.unlock(DB, "wrong") }
    }

    @Test
    fun `unlocking reads no file but the key file`() = runTest {
        givenOpen()

        repository.unlock(DB, "pw")

        assertEquals(listOf("unlockDatabase $DB"), journal)
    }

    // --- create -------------------------------------------------------------------------------------------

    @Test
    fun `creating writes the database and fixes it before handing the dbKey up`() = runTest {
        givenNewFile()

        val dbKey = repository.create(DB, "pw", null)

        assertEquals(DB, dbKey)
        assertArrayEquals(core.databaseBytes, files.contents[DB])
        assertInOrder("createDatabase $DB", "writeReplacing $DB", "keepAccess $DB", "remember $DB")
        assertTrue(DB in core.opened)
    }

    @Test
    fun `the database is named after its file without the extension`() = runTest {
        givenNewFile(name = "Family.KDBX")

        repository.create(DB, "pw", null)

        assertEquals("Family", core.created?.databaseName)
        assertEquals("Family", store.current.value?.databaseName)
        assertEquals("Family.KDBX", store.current.value?.fileName)
    }

    @Test
    fun `a file without a name gives the database the default one`() = runTest {
        givenNewFile(name = null)

        repository.create(DB, "pw", null)

        assertEquals("kdbxvault", core.created?.databaseName)
    }

    @Test
    fun `a file named just the extension gives the database the default one`() = runTest {
        givenNewFile(name = ".kdbx")

        repository.create(DB, "pw", null)

        assertEquals("kdbxvault", core.created?.databaseName)
    }

    @Test
    fun `the key file is read back from where it was saved, and wiped`() = runTest {
        givenNewFile()
        givenKeyFile()

        repository.create(DB, "pw", KEY)

        val keyFile = core.created?.keyFile
        assertEquals("my.keyx", keyFile?.name)
        assertWiped(keyFile!!)
        assertEquals(KEY, store.current.value?.keyFileUri)
        assertEquals(false, files.kept[KEY])
    }

    @Test
    fun `a failed creation deletes the empty file it was given`() = runTest {
        givenNewFile()
        core.failures["createDatabase"] = IllegalStateException("a bug")

        expectFailure<WelcomeFailure.Unexpected> { repository.create(DB, "pw", null) }

        assertFalse(DB in files.contents)
        assertNull(store.current.value)
    }

    @Test
    fun `a write that fails is no access - the database is closed and the file deleted`() = runTest {
        givenNewFile()
        files.unwritable += DB

        val failure = expectFailure<WelcomeFailure.FileUnreachable> { repository.create(DB, "pw", null) }

        assertEquals(PickedFile.Database, failure.file)
        assertFalse(DB in core.opened)
        assertFalse(DB in files.contents)
        assertNull(store.current.value)
    }

    @Test
    fun `access that cannot be kept after creating closes the database and deletes the file`() = runTest {
        givenNewFile()
        files.notKeepable += DB

        val failure = expectFailure<WelcomeFailure.FileUnreachable> { repository.create(DB, "pw", null) }

        assertEquals(PickedFile.Database, failure.file)
        assertFalse(DB in core.opened)
        assertFalse(DB in files.contents)
        assertNull(store.current.value)
    }

    @Test
    fun `a file that held something is never deleted - the user chose to write over it`() = runTest {
        files.contents[DB] = byteArrayOf(1, 2, 3)
        core.failures["createDatabase"] = IllegalStateException("a bug")

        expectFailure<WelcomeFailure.Unexpected> { repository.create(DB, "pw", null) }

        assertArrayEquals(byteArrayOf(1, 2, 3), files.contents[DB])
        assertFalse(journal.toString(), journal.any { it.startsWith("delete") })
    }

    @Test
    fun `a provider that cannot delete does not hide the failure of creating`() = runTest {
        givenNewFile()
        files.undeletable += DB
        core.failures["createDatabase"] = CoreException("UnexpectedError", "boom")

        expectFailure<WelcomeFailure.Unexpected> { repository.create(DB, "pw", null) }

        assertTrue(journal.toString(), "delete $DB" in journal)
    }

    @Test
    fun `an abandoned creation leaves nothing behind`() = runTest {
        givenNewFile()
        core.failures["createDatabase"] = CancellationException("left the screen")

        expectFailure<CancellationException> { repository.create(DB, "pw", null) }

        assertFalse(DB in files.contents)
    }

    @Test
    fun `a key file over the limit is refused, and the empty file goes`() = runTest {
        givenNewFile()
        givenKeyFile()
        files.reportedSizes[KEY] = CredentialLimits.KEY_FILE_MAX_SIZE + 1

        expectFailure<WelcomeFailure.KeyFileTooLarge> { repository.create(DB, "pw", KEY) }

        assertFalse(DB in files.contents)
    }

    // --- helpers ------------------------------------------------------------------------------------------

    private fun givenDatabase() {
        files.contents[DB] = DB_BYTES.copyOf()
        files.names[DB] = "passwords.kdbx"
    }

    private fun givenNewFile(name: String? = "passwords.kdbx") {
        files.contents[DB] = ByteArray(0)
        name?.let { files.names[DB] = it }
    }

    private fun givenKeyFile() {
        files.contents[KEY] = KEY_BYTES.copyOf()
        files.names[KEY] = "my.keyx"
    }

    private fun givenOpen() {
        core.opened[DB] = OpenedDatabase(DB, "Passwords", "passwords.kdbx", null)
    }

    private fun remembered(): CurrentDatabase =
        CurrentDatabase(DB, "passwords.kdbx", "Passwords", null, null).also { store.current.value = it }

    private fun assertWiped(keyFile: KeyFile) {
        assertTrue("the key file was not wiped", keyFile.content.all { it == 0.toByte() })
    }

    private fun assertInOrder(vararg calls: String) {
        val positions = calls.map { call -> journal.indexOf(call).also { assertTrue("$call: $journal", it >= 0) } }
        assertEquals(journal.toString(), positions.sorted(), positions)
    }

    private companion object {
        const val DB = "content://docs/passwords.kdbx"
        const val KEY = "content://keys/my.keyx"
        val DB_BYTES = byteArrayOf(0x03, 0xD9.toByte(), 0xA2.toByte(), 0x9A.toByte(), 0x67)
        val KEY_BYTES = "<KeyFile>secret</KeyFile>".toByteArray()
    }
}
