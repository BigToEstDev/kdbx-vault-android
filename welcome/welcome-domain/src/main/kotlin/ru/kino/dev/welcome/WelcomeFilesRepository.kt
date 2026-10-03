package ru.kino.dev.welcome

/**
 * The files the user picks in `welcome`, before any of them is a database.
 *
 * Every failure is a [WelcomeFailure].
 */
interface WelcomeFilesRepository {

    /**
     * The name to show for a picked file, or null when the provider does not tell - the database picked on
     * the welcome screen, a key file picked on the unlock screen, before either is opened.
     */
    suspend fun displayName(uri: String): String?

    /**
     * Whether writing into [uri] would destroy something - "save as" for the database or for the key file
     * handed back an existing file rather than a new one.
     *
     * The screen asks this before writing and warns: an existing key file may be the key of another
     * database, and writing over it locks that one for good.
     */
    suspend fun wouldOverwrite(uri: String): Boolean

    /**
     * Generates a new key file and writes it into [uri], the file "save as" handed back - the key file
     * backup screen, before the database is created.
     *
     * The content never leaves this call: it goes from the core straight into the file, and creating the
     * database reads it back from there.
     */
    suspend fun writeNewKeyFile(uri: String)
}
