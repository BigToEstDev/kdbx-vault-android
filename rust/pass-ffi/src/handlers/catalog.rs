//! What the home screen is built from: the categories, the entry types, and emptying the bin.
//!
//! Categories are read only in v1. A group can be *marked* as a category, but marking one needs a
//! screen of its own, and without it the command would have no caller - so `mark_group_as_category`
//! stays behind the boundary. Custom entry types are the same story: showing the standard types is
//! what creating an entry needs, while an editor for making new ones is a screen v1 does not have.

use serde::Deserialize;

use kdbx_rust_core::db_service::{self, EntryCategoryGrouping};

use crate::commands::Command;
use crate::dispatch::Answer;
use crate::errors::ErrorPayload;
use crate::handlers::lifecycle::DbKeyArgs;
use crate::handlers::{args, json_answer, reject_bytes, Done};

#[derive(Deserialize)]
struct CategoriesArgs {
    db_key: String,
    grouping: Grouping,
}

/// How the entries are grouped into tiles on the home screen.
///
/// The core names these `AsGroupCategories` / `AsTypes` / `AsTags`; the wire keeps them snake_case
/// like every other argument.
#[derive(Deserialize)]
enum Grouping {
    // The wire names are spelled out rather than derived, because the variants here are named for
    // what they group by while the wire keeps the core's "as ..." wording
    #[serde(rename = "as_group_categories")]
    GroupCategories,
    #[serde(rename = "as_types")]
    Types,
    #[serde(rename = "as_tags")]
    Tags,
}

impl From<Grouping> for EntryCategoryGrouping {
    fn from(grouping: Grouping) -> Self {
        match grouping {
            Grouping::GroupCategories => EntryCategoryGrouping::AsGroupCategories,
            Grouping::Types => EntryCategoryGrouping::AsTypes,
            Grouping::Tags => EntryCategoryGrouping::AsTags,
        }
    }
}

/// The tiles of the home screen with their counts - all entries, favourites, deleted, and then the
/// groups, types or tags, depending on how the user asked for them to be grouped.
pub(crate) fn combined_category_details(
    args_json: &str,
    input: Option<Vec<u8>>,
) -> Result<Answer, ErrorPayload> {
    reject_bytes(Command::CombinedCategoryDetails, input)?;
    let args: CategoriesArgs = args(args_json)?;

    let categories = db_service::combined_category_details(&args.db_key, &args.grouping.into())
        .map_err(|e| ErrorPayload::of(&e))?;

    json_answer(&categories)
}

/// The entry types to choose from when creating an entry - login, card, passport and the rest.
pub(crate) fn entry_type_headers(
    args_json: &str,
    input: Option<Vec<u8>>,
) -> Result<Answer, ErrorPayload> {
    reject_bytes(Command::EntryTypeHeaders, input)?;
    let args: DbKeyArgs = args(args_json)?;

    let headers = db_service::entry_type_headers(&args.db_key).map_err(|e| ErrorPayload::of(&e))?;

    json_answer(&headers)
}

/// Empties the recycle bin: everything in it goes for good, with no undo.
///
/// Moving things *into* the bin is part of groups and entries - this is only the one irreversible
/// step at the end.
pub(crate) fn empty_trash(args_json: &str, input: Option<Vec<u8>>) -> Result<Answer, ErrorPayload> {
    reject_bytes(Command::EmptyTrash, input)?;
    let args: DbKeyArgs = args(args_json)?;

    db_service::empty_trash(&args.db_key).map_err(|e| ErrorPayload::of(&e))?;

    Done::answer()
}
