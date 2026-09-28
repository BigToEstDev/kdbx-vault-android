package ru.kino.dev.ffi

/**
 * The names of the commands, as the bridge knows them.
 *
 * The same list lives in `rust/pass-ffi/src/commands.rs`, and Rust writes it out to
 * `rust/pass-ffi/contract/commands.json`. `FfiCommandsTest` reads that file and compares it with [ALL],
 * because a misspelled name here compiles perfectly and fails only on a device.
 *
 * Adding a command means: a variant in Rust, a branch in its dispatcher, the regenerated contract file,
 * and an entry here.
 */
internal object FfiCommands {

    // The life cycle of a database file. The names are the core's own method names
    const val CREATE_DATABASE = "create_and_write_to_writer"
    const val READ_DATABASE = "read_kdbx"
    const val SAVE_DATABASE = "save_kdbx_to_writer"
    const val CLOSE_DATABASE = "close_kdbx"

    // Groups: the tree the ui draws, one group for its edit screen, and the operations that change it
    const val GROUPS_SUMMARY_DATA = "groups_summary_data"
    const val GET_GROUP_BY_ID = "get_group_by_id"
    const val NEW_BLANK_GROUP = "new_blank_group"
    const val NEW_BLANK_GROUP_WITH_PARENT = "new_blank_group_with_parent"
    const val INSERT_GROUP = "insert_group"
    const val UPDATE_GROUP = "update_group"
    const val MOVE_GROUP = "move_group"
    const val SORT_SUB_GROUPS = "sort_sub_groups"
    const val CLONE_GROUP = "clone_group"
    const val MOVE_GROUP_TO_RECYCLE_BIN = "move_group_to_recycle_bin"
    const val REMOVE_GROUP_PERMANENTLY = "remove_group_permanently"

    // Entries: the list of a category, the form behind one entry, and what changes it
    const val ENTRY_SUMMARY_DATA = "entry_summary_data"
    const val GET_ENTRY_FORM_DATA_BY_ID = "get_entry_form_data_by_id"
    const val ENTRY_KEY_VALUE_FIELDS = "entry_key_value_fields"
    const val NEW_ENTRY_FORM_DATA_BY_ID = "new_entry_form_data_by_id"
    const val INSERT_ENTRY_FROM_FORM_DATA = "insert_entry_from_form_data"
    const val UPDATE_ENTRY_FROM_FORM_DATA = "update_entry_from_form_data"
    const val MOVE_ENTRY = "move_entry"
    const val CLONE_ENTRY = "clone_entry"
    const val MOVE_ENTRY_TO_RECYCLE_BIN = "move_entry_to_recycle_bin"
    const val REMOVE_ENTRY_PERMANENTLY = "remove_entry_permanently"

    const val GENERATE_PASSWORD = "generate_password"

    /** Every name this side knows. Kept in sync with the contract file by the test. */
    val ALL: Set<String> = setOf(
        CREATE_DATABASE,
        READ_DATABASE,
        SAVE_DATABASE,
        CLOSE_DATABASE,
        GROUPS_SUMMARY_DATA,
        GET_GROUP_BY_ID,
        NEW_BLANK_GROUP,
        NEW_BLANK_GROUP_WITH_PARENT,
        INSERT_GROUP,
        UPDATE_GROUP,
        MOVE_GROUP,
        SORT_SUB_GROUPS,
        CLONE_GROUP,
        MOVE_GROUP_TO_RECYCLE_BIN,
        REMOVE_GROUP_PERMANENTLY,
        ENTRY_SUMMARY_DATA,
        GET_ENTRY_FORM_DATA_BY_ID,
        ENTRY_KEY_VALUE_FIELDS,
        NEW_ENTRY_FORM_DATA_BY_ID,
        INSERT_ENTRY_FROM_FORM_DATA,
        UPDATE_ENTRY_FROM_FORM_DATA,
        MOVE_ENTRY,
        CLONE_ENTRY,
        MOVE_ENTRY_TO_RECYCLE_BIN,
        REMOVE_ENTRY_PERMANENTLY,
        GENERATE_PASSWORD,
    )
}
