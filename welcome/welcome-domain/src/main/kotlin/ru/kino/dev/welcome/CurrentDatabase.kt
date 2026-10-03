package ru.kino.dev.welcome

/**
 * The one database the app remembers - there is no list of recent ones.
 *
 * Written only after the database opened, or was created, and access to its files was kept: a file picked
 * by mistake never replaces a database that works. Nothing secret is in here.
 *
 * @param uri the document, and the identity of the database in the core (its `dbKey`)
 * @param fileName name of the file to show; the uri itself is not readable
 * @param databaseName name from inside the database, as it was when last opened
 * @param keyFileUri the key file it was last opened with, read again on every opening
 * @param keyFileName name of that key file to show, so the screen does not ask the provider for it
 */
data class CurrentDatabase(
    val uri: String,
    val fileName: String?,
    val databaseName: String?,
    val keyFileUri: String?,
    val keyFileName: String?,
)
