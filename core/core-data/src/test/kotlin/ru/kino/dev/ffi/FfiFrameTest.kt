package ru.kino.dev.ffi

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import ru.kino.dev.core.CoreException

/**
 * The frame is plain bytes, so both halves of the binary boundary can be checked here, on the jvm: the
 * shape Rust writes in `rust/pass-ffi/src/frame.rs`, and that a secret does not outlive the call.
 */
class FfiFrameTest {

    @Test
    fun `an envelope without a payload is read back`() {
        val parsed = FfiFrame.split(frameOf("""{"ok":null}"""))

        assertEquals("""{"ok":null}""", parsed.envelope)
        assertEquals(0, parsed.payload.size)
    }

    @Test
    fun `the payload after the envelope is returned untouched`() {
        val parsed = FfiFrame.split(frameOf("{}", byteArrayOf(7, 8, 9)))

        assertEquals("{}", parsed.envelope)
        assertArrayEquals(byteArrayOf(7, 8, 9), parsed.payload)
    }

    @Test
    fun `a frame too short to hold its own length is a malformed envelope`() {
        try {
            FfiFrame.split(byteArrayOf(0, 0))
            fail("a two byte frame should not parse")
        } catch (e: CoreException) {
            assertEquals(FfiEnvelope.MALFORMED_ENVELOPE, e.kind)
        }
    }

    @Test
    fun `a length longer than the frame is a malformed envelope`() {
        val lying = byteArrayOf(0, 0, 0, 64) + "{}".toByteArray()

        try {
            FfiFrame.split(lying)
            fail("a frame claiming 64 bytes of envelope should not parse")
        } catch (e: CoreException) {
            assertEquals(FfiEnvelope.MALFORMED_ENVELOPE, e.kind)
        }
    }

    @Test
    fun `the arguments are wiped after the call, successful or not`() {
        var seenByRust: ByteArray? = null

        val parsed = FfiBinaryCall.call(
            command = "whatever",
            argsJson = """{"password":"s3cret"}""",
        ) { _, args, _ ->
            // The bridge sees the real arguments while the call is running
            seenByRust = args
            assertTrue(String(args).contains("s3cret"))
            frameOf("""{"ok":null}""")
        }

        assertEquals("""{"ok":null}""", parsed.envelope)
        assertArrayEquals(ByteArray(seenByRust!!.size), seenByRust)
    }

    @Test
    fun `the arguments are wiped even when the call fails`() {
        var seenByRust: ByteArray? = null

        try {
            FfiBinaryCall.call(command = "whatever", argsJson = """{"password":"s3cret"}""") { _, args, _ ->
                seenByRust = args
                throw RuntimeException("the bridge is broken")
            }
            fail("the failure should have travelled up")
        } catch (e: RuntimeException) {
            assertEquals("the bridge is broken", e.message)
        }

        assertArrayEquals(ByteArray(seenByRust!!.size), seenByRust)
    }

    private fun frameOf(envelope: String, payload: ByteArray = ByteArray(0)): ByteArray {
        val envelopeBytes = envelope.toByteArray(Charsets.UTF_8)
        val length = envelopeBytes.size
        return byteArrayOf(
            (length ushr 24).toByte(),
            (length ushr 16).toByte(),
            (length ushr 8).toByte(),
            length.toByte(),
        ) + envelopeBytes + payload
    }
}
