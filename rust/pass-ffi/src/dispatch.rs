//! Name of a command -> method of the core.
//!
//! Every call is `command` plus a json object of arguments, and the answer is the envelope from
//! `errors`. One function instead of a jni function per operation: the core's types already carry serde
//! derives, so json is the cheapest possible bridge, and adding an operation later touches this file
//! only - not the jni layer, not Gradle, not the `.so` name.
//!
//! Step 22 carries a single command, `generate_password`, to prove the chain end to end. The full v1
//! set, and the contract tests that keep the json shape from drifting away from Kotlin, are Step 23.

use serde::de::DeserializeOwned;

use kdbx_rust_core::db_service::PasswordGenerationOptions;

use crate::commands::Command;
use crate::errors::{err_envelope, ok_envelope, ErrorPayload};

/// What a command answers: the envelope always, and raw bytes when the command produces a file - the
/// bytes of a database on save. Kept apart from the envelope on purpose, so a database never has to be
/// base64'd into json.
pub(crate) struct Answer {
    pub(crate) envelope: String,
    pub(crate) payload: Option<Vec<u8>>,
}

/// Runs one command and returns the json envelope. Never panics on bad input: unknown commands and
/// unparseable arguments come back as `err`.
pub(crate) fn run(command: &str, args_json: &str) -> String {
    run_with_bytes(command, args_json, None).envelope
}

/// The same, for the binary boundary: arguments may carry secrets and the command may be handed the
/// bytes of a database. Commands that take no bytes reject them rather than ignore them - a caller that
/// sends bytes to the wrong command has a bug, and silence would hide it.
pub(crate) fn run_with_bytes(command: &str, args_json: &str, input: Option<Vec<u8>>) -> Answer {
    match dispatch(command, args_json, input) {
        Ok(answer) => answer,
        Err(payload) => Answer {
            envelope: err_envelope(&payload),
            payload: None,
        },
    }
}

fn dispatch(
    command: &str,
    args_json: &str,
    input: Option<Vec<u8>>,
) -> Result<Answer, ErrorPayload> {
    let parsed = Command::parse(command).ok_or_else(|| {
        ErrorPayload::bridge(
            "UnknownCommand",
            format!("There is no command named '{}'", command),
        )
    })?;

    // The match is over the enum, so a command added to `commands` without a branch here does not
    // compile - the registry and the implementation cannot drift apart
    match parsed {
        Command::GeneratePassword => {
            reject_bytes(parsed, input)?;
            // Absent or empty arguments mean the core's defaults
            let options: PasswordGenerationOptions = args_or_default(args_json)?;
            let password = options.generate().map_err(|e| ErrorPayload::of(&e))?;
            Ok(Answer {
                envelope: ok_envelope(&GeneratedPassword { password })?,
                payload: None,
            })
        }
    }
}

fn reject_bytes(command: Command, input: Option<Vec<u8>>) -> Result<(), ErrorPayload> {
    match input {
        None => Ok(()),
        Some(bytes) => Err(ErrorPayload::bridge(
            "InvalidArguments",
            format!(
                "The command {:?} takes no bytes, but {} were sent",
                command,
                bytes.len()
            ),
        )),
    }
}

#[derive(serde::Serialize)]
struct GeneratedPassword {
    password: String,
}

fn args_or_default<T: DeserializeOwned + Default>(args_json: &str) -> Result<T, ErrorPayload> {
    if args_json.trim().is_empty() {
        return Ok(T::default());
    }

    serde_json::from_str(args_json).map_err(|e| {
        ErrorPayload::bridge(
            "InvalidArguments",
            format!("The arguments could not be read: {}", e),
        )
    })
}

#[cfg(test)]
mod tests {
    use super::run;

    #[test]
    fn a_command_answers_with_the_ok_envelope() {
        let json = run(
            "generate_password",
            r#"{"length":20,"numbers":true,"lowercase_letters":true,"uppercase_letters":true,"symbols":true,"spaces":false,"exclude_similar_characters":true,"strict":true}"#,
        );
        assert!(json.starts_with(r#"{"ok":{"password":""#), "{}", json);
    }

    #[test]
    fn empty_arguments_mean_the_defaults() {
        let json = run("generate_password", "");
        assert!(json.starts_with(r#"{"ok":{"password":""#), "{}", json);
    }

    #[test]
    fn an_unknown_command_is_an_error_and_not_a_panic() {
        let json = run("no_such_command", "{}");
        assert!(json.contains(r#""kind":"UnknownCommand""#), "{}", json);
    }

    #[test]
    fn bytes_sent_to_a_command_that_takes_none_are_refused() {
        let answer = super::run_with_bytes("generate_password", "", Some(vec![1, 2, 3]));
        assert!(
            answer.envelope.contains(r#""kind":"InvalidArguments""#),
            "{}",
            answer.envelope
        );
        assert!(answer.payload.is_none());
    }

    #[test]
    fn arguments_that_do_not_parse_are_reported_as_such() {
        let json = run("generate_password", "{not json");
        assert!(json.contains(r#""kind":"InvalidArguments""#), "{}", json);
    }

    // A length of zero is rejected by the core, and that rejection has to reach the caller as data
    #[test]
    fn a_failure_inside_the_core_keeps_its_own_kind() {
        let json = run(
            "generate_password",
            r#"{"length":0,"numbers":true,"lowercase_letters":true,"uppercase_letters":false,"symbols":false,"spaces":false,"exclude_similar_characters":false,"strict":false}"#,
        );
        assert!(json.starts_with(r#"{"err":"#), "{}", json);
        assert!(!json.contains("UnknownCommand"), "{}", json);
    }
}
