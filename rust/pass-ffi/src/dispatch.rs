//! Name of a command -> method of the core.
//!
//! Every call is `command` plus a json object of arguments, and the answer is the envelope from
//! `errors`. One function instead of a jni function per operation: the core's types already carry serde
//! derives, so json is the cheapest possible bridge, and adding an operation later touches this file
//! only - not the jni layer, not Gradle, not the `.so` name.
//!
//! Arguments are our own structs, declared here: the core's argument types carry names we do not want in
//! the Kotlin models (`camelCase` in one enum, `group_uuid` where it means the parent). Answers are the
//! core's types as they are - they are what the contract files in `contract/` pin.
//!
//! The one exception is creating a database: `NewDatabase` keeps its fields private and can only be
//! built through serde, so its own shape is the argument shape. The contract file pins it like any other.

use std::io::Cursor;

use serde::de::DeserializeOwned;
use serde::Deserialize;

use kdbx_rust_core::db_service::{self, NewDatabase, PasswordGenerationOptions};

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
        Command::CreateAndWriteToWriter => {
            reject_bytes(parsed, input)?;
            let new_db: NewDatabase = args(args_json)?;

            // The core writes the fresh database into the writer, so the writer is a buffer and the
            // bytes go back to Kotlin, which owns the file through SAF
            let mut buffer = Cursor::new(Vec::<u8>::new());
            let loaded = db_service::create_and_write_to_writer(&mut buffer, new_db)
                .map_err(|e| ErrorPayload::of(&e))?;

            Ok(Answer {
                envelope: ok_envelope(&loaded)?,
                payload: Some(buffer.into_inner()),
            })
        }

        Command::ReadKdbx => {
            let bytes = require_bytes(parsed, input)?;
            let args: ReadKdbxArgs = args(args_json)?;

            let mut reader = Cursor::new(bytes);
            let loaded = db_service::read_kdbx(
                &mut reader,
                &args.db_key,
                args.password.as_deref(),
                args.key_file_name.as_deref(),
                args.file_name.as_deref(),
            )
            .map_err(|e| ErrorPayload::of(&e))?;

            Ok(Answer {
                envelope: ok_envelope(&loaded)?,
                payload: None,
            })
        }

        Command::SaveKdbxToWriter => {
            reject_bytes(parsed, input)?;
            let args: DbKeyArgs = args(args_json)?;

            // A fresh buffer, never the previous contents of the file: `save_kdbx_to_writer` does not
            // truncate its writer, and a shorter database would otherwise keep the tail of the older one
            let mut buffer = Cursor::new(Vec::<u8>::new());
            let saved = db_service::save_kdbx_to_writer(&mut buffer, &args.db_key)
                .map_err(|e| ErrorPayload::of(&e))?;

            Ok(Answer {
                envelope: ok_envelope(&saved)?,
                payload: Some(buffer.into_inner()),
            })
        }

        Command::CloseKdbx => {
            reject_bytes(parsed, input)?;
            let args: DbKeyArgs = args(args_json)?;

            db_service::close_kdbx(&args.db_key).map_err(|e| ErrorPayload::of(&e))?;

            Ok(Answer {
                envelope: ok_envelope(&Closed { closed: true })?,
                payload: None,
            })
        }

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

/// Arguments of every command that works on an open database.
#[derive(Deserialize)]
struct DbKeyArgs {
    /// The uri of the database file, which is also its identity in the core's store
    db_key: String,
}

/// Arguments of `read_kdbx`. The bytes of the file arrive beside them, not inside.
#[derive(Deserialize)]
struct ReadKdbxArgs {
    db_key: String,
    password: Option<String>,
    /// Path to a key file in the app's own storage - the core reads key files by path, not by uri
    key_file_name: Option<String>,
    /// The name to show. Passed in because the core would otherwise derive it from `db_key`, and a SAF
    /// uri has no readable file name in it
    file_name: Option<String>,
}

#[derive(serde::Serialize)]
struct Closed {
    closed: bool,
}

fn require_bytes(command: Command, input: Option<Vec<u8>>) -> Result<Vec<u8>, ErrorPayload> {
    input.ok_or_else(|| {
        ErrorPayload::bridge(
            "InvalidArguments",
            format!("The command {:?} needs the bytes of a database", command),
        )
    })
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

// Arguments that are required: an empty object is not a valid substitute, because a missing db_key is a
// bug in the caller rather than a request for a default
fn args<T: DeserializeOwned>(args_json: &str) -> Result<T, ErrorPayload> {
    serde_json::from_str(args_json).map_err(|e| {
        ErrorPayload::bridge(
            "InvalidArguments",
            format!("The arguments could not be read: {}", e),
        )
    })
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
        crate::contract::assert_shape("generate_password", &json);
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
