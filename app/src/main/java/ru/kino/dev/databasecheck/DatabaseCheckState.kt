package ru.kino.dev.databasecheck

import ru.kino.dev.core.RecentDatabase

/**
 * What the check screen shows.
 *
 * A screen for Step 23: it drives a real database through the bridge - create, save, close, open - on a
 * file the user picks, because that is the part no unit test can cover. It goes away when the real screens
 * of the app arrive.
 */
data class DatabaseCheckState(
    /** Uri of the document the user picked, or null while nothing is picked */
    val uri: String? = null,
    /** Databases opened before: tapping one opens it without the picker, which is the real test of the
     * permissions surviving a restart */
    val recent: List<RecentDatabase> = emptyList(),
    /** Name of the open database as the core reports it */
    val databaseName: String? = null,
    /** File name the app passed in and got back */
    val fileName: String? = null,
    val password: String = DEFAULT_PASSWORD,
    val isOpen: Boolean = false,
    val isBusy: Boolean = false,
    /** The last thing that happened, for the log on screen */
    val log: List<String> = emptyList(),
    /** `kind: message` of the last failure */
    val error: String? = null,
) {
    companion object {
        // A fixed password keeps the check to two taps; it is a throwaway database on a throwaway file
        const val DEFAULT_PASSWORD = "check me"

        /**
         * How many entries the fill button adds.
         *
         * A test number, not a product one: merge walks the whole tree, and on a database of a root and a
         * recycle bin there is nothing to see in the log. Big enough that logcat and the clock say
         * something, small enough to wait for on a phone.
         */
        const val ENTRIES_TO_FILL = 100
    }
}
