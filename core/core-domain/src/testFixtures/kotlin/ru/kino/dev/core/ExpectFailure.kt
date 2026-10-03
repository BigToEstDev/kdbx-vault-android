package ru.kino.dev.core

/**
 * Runs [block] and returns the failure of type [T] it ended with.
 *
 * Inline, so the block may call suspend functions from inside `runTest` - junit's `assertThrows` takes a
 * plain lambda and would need a second, blocking coroutine for that.
 */
inline fun <reified T : Throwable> expectFailure(block: () -> Unit): T {
    try {
        block()
    } catch (e: Throwable) {
        if (e is T) return e
        throw AssertionError("expected ${T::class.simpleName}, got $e", e)
    }
    throw AssertionError("expected ${T::class.simpleName}, nothing was thrown")
}
