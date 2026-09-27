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

    const val GENERATE_PASSWORD = "generate_password"

    /** Every name this side knows. Kept in sync with the contract file by the test. */
    val ALL: Set<String> = setOf(
        CREATE_DATABASE,
        READ_DATABASE,
        SAVE_DATABASE,
        CLOSE_DATABASE,
        GENERATE_PASSWORD,
    )
}
