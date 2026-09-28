//! The settings of a database, and generating a key file.
//!
//! `DbSettings` is a core type in both directions, the fourth and last such place: its fields are
//! private, it exists only through serde, and `set_db_settings` takes the whole thing back. It is also
//! how the credentials are changed - `password_changed` / `key_file_changed` next to the new values -
//! so the ui reads the settings, edits them, and sends them in.
//!
//! The key file is the one thing the core still takes by path rather than through the boundary: it
//! reads and writes it itself. On Android that path has to be inside the app's own storage, never a
//! SAF uri, so picking a key file means copying it in first.

use serde::Deserialize;

use kdbx_rust_core::db_service::{self, DbSettings};

use crate::commands::Command;
use crate::dispatch::Answer;
use crate::errors::ErrorPayload;
use crate::handlers::lifecycle::DbKeyArgs;
use crate::handlers::{args, json_answer, reject_bytes, Done};

#[derive(Deserialize)]
struct SetSettingsArgs {
    db_key: String,
    settings: DbSettings,
}

#[derive(Deserialize)]
struct KeyFileArgs {
    /// Full path inside the app's own storage - the core writes the file itself
    key_file_name: String,
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

/// Writes a new key file at the given path.
///
/// An existing file is not overwritten: the core answers `AlreadyExists`, and asking "replace it?" is
/// the ui's job - this is a file the user may already be using for another database.
pub(crate) fn generate_key_file(
    args_json: &str,
    input: Option<Vec<u8>>,
) -> Result<Answer, ErrorPayload> {
    reject_bytes(Command::GenerateKeyFile, input)?;
    let args: KeyFileArgs = args(args_json)?;

    db_service::generate_key_file(&args.key_file_name).map_err(|e| ErrorPayload::of(&e))?;

    Done::answer()
}
