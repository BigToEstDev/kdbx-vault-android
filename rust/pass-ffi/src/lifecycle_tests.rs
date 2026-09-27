//! The life cycle of a database across the boundary: create, read, save, close.
//!
//! Nothing here touches a file. The bytes travel in and out exactly as they do on Android, where the file
//! belongs to SAF and this library never sees a path - so the test exercises the app's own path, on the
//! host, without a device. It also pins the shape of every answer through `contract`.

use crate::contract::assert_shape;
use crate::dispatch::run_with_bytes;
use crate::key_store;

const KDBX_SIGNATURE: [u8; 4] = [0x03, 0xd9, 0xa2, 0x9a];

// The core keeps open databases in one process wide store keyed by db_key, and cargo runs tests in
// parallel - so every test works on a key of its own. A shared key made them close each other's database
fn key_of(test: &str) -> String {
    format!("content://test/{}.kdbx", test)
}

// Argon2 with the core's defaults costs about a second per call and these tests are not about the kdf, so
// the cheapest sane parameters keep the suite fast
fn new_db_args(db_key: &str) -> String {
    format!(
        r#"{{
    "database_name": "Test",
    "database_description": "Created by the lifecycle test",
    "database_file_name": "{}",
    "file_name": "lifecycle.kdbx",
    "kdf": {{"algorithm": "Argon2id", "memory": 16384, "iterations": 2, "parallelism": 1, "salt": []}},
    "cipher_id": "Aes256",
    "password": "open sesame",
    "key_file_name": null
}}"#,
        db_key
    )
}

fn db_key_args(db_key: &str) -> String {
    format!(r#"{{"db_key":"{}"}}"#, db_key)
}

fn read_args(db_key: &str, password: &str) -> String {
    format!(
        r#"{{"db_key":"{}","password":"{}","key_file_name":null,"file_name":"lifecycle.kdbx"}}"#,
        db_key, password
    )
}

// Every test needs the key store the core asks for, and installing it twice is a no-op
fn prepare() {
    key_store::install();
}

#[test]
fn a_database_is_created_read_saved_and_closed() {
    prepare();
    let db_key = key_of("round-trip");

    // Create: the bytes of a brand new database come back for Kotlin to write through SAF
    let created = run_with_bytes("create_and_write_to_writer", &new_db_args(&db_key), None);
    assert!(
        created
            .envelope
            .contains(&format!(r#""db_key":"{}""#, db_key)),
        "{}",
        created.envelope
    );
    assert_shape("create_and_write_to_writer", &created.envelope);

    let bytes = created
        .payload
        .expect("the new database has to come back as bytes");
    assert_eq!(&bytes[..4], &KDBX_SIGNATURE, "not a kdbx file");

    // Close it, so reading it back goes through the real path rather than the cache
    let closed = run_with_bytes("close_kdbx", &db_key_args(&db_key), None);
    assert!(
        closed.envelope.contains(r#""closed":true"#),
        "{}",
        closed.envelope
    );
    assert_shape("close_kdbx", &closed.envelope);

    // Read: the same bytes, the password, and the display name the app knows
    let read = run_with_bytes("read_kdbx", &read_args(&db_key, "open sesame"), Some(bytes));
    assert!(
        read.envelope.contains(r#""database_name":"Test""#),
        "{}",
        read.envelope
    );
    assert!(
        read.envelope.contains(r#""file_name":"lifecycle.kdbx""#),
        "the name the app passed in has to come back, not one derived from the uri: {}",
        read.envelope
    );
    assert!(read.payload.is_none(), "reading produces no bytes");
    assert_shape("read_kdbx", &read.envelope);

    // Save: a fresh serialisation of what is in memory now
    let saved = run_with_bytes("save_kdbx_to_writer", &db_key_args(&db_key), None);
    assert_shape("save_kdbx_to_writer", &saved.envelope);
    let saved_bytes = saved.payload.expect("saving has to produce the file");
    assert_eq!(&saved_bytes[..4], &KDBX_SIGNATURE, "not a kdbx file");

    // The saved bytes open again. Their equality with the original is not the point and would not hold:
    // every save rewrites the encrypted payload with fresh randomness
    run_with_bytes("close_kdbx", &db_key_args(&db_key), None);
    let reread = run_with_bytes(
        "read_kdbx",
        &read_args(&db_key, "open sesame"),
        Some(saved_bytes),
    );
    assert!(
        reread.envelope.contains(r#""database_name":"Test""#),
        "{}",
        reread.envelope
    );

    run_with_bytes("close_kdbx", &db_key_args(&db_key), None);
}

#[test]
fn a_wrong_password_is_a_failure_with_its_own_kind_and_not_a_panic() {
    prepare();
    let db_key = key_of("wrong-password");

    let created = run_with_bytes("create_and_write_to_writer", &new_db_args(&db_key), None);
    let bytes = created.payload.expect("the new database has to come back");
    run_with_bytes("close_kdbx", &db_key_args(&db_key), None);

    let answer = run_with_bytes(
        "read_kdbx",
        &read_args(&db_key, "not the password"),
        Some(bytes),
    );

    assert!(
        answer.envelope.starts_with(r#"{"err":"#),
        "{}",
        answer.envelope
    );
    assert!(
        answer.envelope.contains("HeaderHmacHashCheckFailed"),
        "the kind has to name what went wrong: {}",
        answer.envelope
    );
    assert!(answer.payload.is_none(), "a refusal carries no payload");
}

#[test]
fn reading_without_the_bytes_of_a_file_is_refused() {
    prepare();

    let answer = run_with_bytes("read_kdbx", &db_key_args(&key_of("no-bytes")), None);

    assert!(
        answer.envelope.contains(r#""kind":"InvalidArguments""#),
        "{}",
        answer.envelope
    );
}

#[test]
fn an_operation_on_a_database_that_is_not_open_says_so() {
    prepare();

    let answer = run_with_bytes(
        "save_kdbx_to_writer",
        r#"{"db_key":"content://test/never-opened.kdbx"}"#,
        None,
    );

    assert!(
        answer.envelope.starts_with(r#"{"err":"#),
        "{}",
        answer.envelope
    );
    assert!(
        answer.envelope.contains("DbKeyNotFound"),
        "{}",
        answer.envelope
    );
}

#[test]
fn arguments_without_a_db_key_are_refused_rather_than_defaulted() {
    prepare();

    let answer = run_with_bytes("close_kdbx", "{}", None);

    assert!(
        answer.envelope.contains(r#""kind":"InvalidArguments""#),
        "{}",
        answer.envelope
    );
}
