package ru.kino.dev.welcome

import kotlinx.coroutines.flow.Flow

/**
 * The remembered database, read-only.
 *
 * Read for the start route - none remembered goes to the welcome screen, one remembered to unlocking it -
 * and by the unlock screen for the names it shows. It is written by [WelcomeDatabaseRepository] alone, as
 * the last step of a successful opening or creation, so there is no way to remember a database that did
 * not open.
 *
 * The source of truth after the process was killed: the core holds nothing then, this does.
 */
interface CurrentDatabaseRepository {

    /** The remembered database, or null when there is none. Emits again whenever it changes. */
    val current: Flow<CurrentDatabase?>
}
