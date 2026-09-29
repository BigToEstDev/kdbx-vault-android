package ru.kino.dev.databasecheck

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer
import ru.kino.dev.core.CoreException
import ru.kino.dev.core.DatabaseFileException
import ru.kino.dev.core.DatabaseRepository
import ru.kino.dev.core.MergeSummary
import ru.kino.dev.core.NativeCore
import ru.kino.dev.core.OpenedDatabase
import ru.kino.dev.core.RecentDatabases
import javax.inject.Inject

/**
 * Drives one database through the bridge: create it in a chosen file, save it, close it, open it again.
 *
 * The code is unremarkable on purpose. What this screen is for is the part no unit test can reach: a real
 * document from a real provider, where a write that does not truncate, or a permission that dies with the
 * process, shows up and nowhere else.
 */
@HiltViewModel
class DatabaseCheckProcessor @Inject constructor(
    private val databases: DatabaseRepository,
    // Reached past the repository on purpose: filling a database with entries is not a user intention,
    // it is how this screen makes one big enough for a merge to say anything
    private val core: NativeCore,
    private val recent: RecentDatabases,
) :
    ViewModel(),
    OrbitContainerHost<DatabaseCheckState, DatabaseCheckState, Nothing> {

    override val container = orbitContainer<DatabaseCheckState, Nothing>(DatabaseCheckState()) {
        observeRecent()
    }

    private fun observeRecent() = intent {
        recent.all.collect { remembered ->
            reduce { state.copy(recent = remembered) }
        }
    }

    /** A document was just created by the picker: make a database in it. */
    fun onFileCreated(uri: String) = intent {
        begin("creating a database in the chosen file")

        val outcome = runCatching {
            databases.create(
                uri = uri,
                databaseName = DATABASE_NAME,
                password = state.password,
            )
        }

        reduce {
            outcome.fold(
                onSuccess = { state.opened(uri, it, "created") },
                onFailure = { state.failed(it) },
            )
        }
    }

    /**
     * A document was picked, or a remembered one was tapped: open the database in it.
     *
     * The same path for both on purpose - a remembered uri is an ordinary uri, and if the access to it did
     * not survive the restart, that failure is exactly what this screen is here to show.
     */
    fun onFilePicked(uri: String) = intent {
        begin("opening $uri")

        val outcome = runCatching { databases.open(uri = uri, password = state.password) }

        // A file that cannot be read any more has no business staying in the list: the document was deleted,
        // the card removed, the provider uninstalled
        outcome.exceptionOrNull()
            ?.takeIf { it is DatabaseFileException }
            ?.let { recent.forget(uri) }

        reduce {
            outcome.fold(
                onSuccess = { state.opened(uri, it, "opened") },
                onFailure = { state.failed(it) },
            )
        }
    }

    fun onSave() = intent {
        val uri = state.uri ?: return@intent
        begin("saving")

        val outcome = runCatching { databases.save(uri) }

        reduce {
            outcome.fold(
                onSuccess = { state.done("saved, the file was replaced") },
                onFailure = { state.failed(it) },
            )
        }
    }

    fun onClose() = intent {
        val uri = state.uri ?: return@intent
        begin("closing")

        val outcome = runCatching { databases.close(uri) }

        reduce {
            outcome.fold(
                onSuccess = {
                    state.done("closed, the database left the process")
                        .copy(isOpen = false, databaseName = null, fileName = null)
                },
                onFailure = { state.failed(it) },
            )
        }
    }

    /**
     * Fills the open database with entries and saves it.
     *
     * Separate from the merge button because it is the slow half: a hundred entries is a hundred round
     * trips through the bridge, and there is no reason to pay for it again on every merge.
     */
    fun onFill() = intent {
        val uri = state.uri ?: return@intent
        begin("adding ${DatabaseCheckState.ENTRIES_TO_FILL} entries")

        val outcome = runCatching {
            val root = core.rootGroupUuid(uri)
            repeat(DatabaseCheckState.ENTRIES_TO_FILL) { index ->
                core.addEntry(dbKey = uri, parentGroupUuid = root, title = "Entry ${index + 1}")
            }
            databases.save(uri)
        }

        reduce {
            outcome.fold(
                onSuccess = { state.done("added ${DatabaseCheckState.ENTRIES_TO_FILL} entries and saved") },
                onFailure = { state.failed(it) },
            )
        }
    }

    /**
     * Merges the file into the open database - which is the same database, so nothing changes.
     *
     * That is the point: merge walks both trees whatever the outcome, and this is the loudest thing the
     * core does. What it is here to show is the clock and logcat, not the result.
     */
    fun onMerge() = intent {
        val uri = state.uri ?: return@intent
        begin("merging the file back in")

        val outcome = runCatching {
            val changed = databases.hasChangedElsewhere(uri)
            val summary = databases.merge(uri)
            changed to summary
        }

        reduce {
            outcome.fold(
                onSuccess = { (changed, summary) ->
                    val file = if (changed) "the file differs" else "the file still matches"
                    state.done("$file; ${summary.describe()}")
                },
                onFailure = { state.failed(it) },
            )
        }
    }

    fun onPasswordChange(password: String) = intent {
        reduce { state.copy(password = password) }
    }

    private suspend fun org.orbitmvi.orbit.syntax.Syntax<DatabaseCheckState, Nothing>.begin(what: String) {
        reduce { state.copy(isBusy = true, error = null, log = state.log + what) }
    }

    private fun DatabaseCheckState.opened(
        uri: String,
        database: OpenedDatabase,
        what: String,
    ) = copy(
        uri = uri,
        isBusy = false,
        isOpen = true,
        databaseName = database.databaseName,
        fileName = database.fileName,
        log = log + "$what '${database.databaseName}'",
    )

    private fun DatabaseCheckState.done(what: String) = copy(isBusy = false, log = log + what)

    private fun DatabaseCheckState.failed(cause: Throwable) = copy(
        isBusy = false,
        error = cause.describe(),
        log = log + "failed",
    )

    // Counts only: names of groups and entries are the contents of the database, and this line is read
    // off a screen and copied into a report
    private fun MergeSummary.describe(): String =
        "merged=$mergeDone, different=$differentDatabases, meta=$metaDataChanged, " +
            "groups +$addedGroups ~$updatedGroups >$movedGroups -$deletedGroups, " +
            "entries +$addedEntries ~$updatedEntries >$movedEntries -$deletedEntries"

    private fun Throwable.describe(): String = when (this) {
        is CoreException -> "$kind: $message"
        is DatabaseFileException -> "file: $message"
        else -> toString()
    }

    private companion object {
        const val DATABASE_NAME = "Check"
    }
}
