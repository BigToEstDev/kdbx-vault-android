package ru.kino.dev.ffi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import ru.kino.dev.core.CoreException

/**
 * The envelope is the contract between Kotlin and Rust, and it is pure text - so it can be tested here,
 * on the jvm, without a device and without the native library.
 */
class FfiEnvelopeTest {

    @Test
    fun `a payload is read out of the ok envelope`() {
        val password = FfiEnvelope.unwrap(
            """{"ok":{"password":"s3cret"}}""",
            GeneratedPasswordDto.serializer(),
        ).password

        assertEquals("s3cret", password)
    }

    @Test
    fun `a failure becomes an exception carrying the kind the core reported`() {
        try {
            FfiEnvelope.unwrap(
                """{"err":{"kind":"NotFound","message":"no entry for that id"}}""",
                GeneratedPasswordDto.serializer(),
            )
            fail("the failure should not have been ignored")
        } catch (e: CoreException) {
            assertEquals("NotFound", e.kind)
            assertEquals("no entry for that id", e.message)
        }
    }

    // An older app must survive a core that answers with a field the app has never heard of
    @Test
    fun `an unknown field in the payload is ignored`() {
        val password = FfiEnvelope.unwrap(
            """{"ok":{"password":"s3cret","entropy_bits":96}}""",
            GeneratedPasswordDto.serializer(),
        ).password

        assertEquals("s3cret", password)
    }

    @Test
    fun `an envelope with neither ok nor err is reported as malformed`() {
        try {
            FfiEnvelope.unwrap("""{"something":1}""", GeneratedPasswordDto.serializer())
            fail("a nonsense envelope should not pass silently")
        } catch (e: CoreException) {
            assertEquals(FfiEnvelope.MALFORMED_ENVELOPE, e.kind)
        }
    }

    @Test
    fun `password options are serialised with the names the core expects`() {
        val json = FfiEnvelope.json.encodeToString(
            PasswordOptionsDto.serializer(),
            PasswordOptionsDto(
                length = 20,
                numbers = true,
                lowercaseLetters = true,
                uppercaseLetters = true,
                symbols = false,
                spaces = false,
                excludeSimilarCharacters = true,
                strict = true,
            ),
        )

        // The core reads these exact snake_case keys; a rename here silently breaks the call
        assertTrue(json, json.contains(""""lowercase_letters":true"""))
        assertTrue(json, json.contains(""""uppercase_letters":true"""))
        assertTrue(json, json.contains(""""exclude_similar_characters":true"""))
        assertTrue(json, json.contains(""""length":20"""))
    }

    // The bridge reads `key_file` as {name, content} with standard padded base64 (src/key_file.rs)
    @Test
    fun `a key file is serialised the way the bridge reads it`() {
        val json = FfiEnvelope.json.encodeToString(
            ReadKdbxDto.serializer(),
            ReadKdbxDto(
                dbKey = "content://db",
                password = "pw",
                keyFile = KeyFileDto(name = "my.keyx", content = "AAEC"),
                fileName = "db.kdbx",
            ),
        )

        assertTrue(json, json.contains(""""key_file":{"name":"my.keyx","content":"AAEC"}"""))
        assertTrue("no path is sent any more: $json", !json.contains("key_file_name"))
    }

    // `UnlockArgs` in src/handlers/lifecycle.rs: the credentials of opening, without the file
    @Test
    fun `unlocking sends the credentials under the names the bridge reads`() {
        val json = FfiEnvelope.json.encodeToString(
            UnlockDto.serializer(),
            UnlockDto(
                dbKey = "content://db",
                password = "pw",
                keyFile = KeyFileDto(name = "my.keyx", content = "AAEC"),
            ),
        )

        assertTrue(json, json.contains(""""db_key":"content://db""""))
        assertTrue(json, json.contains(""""password":"pw""""))
        assertTrue(json, json.contains(""""key_file":{"name":"my.keyx","content":"AAEC"}"""))
    }
}
