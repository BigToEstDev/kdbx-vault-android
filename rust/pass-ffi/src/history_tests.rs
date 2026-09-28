//! The history of an entry across the boundary.
//!
//! History is not created by a command of its own: the core writes a version every time an entry is
//! updated. So these tests edit an entry twice and then look at what the history says - which is also
//! the only way the app will ever produce one.

use serde_json::{json, Value};

use crate::contract::assert_shape;
use crate::dispatch::run_with_bytes;
use crate::test_support::{close_database, key_of, ok_payload, open_database};

const LOGIN_TYPE_UUID: &str = "ffef5f51-7efc-4373-9eb5-382d5b501768";

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

fn root_uuid(db_key: &str) -> String {
    call_ok("groups_summary_data", json!({ "db_key": db_key }))["root_uuid"]
        .as_str()
        .expect("the tree has to name its root")
        .to_string()
}

/// An entry that has been saved `revisions` times after its creation, so it has that many old
/// versions behind it.
fn entry_with_history(db_key: &str, revisions: usize) -> String {
    let root = root_uuid(db_key);

    let mut form = call_ok(
        "new_entry_form_data_by_id",
        json!({
            "db_key": db_key,
            "entry_type_uuid": LOGIN_TYPE_UUID,
            "parent_group_uuid": root,
        }),
    );
    form["title"] = json!("Version 0");
    call_ok(
        "insert_entry_from_form_data",
        json!({"db_key": db_key, "form_data": form}),
    );

    let uuid = form["uuid"].as_str().expect("a form has a uuid").to_string();

    for revision in 1..=revisions {
        let mut current = call_ok(
            "get_entry_form_data_by_id",
            json!({"db_key": db_key, "entry_uuid": uuid}),
        );
        current["title"] = json!(format!("Version {}", revision));
        call_ok(
            "update_entry_from_form_data",
            json!({"db_key": db_key, "form_data": current}),
        );
    }

    uuid
}

#[test]
fn every_update_leaves_a_version_behind() {
    let db_key = key_of("history-summary");
    open_database(&db_key);

    let uuid = entry_with_history(&db_key, 2);

    let envelope = call(
        "history_entries_summary",
        json!({"db_key": db_key, "entry_uuid": uuid}),
    );
    assert_shape("history_entries_summary", &envelope);

    let versions = ok_payload(&envelope);
    let summaries = versions.as_array().expect("the history is a list");
    assert_eq!(summaries.len(), 2, "two updates leave two versions: {}", versions);
    assert!(
        summaries
            .iter()
            .all(|entry| entry["history_index"].is_number()),
        "a version is addressed by its index: {}",
        versions
    );

    close_database(&db_key);
}

#[test]
fn an_old_version_is_read_as_a_form() {
    let db_key = key_of("history-read");
    open_database(&db_key);

    let uuid = entry_with_history(&db_key, 1);

    let envelope = call(
        "history_entry_by_index",
        json!({"db_key": db_key, "entry_uuid": uuid, "index": 0}),
    );
    assert_shape("history_entry_by_index", &envelope);

    let old = ok_payload(&envelope);
    assert_eq!(
        old["title"].as_str(),
        Some("Version 0"),
        "the first version is the entry as it was created"
    );

    // And the entry itself is the current one, not the version
    let current = call_ok(
        "get_entry_form_data_by_id",
        json!({"db_key": db_key, "entry_uuid": uuid}),
    );
    assert_eq!(current["title"].as_str(), Some("Version 1"));

    close_database(&db_key);
}

#[test]
fn one_version_is_deleted_and_then_the_whole_history() {
    let db_key = key_of("history-delete");
    open_database(&db_key);

    let uuid = entry_with_history(&db_key, 3);

    let deleted_one = call(
        "delete_history_entry_by_index",
        json!({"db_key": db_key, "entry_uuid": uuid, "index": 0}),
    );
    assert_shape("delete_history_entry_by_index", &deleted_one);

    let after_one = call_ok(
        "history_entries_summary",
        json!({"db_key": db_key, "entry_uuid": uuid}),
    );
    assert_eq!(
        after_one.as_array().expect("a list").len(),
        2,
        "one version fewer: {}",
        after_one
    );

    let deleted_all = call(
        "delete_history_entries",
        json!({"db_key": db_key, "entry_uuid": uuid}),
    );
    assert_shape("delete_history_entries", &deleted_all);

    let after_all = call_ok(
        "history_entries_summary",
        json!({"db_key": db_key, "entry_uuid": uuid}),
    );
    assert!(
        after_all.as_array().expect("a list").is_empty(),
        "the history is gone: {}",
        after_all
    );

    // The entry itself survives losing its history
    let current = call_ok(
        "get_entry_form_data_by_id",
        json!({"db_key": db_key, "entry_uuid": uuid}),
    );
    assert_eq!(current["title"].as_str(), Some("Version 3"));

    close_database(&db_key);
}

#[test]
fn an_index_that_is_not_there_is_a_failure_and_not_a_panic() {
    let db_key = key_of("history-missing-index");
    open_database(&db_key);

    let uuid = entry_with_history(&db_key, 1);

    let envelope = call(
        "history_entry_by_index",
        json!({"db_key": db_key, "entry_uuid": uuid, "index": 42}),
    );

    assert!(
        envelope.starts_with(r#"{"err":"#),
        "an index past the end has to come back as a refusal: {}",
        envelope
    );
    assert!(envelope.contains("NotFound"), "{}", envelope);

    close_database(&db_key);
}

#[test]
fn the_history_of_an_entry_that_does_not_exist_says_so() {
    let db_key = key_of("history-missing-entry");
    open_database(&db_key);

    let envelope = call(
        "history_entries_summary",
        json!({"db_key": db_key, "entry_uuid": "00000000-0000-4000-8000-000000000000"}),
    );

    assert!(envelope.contains("NotFound"), "{}", envelope);

    close_database(&db_key);
}
