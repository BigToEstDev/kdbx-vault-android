//! Creating, reading, saving and closing the database file.
//!
//! The file itself belongs to Android: the app picks it through SAF and this library never sees a path.
//! So a database travels as bytes beside the arguments - in for reading, out for creating and saving -
//! and the core reads and writes a `Cursor` over them. Both of its functions are generic over
//! `Read + Seek` / `Read + Write + Seek`, so nothing in the core had to change for this.
//!
//! Why bytes rather than the file descriptor SAF could hand over: `File::from_raw_fd` is unsafe by
//! contract and would put the one unsafe block of the project in the middle of the data path, together
//! with the ownership rule that comes with `detachFd`. The price is the database in memory twice for the
//! length of a call - it is a few megabytes, and they are the *encrypted* bytes of the file.

use std::io::Cursor;

use serde::Deserialize;

use kdbx_rust_core::db_service::{self, NewDatabase};

use crate::commands::Command;
use crate::dispatch::Answer;
use crate::errors::{ok_envelope, ErrorPayload};
use crate::handlers::{args, json_answer, reject_bytes, require_bytes, Done};

/// Arguments of every command that works on an open database.
#[derive(Deserialize)]
pub(crate) struct DbKeyArgs {
    /// The uri of the database file, which is also its identity in the core's store
    pub(crate) db_key: String,
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

/// Creates a database and hands its bytes back for Kotlin to write through SAF.
///
/// The arguments are `NewDatabase` itself, the one place where a core type is the argument type: its
/// fields are private and it can only be built through serde, so its own shape is the shape of the
/// arguments. The contract file pins it like any other.
pub(crate) fn create(args_json: &str, input: Option<Vec<u8>>) -> Result<Answer, ErrorPayload> {
    reject_bytes(Command::CreateAndWriteToWriter, input)?;
    let new_db: NewDatabase = args(args_json)?;

    let mut buffer = Cursor::new(Vec::<u8>::new());
    let loaded =
        db_service::create_and_write_to_writer(&mut buffer, new_db).map_err(|e| ErrorPayload::of(&e))?;

    Ok(Answer {
        envelope: ok_envelope(&loaded)?,
        payload: Some(buffer.into_inner()),
    })
}

/// Opens the database whose bytes were sent along, and leaves it open in the core's store.
pub(crate) fn read(args_json: &str, input: Option<Vec<u8>>) -> Result<Answer, ErrorPayload> {
    let bytes = require_bytes(Command::ReadKdbx, input)?;
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

    json_answer(&loaded)
}

/// Writes the open database into a buffer and hands the bytes back.
pub(crate) fn save(args_json: &str, input: Option<Vec<u8>>) -> Result<Answer, ErrorPayload> {
    reject_bytes(Command::SaveKdbxToWriter, input)?;
    let args: DbKeyArgs = args(args_json)?;

    // A fresh buffer, never the previous contents of the file: `save_kdbx_to_writer` does not truncate
    // its writer, and a shorter database would otherwise keep the tail of the older one
    let mut buffer = Cursor::new(Vec::<u8>::new());
    let saved =
        db_service::save_kdbx_to_writer(&mut buffer, &args.db_key).map_err(|e| ErrorPayload::of(&e))?;

    Ok(Answer {
        envelope: ok_envelope(&saved)?,
        payload: Some(buffer.into_inner()),
    })
}

/// Drops the database from the core's store, keys and all.
pub(crate) fn close(args_json: &str, input: Option<Vec<u8>>) -> Result<Answer, ErrorPayload> {
    reject_bytes(Command::CloseKdbx, input)?;
    let args: DbKeyArgs = args(args_json)?;

    db_service::close_kdbx(&args.db_key).map_err(|e| ErrorPayload::of(&e))?;

    Done::answer()
}
