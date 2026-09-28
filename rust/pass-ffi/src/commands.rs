//! The names of the commands, in one place.
//!
//! A command arrives from Kotlin as a string, and a string is exactly what nothing checks. This enum is
//! the only place where a name is spelled: `dispatch` matches on the enum, so a variant without a branch
//! does not compile, and a name that is not here becomes `UnknownCommand` before any argument is read.
//!
//! The list is also written out to `contract/commands.json`, which a Kotlin test reads: that is what
//! catches a typo on the Kotlin side, where the compiler cannot help. `names_match_the_contract_file`
//! below fails when the file and this enum drift apart.
//!
//! A `macro_rules!` rather than `#[derive(Deserialize)]` with `rename_all`: the wire name has to be
//! available as a plain string too - for the contract file and for error messages - and serde alone
//! would give parsing without ever handing us the list.

macro_rules! commands {
    ($($variant:ident => $name:literal),+ $(,)?) => {
        /// Every command the bridge answers. Grows together with `dispatch`, one variant per operation.
        #[derive(Debug, Clone, Copy, PartialEq, Eq)]
        pub(crate) enum Command {
            $($variant),+
        }

        impl Command {
            /// The name as it travels from Kotlin, or `None` when there is no such command.
            pub(crate) fn parse(name: &str) -> Option<Self> {
                match name {
                    $($name => Some(Command::$variant),)+
                    _ => None,
                }
            }

            /// Every name, sorted, as the contract file holds them. Only the contract test needs the
            /// list at runtime - the app looks names up one at a time - so it is not built into the
            /// library.
            #[cfg(test)]
            pub(crate) fn all_names() -> Vec<&'static str> {
                let mut names = vec![$($name),+];
                names.sort_unstable();
                names
            }
        }
    };
}

// Commands are added here together with their branch in `dispatch`, never ahead of it: a name that
// answers nothing is worse than a missing name, because Kotlin cannot tell it from a working one.
commands! {
    // The life cycle of a database file. The names are the core's own method names, so a command can be
    // traced to the function it calls without a table in between
    CreateAndWriteToWriter => "create_and_write_to_writer",
    ReadKdbx => "read_kdbx",
    SaveKdbxToWriter => "save_kdbx_to_writer",
    CloseKdbx => "close_kdbx",
    LockKdbx => "lock_kdbx",
    UnlockKdbx => "unlock_kdbx",
    IsDbLocked => "is_db_locked",
    IsDbOpened => "is_db_opened",
    RenameDbKey => "rename_db_key",
    KdbxContextStatuses => "kdbx_context_statuses",

    // Groups: the tree the ui draws, one group for its edit screen, and the operations that change it
    GroupsSummaryData => "groups_summary_data",
    GetGroupById => "get_group_by_id",
    NewBlankGroup => "new_blank_group",
    NewBlankGroupWithParent => "new_blank_group_with_parent",
    InsertGroup => "insert_group",
    UpdateGroup => "update_group",
    MoveGroup => "move_group",
    SortSubGroups => "sort_sub_groups",
    CloneGroup => "clone_group",
    MoveGroupToRecycleBin => "move_group_to_recycle_bin",
    RemoveGroupPermanently => "remove_group_permanently",

    // Entries: the list of a category, the form behind one entry, and what changes it
    EntrySummaryData => "entry_summary_data",
    GetEntryFormDataById => "get_entry_form_data_by_id",
    EntryKeyValueFields => "entry_key_value_fields",
    NewEntryFormDataById => "new_entry_form_data_by_id",
    InsertEntryFromFormData => "insert_entry_from_form_data",
    UpdateEntryFromFormData => "update_entry_from_form_data",
    MoveEntry => "move_entry",
    CloneEntry => "clone_entry",
    MoveEntryToRecycleBin => "move_entry_to_recycle_bin",
    RemoveEntryPermanently => "remove_entry_permanently",

    // The history of an entry: kdbx keeps old versions inside the entry, addressed by index
    HistoryEntriesSummary => "history_entries_summary",
    HistoryEntryByIndex => "history_entry_by_index",
    DeleteHistoryEntryByIndex => "delete_history_entry_by_index",
    DeleteHistoryEntries => "delete_history_entries",

    // Two factor codes: the tokens a list shows, and the 2fa settings of one entry
    EntryListCurrentOtps => "entry_list_current_otps",
    FormOtpUrl => "form_otp_url",
    IsValidOtpUrl => "is_valid_otp_url",
    SetEntryOtp => "set_entry_otp",
    DeleteEntryOtp => "delete_entry_otp",

    // Search over every field of every entry, and the tags a picker offers
    SearchTerm => "search_term",
    CollectEntryGroupTags => "collect_entry_group_tags",

    // The settings of a database, and the key file the core writes by path
    GetDbSettings => "get_db_settings",
    SetDbSettings => "set_db_settings",
    GenerateKeyFile => "generate_key_file",

    // Has the file changed under us, and merging it back in when it has. All but one carry the bytes
    // of the file on disk
    VerifyDbFileChecksum => "verify_db_file_checksum",
    CalculateAndSetDbFileChecksum => "calculate_and_set_db_file_checksum",
    DbChecksumHash => "db_checksum_hash",
    MergeKdbxWithReader => "merge_kdbx_with_reader",

    GeneratePassword => "generate_password",
}

#[cfg(test)]
mod tests {
    use super::Command;

    const CONTRACT_FILE: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/contract/commands.json");

    #[test]
    fn a_known_name_parses_and_an_unknown_one_does_not() {
        assert_eq!(
            Command::parse("generate_password"),
            Some(Command::GeneratePassword)
        );
        assert_eq!(Command::parse("generate_passwor"), None);
        assert_eq!(Command::parse(""), None);
    }

    // The contract file is what Kotlin reads, so it is part of the source, not a build artefact. When
    // this fails after adding a command, rerun with UPDATE_CONTRACT=1 to rewrite the file, then commit
    // it - the Kotlin test on the other side then fails until its own list is updated too.
    #[test]
    fn names_match_the_contract_file() {
        let expected = serde_json::to_string_pretty(&Command::all_names()).unwrap() + "\n";

        if std::env::var("UPDATE_CONTRACT").is_ok() {
            std::fs::write(CONTRACT_FILE, &expected).unwrap();
        }

        let actual = std::fs::read_to_string(CONTRACT_FILE).unwrap_or_default();
        assert_eq!(
            actual, expected,
            "contract/commands.json is out of date; rerun with UPDATE_CONTRACT=1 and commit the file"
        );
    }
}
