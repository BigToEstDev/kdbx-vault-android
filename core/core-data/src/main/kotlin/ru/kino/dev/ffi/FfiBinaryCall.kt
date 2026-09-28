package ru.kino.dev.ffi

/**
 * The binary boundary, with the wiping of arguments in one place.
 *
 * Arguments are built as bytes because a password inside a String cannot be cleared on the jvm - the
 * runtime is free to keep copies of it until the heap is reused. This side clears its copy, Rust clears
 * the one it makes, and what remains is out of our reach.
 *
 * The wipe runs in `finally`: a failed call is exactly when a secret must not be left behind.
 */
internal object FfiBinaryCall {

    /**
     * Runs [command] with [argsJson] as its arguments and optional [input] bytes.
     *
     * [invoke] exists so the wiping and the framing can be tested on the jvm, without the native
     * library; nothing but a test passes it.
     */
    fun call(
        command: String,
        argsJson: String,
        input: ByteArray? = null,
        invoke: (String, ByteArray, ByteArray?) -> ByteArray = PassFfi::invokeBinary,
    ): FfiFrame.Parsed {
        val args = argsJson.toByteArray(Charsets.UTF_8)
        try {
            return FfiFrame.split(invoke(command, args, input))
        } finally {
            args.fill(0)
        }
    }
}
