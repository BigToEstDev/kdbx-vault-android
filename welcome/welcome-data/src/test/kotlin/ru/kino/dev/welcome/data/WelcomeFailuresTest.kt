package ru.kino.dev.welcome.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.kino.dev.core.CoreException
import ru.kino.dev.core.DatabaseFileException
import ru.kino.dev.core.expectFailure
import ru.kino.dev.welcome.PickedFile
import ru.kino.dev.welcome.WelcomeFailure
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass

/** The table of pass-docs, `docs/android/screens/welcome/decisions.md`, "Ошибки", kind by kind. */
class WelcomeFailuresTest {

    @Test
    fun `every kind of the table lands in its case`() {
        val table: Map<String, KClass<out WelcomeFailure>> = mapOf(
            "InvalidCredentials" to WelcomeFailure.InvalidCredentials::class,
            "InvalidKeePassFile" to WelcomeFailure.UnsupportedFile::class,
            "OldUnsupportedKeePass1" to WelcomeFailure.UnsupportedFile::class,
            "OldUnsupportedKdbxFormat" to WelcomeFailure.UnsupportedFile::class,
            "UnsupportedCipher" to WelcomeFailure.UnsupportedFile::class,
            "UnsupportedKdfAlgorithm" to WelcomeFailure.UnsupportedFile::class,
            "SupportedOnlyArgon2dKdfAlgorithm" to WelcomeFailure.UnsupportedFile::class,
            "HeaderCorrupted" to WelcomeFailure.CorruptedFile::class,
            "ContentCorrupted" to WelcomeFailure.CorruptedFile::class,
            "XmlParsingFailed" to WelcomeFailure.CorruptedFile::class,
            "Io" to WelcomeFailure.CorruptedFile::class,
            "KeyFileTooLarge" to WelcomeFailure.KeyFileTooLarge::class,
            "DbKeyNotFound" to WelcomeFailure.Unexpected::class,
            "Decryption" to WelcomeFailure.Unexpected::class,
            "UnexpectedError" to WelcomeFailure.Unexpected::class,
            "PasswordTooLong" to WelcomeFailure.Unexpected::class,
            "InvalidArguments" to WelcomeFailure.Unexpected::class,
            "UnknownCommand" to WelcomeFailure.Unexpected::class,
        )

        table.forEach { (kind, expected) ->
            val core = CoreException(kind, "from the core")
            val mapped = core.toWelcomeFailure()

            assertEquals(kind, expected, mapped::class)
            assertSame("the core's failure goes along for the log", core, mapped.cause)
        }
    }

    @Test
    fun `a failure already mapped passes as it is`() = runTest {
        val failure = WelcomeFailure.FileUnreachable(PickedFile.KeyFile)

        assertSame(failure, expectFailure<WelcomeFailure> { welcomeCall { throw failure } })
    }

    @Test
    fun `anything that is not the core's is ours`() = runTest {
        val bug = IllegalStateException("a bug")

        val mapped = expectFailure<WelcomeFailure.Unexpected> { welcomeCall { throw bug } }
        assertSame(bug, mapped.cause)
    }

    @Test
    fun `cancellation is not a failure and passes untouched`() = runTest {
        val cancelled = CancellationException("left the screen")

        assertSame(cancelled, expectFailure<CancellationException> { welcomeCall { throw cancelled } })
    }

    @Test
    fun `a file that cannot be reached names which file it was`() {
        val failure = expectFailure<WelcomeFailure.FileUnreachable> {
            accessing(PickedFile.KeyFile) { throw DatabaseFileException("gone") }
        }

        assertEquals(PickedFile.KeyFile, failure.file)
        assertTrue(failure.cause is DatabaseFileException)
    }
}
