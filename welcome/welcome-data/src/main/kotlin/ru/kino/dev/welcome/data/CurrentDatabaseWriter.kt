package ru.kino.dev.welcome.data

import ru.kino.dev.welcome.CurrentDatabase

/**
 * Writing the remembered database - kept inside the data layer on purpose.
 *
 * Only [WelcomeDatabaseRepositoryImpl] writes it, as the last step of a database that opened, so the domain
 * interface offers reading alone and no screen can remember a database that did not open.
 */
internal interface CurrentDatabaseWriter {

    /** Replaces the remembered database. */
    suspend fun remember(database: CurrentDatabase)
}
