package ru.kino.dev.ffi

/**
 * The raw jni surface of `libpass_ffi.so`.
 *
 * Nothing but declarations: no threading, no parsing, no error handling. The names and the signatures
 * here are matched by hand in `rust/pass-ffi/src/lib.rs`, where the exported symbol carries this
 * package (`Java_ru_kino_dev_ffi_PassFfi_invoke`) - so **renaming this package or this object means
 * renaming the Rust functions too**. The package must also stay free of underscores, because jni encodes
 * `_` in a name as `_1`.
 *
 * Use [NativeCoreFfi] instead of calling this directly: it moves the work off the caller's thread and
 * turns the json envelope into a result or a [ru.kino.dev.core.CoreException].
 */
internal object PassFfi {

    init {
        // Loads lib/<abi>/libpass_ffi.so from the apk. Throws UnsatisfiedLinkError if the apk was built
        // without the native part, which is why Gradle builds it as part of the app (see the
        // rust-bridge convention plugin)
        System.loadLibrary("pass_ffi")
    }

    /** Version of the crate plus its build timestamp, so a stale .so is visible in the log. */
    external fun buildInfo(): String

    /**
     * Runs one command of the core.
     *
     * @param command name of the command, e.g. `generate_password`
     * @param argsJson json object of arguments, or an empty string for the defaults
     * @return the json envelope: `{"ok": ...}` or `{"err": {"kind", "message"}}`
     */
    external fun invoke(command: String, argsJson: String): String
}
