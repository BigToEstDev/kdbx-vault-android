//! Settings and key files across the boundary.
//!
//! Nothing here touches the disk: since Step 29 a new key file comes back as bytes, for the app to write
//! through SAF.

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
fn a_new_key_file_comes_back_as_bytes() {
    let answer = run_with_bytes("generate_key_file", "", None);
    assert_shape("generate_key_file", &answer.envelope);

    let content = answer
        .payload
        .expect("the key file has to come back as bytes, for the app to write through SAF");
    let xml = String::from_utf8(content).expect("a generated key file is xml");
    assert!(xml.contains("<KeyFile>"), "{}", xml);
    assert!(xml.contains(r#"<Version>2.0</Version>"#), "{}", xml);
}

#[test]
fn two_new_key_files_are_different() {
    let first = run_with_bytes("generate_key_file", "", None).payload;
    let second = run_with_bytes("generate_key_file", "", None).payload;
    assert_ne!(first, second);
}

#[test]
fn generating_a_key_file_takes_no_bytes() {
    let refused = run_with_bytes("generate_key_file", "", Some(vec![1, 2, 3])).envelope;
    assert!(refused.contains("InvalidArguments"), "{}", refused);
}

#[test]
fn a_password_over_the_limit_cannot_be_set_in_the_settings() {
    let db_key = key_of("settings-password-limit");
    open_database(&db_key);

    let mut settings = call_ok("get_db_settings", json!({ "db_key": db_key }));
    settings["password"] = json!("a".repeat(257));
    settings["password_used"] = json!(true);
    settings["password_changed"] = json!(true);
    settings["meta"]["database_name"] = json!("Renamed");

    let refused = call(
        "set_db_settings",
        json!({"db_key": db_key, "settings": settings}),
    );
    assert!(
        refused.contains(r#""kind":"PasswordTooLong""#),
        "{}",
        refused
    );

    // Refused before anything changed
    let after = call_ok("get_db_settings", json!({ "db_key": db_key }));
    assert_eq!(after["meta"]["database_name"].as_str(), Some("Test"));

    close_database(&db_key);
}

#[test]
fn the_settings_of_a_database_that_is_not_open_say_so() {
    let envelope = call(
        "get_db_settings",
        json!({"db_key": "content://test/never-opened-settings.kdbx"}),
    );

    assert!(envelope.contains("DbKeyNotFound"), "{}", envelope);
}
