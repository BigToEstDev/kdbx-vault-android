//! Settings and key files across the boundary.
//!
//! The key file is the one place these tests touch the disk, because it is the one thing the core
//! still handles by path: it writes the file itself. A temporary directory stands in for the app's
//! own storage, which is where the path points on Android.

use std::env;
use std::fs;

use serde_json::{json, Value};

use crate::contract::assert_shape;
use crate::dispatch::run_with_bytes;
use crate::test_support::{close_database, key_of, ok_payload, open_database};

fn call(command: &str, args: Value) -> String {
    run_with_bytes(command, &args.to_string(), None).envelope
}

fn call_ok(command: &str, args: Value) -> Value {
    let envelope = call(command, args);
    assert!(
        envelope.starts_with(r#"{"ok":"#),
        "{} failed: {}",
        command,
        envelope
    );

    ok_payload(&envelope)
}

/// A path in the temporary directory, standing in for the app's own storage.
fn temp_path(name: &str) -> String {
    env::temp_dir()
        .join(format!("pass-ffi-{}", name))
        .to_string_lossy()
        .into_owned()
}

#[test]
fn the_settings_of_a_database_are_read_and_written_back() {
    let db_key = key_of("settings-round-trip");
    open_database(&db_key);

    let envelope = call("get_db_settings", json!({ "db_key": db_key }));
    assert_shape("get_db_settings", &envelope);

    let mut settings = ok_payload(&envelope);
    assert_eq!(
        settings["database_file_name"].as_str(),
        Some(db_key.as_str()),
        "the settings name the database they belong to: {}",
        settings
    );

    // The name of the database lives in its metadata, and editing it is what a settings screen does
    settings["meta"]["database_name"] = json!("Renamed");
    let written = call(
        "set_db_settings",
        json!({"db_key": db_key, "settings": settings}),
    );
    assert_shape("set_db_settings", &written);

    let after = call_ok("get_db_settings", json!({ "db_key": db_key }));
    assert_eq!(after["meta"]["database_name"].as_str(), Some("Renamed"));

    close_database(&db_key);
}

#[test]
fn a_key_file_is_written_and_an_existing_one_is_not_overwritten() {
    let path = temp_path("generated-key-file");
    let _ = fs::remove_file(&path);

    let envelope = call("generate_key_file", json!({ "key_file_name": path }));
    assert_shape("generate_key_file", &envelope);
    assert!(
        fs::metadata(&path).is_ok(),
        "the core writes the file itself, by path"
    );

    // A second call on the same path is refused rather than silently replacing a key file the user
    // may be using for another database - asking "replace it?" is the ui's job
    let again = call("generate_key_file", json!({ "key_file_name": path }));
    assert!(again.starts_with(r#"{"err":"#), "{}", again);
    assert!(
        again.contains("AlreadyExists"),
        "the kind has to say what is wrong: {}",
        again
    );

    let _ = fs::remove_file(&path);
}

#[test]
fn a_key_file_in_a_directory_that_does_not_exist_is_a_failure_and_not_a_panic() {
    let path = temp_path("no-such-directory/key-file");

    let envelope = call("generate_key_file", json!({ "key_file_name": path }));

    assert!(
        envelope.starts_with(r#"{"err":"#),
        "an impossible path has to come back as a refusal: {}",
        envelope
    );
}

#[test]
fn the_settings_of_a_database_that_is_not_open_say_so() {
    let envelope = call(
        "get_db_settings",
        json!({"db_key": "content://test/never-opened-settings.kdbx"}),
    );

    assert!(envelope.contains("DbKeyNotFound"), "{}", envelope);
}
