//! The settings of a database, and generating a key file.
//!
//! `DbSettings` is a core type in both directions, the fourth and last such place: its fields are
//! private, it exists only through serde, and `set_db_settings` takes the whole thing back. It is also
//! how the credentials are changed - `password_changed` / `key_file_changed` next to the new values -
//! so the ui reads the settings, edits them, and sends them in.
//!
//! A new key file comes back as bytes, for the app to write through SAF - the core no longer writes it
//! itself. One place still takes a key file by path: `key_file_name` inside `DbSettings`. Moving it to
//! content waits for the settings screen (plan/todo/android/db-credentials-change.md in pass-docs).

use serde::Deserialize;

use kdbx_rust_core::db_service::{self, DbSettings};

use crate::commands::Command;
use crate::dispatch::Answer;
use crate::errors::{ok_envelope, ErrorPayload};
use crate::handlers::lifecycle::DbKeyArgs;
use crate::handlers::{args, json_answer, reject_bytes, Done};

#[derive(Deserialize)]
struct SetSettingsArgs {
    db_key: String,
    settings: DbSettings,
}

/// The settings as they are: the kdf and cipher, which credentials are in use, and the metadata of
/// the database.
pub(crate) fn get_db_settings(
    args_json: &str,
    input: Option<Vec<u8>>,
) -> Result<Answer, ErrorPayload> {
    reject_bytes(Command::GetDbSettings, input)?;
    let args: DbKeyArgs = args(args_json)?;

    let settings = db_service::get_db_settings(&args.db_key).map_err(|e| ErrorPayload::of(&e))?;

    json_answer(&settings)
}

/// Writes edited settings back, including a changed password or key file.
///
/// It changes the database in memory only - like every other edit, it reaches the file on the next
/// save, and until then the old file still opens with the old credentials.
pub(crate) fn set_db_settings(
    args_json: &str,
    input: Option<Vec<u8>>,
) -> Result<Answer, ErrorPayload> {
    reject_bytes(Command::SetDbSettings, input)?;
    let args: SetSettingsArgs = args(args_json)?;

    db_service::set_db_settings(&args.db_key, args.settings).map_err(|e| ErrorPayload::of(&e))?;

    Done::answer()
}

/// A new key file - 32 random bytes as xml KeyFile 2.0 - handed back as bytes beside the answer.
///
/// Takes no arguments. Writing the file is the app's, through SAF "save as", and so is refusing to
/// overwrite an existing one: it may be the key of another database, and replacing it locks that
/// database for good. The core used to guard this when it wrote the file itself (`create_new`); with
/// bytes the guard moves to the ui.
pub(crate) fn generate_key_file(
    _args_json: &str,
    input: Option<Vec<u8>>,
) -> Result<Answer, ErrorPayload> {
    reject_bytes(Command::GenerateKeyFile, input)?;

    let content = db_service::generate_key_file_content().map_err(|e| ErrorPayload::of(&e))?;

    Ok(Answer {
        envelope: ok_envelope(&Done { done: true })?,
        payload: Some(content),
    })
}
