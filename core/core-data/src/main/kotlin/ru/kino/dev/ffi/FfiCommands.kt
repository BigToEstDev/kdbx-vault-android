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

    const val GENERATE_PASSWORD = "generate_password"

    /** Every name this side knows. Kept in sync with the contract file by the test. */
    val ALL: Set<String> = setOf(
        GENERATE_PASSWORD,
    )
}
