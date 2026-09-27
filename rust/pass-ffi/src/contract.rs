//! The shape of an answer, pinned in a file both sides read.
//!
//! A contract test does not check values - they change with every run - but the **shape**: which keys an
//! answer has and of what type. That is exactly what breaks Kotlin silently when a field is renamed in
//! the core, and exactly what a compiler cannot see across the boundary.
//!
//! The files live in `contract/` next to `commands.json` and are part of the source. When a shape
//! legitimately changes, rerun the tests with `UPDATE_CONTRACT=1`, look at the diff, and commit it - the
//! Kotlin side then fails until its own model is updated.

use serde_json::{json, Value};

/// Replaces every value with the name of its type, recursively.
pub(crate) fn shape(value: &Value) -> Value {
    match value {
        Value::Object(fields) => Value::Object(
            fields
                .iter()
                .map(|(key, value)| (key.clone(), shape(value)))
                .collect(),
        ),
        // Only the first element: a list is homogeneous here, and pinning its length would make the
        // file depend on the test data
        Value::Array(items) => match items.first() {
            Some(first) => json!([shape(first)]),
            None => json!([]),
        },
        Value::String(_) => json!("string"),
        Value::Number(_) => json!("number"),
        Value::Bool(_) => json!("bool"),
        // A null tells us nothing about the type, and an Option that happens to be None in the test
        // would pin the wrong shape. The name says so out loud
        Value::Null => json!("null-in-this-sample"),
    }
}

/// Compares the `ok` payload of an envelope with `contract/<command>.json`.
pub(crate) fn assert_shape(command: &str, envelope: &str) {
    let parsed: Value = serde_json::from_str(envelope).expect("the envelope is not json");
    let ok = parsed
        .get("ok")
        .unwrap_or_else(|| panic!("the answer of {} carries no 'ok': {}", command, envelope));

    let expected = serde_json::to_string_pretty(&shape(ok)).unwrap() + "\n";
    let path = format!("{}/contract/{}.json", env!("CARGO_MANIFEST_DIR"), command);

    if std::env::var("UPDATE_CONTRACT").is_ok() {
        std::fs::write(&path, &expected).unwrap();
    }

    let actual = std::fs::read_to_string(&path).unwrap_or_default();
    assert_eq!(
        actual, expected,
        "the shape of {} changed; check the diff, rerun with UPDATE_CONTRACT=1 and update the Kotlin model",
        command
    );
}

#[cfg(test)]
mod tests {
    use super::shape;
    use serde_json::json;

    #[test]
    fn every_value_becomes_the_name_of_its_type() {
        let sample = json!({"name": "db", "count": 3, "locked": false, "tags": ["a", "b"]});

        assert_eq!(
            shape(&sample),
            json!({"name": "string", "count": "number", "locked": "bool", "tags": ["string"]})
        );
    }

    #[test]
    fn nesting_is_kept_so_a_moved_field_is_visible() {
        let sample = json!({"db": {"key": "uri", "history": [{"index": 1}]}});

        assert_eq!(
            shape(&sample),
            json!({"db": {"key": "string", "history": [{"index": "number"}]}})
        );
    }
}
