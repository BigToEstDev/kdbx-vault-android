package ru.kino.dev.welcome.data

import kotlinx.coroutines.flow.MutableStateFlow
import ru.kino.dev.welcome.CurrentDatabase
import ru.kino.dev.welcome.CurrentDatabaseRepository

/** The remembered database in memory; writes go to the shared [journal] as `"remember <uri>"`. */
internal class FakeCurrentDatabaseStore(
    private val journal: MutableList<String>,
) : CurrentDatabaseRepository, CurrentDatabaseWriter {

    override val current = MutableStateFlow<CurrentDatabase?>(null)

    /** Thrown by [remember] when set - the store could not be written. */
    var failure: Throwable? = null

    override suspend fun remember(database: CurrentDatabase) {
        journal += "remember ${database.uri}"
        failure?.let { throw it }
        current.value = database
    }
}
