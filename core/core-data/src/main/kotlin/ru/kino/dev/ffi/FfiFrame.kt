package ru.kino.dev.ffi

import ru.kino.dev.core.CoreException

/**
 * The answer of a binary call: the json envelope and the bytes it describes.
 *
 * Shape on the wire, built by `rust/pass-ffi/src/frame.rs`:
 *
 * ```text
 * [4 bytes: length of the envelope, big endian][envelope utf-8][payload bytes]
 * ```
 *
 * One frame instead of two calls, so the envelope and its bytes cannot be separated and there is nothing
 * left in the library for a second call to fetch. A refusal carries no payload, so a failed call is a
 * frame of just the envelope - and [FfiEnvelope.unwrap] turns it into a [CoreException] as usual.
 */
internal object FfiFrame {

    private const val LENGTH_BYTES = 4

    /** Splits a frame into the envelope text and the payload, which is empty for most commands. */
    fun split(frame: ByteArray): Parsed {
        if (frame.size < LENGTH_BYTES) {
            throw CoreException(
                kind = FfiEnvelope.MALFORMED_ENVELOPE,
                message = "A frame of ${frame.size} bytes is too short to hold the envelope length",
            )
        }

        val envelopeLength = readLength(frame)
        val payloadStart = LENGTH_BYTES + envelopeLength

        if (envelopeLength < 0 || payloadStart > frame.size) {
            throw CoreException(
                kind = FfiEnvelope.MALFORMED_ENVELOPE,
                message = "The frame says the envelope is $envelopeLength bytes, " +
                    "but only ${frame.size - LENGTH_BYTES} follow the length",
            )
        }

        return Parsed(
            envelope = String(frame, LENGTH_BYTES, envelopeLength, Charsets.UTF_8),
            payload = frame.copyOfRange(payloadStart, frame.size),
        )
    }

    // Big endian, as Rust writes it with to_be_bytes. Read by hand rather than through ByteBuffer: four
    // bytes are four bytes, and the intent is clearer than a wrapped buffer
    private fun readLength(frame: ByteArray): Int =
        (frame[0].toInt() and 0xFF shl 24) or
            (frame[1].toInt() and 0xFF shl 16) or
            (frame[2].toInt() and 0xFF shl 8) or
            (frame[3].toInt() and 0xFF)

    internal data class Parsed(val envelope: String, val payload: ByteArray) {

        // ByteArray in a data class compares by identity, which is never what a test means here
        override fun equals(other: Any?): Boolean =
            this === other ||
                (other is Parsed && envelope == other.envelope && payload.contentEquals(other.payload))

        override fun hashCode(): Int = 31 * envelope.hashCode() + payload.contentHashCode()
    }
}
