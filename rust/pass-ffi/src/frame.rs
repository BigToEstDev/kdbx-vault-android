//! The frame that carries an answer across the binary boundary.
//!
//! ```text
//! [4 bytes: length of the envelope, big endian][envelope utf-8][payload bytes]
//! ```
//!
//! One value instead of two calls: the envelope and the bytes it describes travel together, so there is
//! no slot in the library to fetch bytes from afterwards and nothing to leak if Kotlin never asks. Big
//! endian because that is what `ByteBuffer` and `DataInputStream` read by default on the other side.
//!
//! The payload is whatever the command produced - for a save, the bytes of the database. A refusal has no
//! payload at all, so a failed call is a frame of just the envelope.

/// Builds the frame. The envelope is always present; the payload only when the command produced bytes.
pub(crate) fn build(envelope: &str, payload: Option<&[u8]>) -> Vec<u8> {
    let envelope = envelope.as_bytes();
    let payload = payload.unwrap_or(&[]);

    let mut frame = Vec::with_capacity(4 + envelope.len() + payload.len());
    // A length that does not fit in u32 would mean a json envelope of four gigabytes: impossible here,
    // and truncating silently would be worse than the panic this would be in debug
    frame.extend_from_slice(&(envelope.len() as u32).to_be_bytes());
    frame.extend_from_slice(envelope);
    frame.extend_from_slice(payload);
    frame
}

#[cfg(test)]
mod tests {
    use super::build;

    #[test]
    fn a_frame_starts_with_the_length_of_the_envelope() {
        let frame = build(r#"{"ok":1}"#, None);

        assert_eq!(&frame[..4], &[0, 0, 0, 8]);
        assert_eq!(&frame[4..], br#"{"ok":1}"#);
    }

    #[test]
    fn the_payload_follows_the_envelope_untouched() {
        let frame = build("{}", Some(&[7, 8, 9]));

        assert_eq!(&frame[..4], &[0, 0, 0, 2]);
        assert_eq!(&frame[4..6], b"{}");
        assert_eq!(&frame[6..], &[7, 8, 9]);
    }

    #[test]
    fn an_empty_payload_and_no_payload_look_the_same() {
        assert_eq!(build("{}", None), build("{}", Some(&[])));
    }
}
