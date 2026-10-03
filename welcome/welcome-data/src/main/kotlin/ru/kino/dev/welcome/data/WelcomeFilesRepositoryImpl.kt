package ru.kino.dev.welcome.data

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import ru.kino.dev.core.DatabaseFiles
import ru.kino.dev.core.NativeCore
import ru.kino.dev.welcome.PickedFile
import ru.kino.dev.welcome.WelcomeFilesRepository
import javax.inject.Inject
import javax.inject.Singleton

/** [WelcomeFilesRepository] over the picked documents and the key file generator of the core. */
@Singleton
internal class WelcomeFilesRepositoryImpl @Inject constructor(
    private val core: NativeCore,
    private val files: DatabaseFiles,
) : WelcomeFilesRepository {

    override suspend fun displayName(uri: String): String? = welcomeCall { files.displayName(uri) }

    override suspend fun wouldOverwrite(uri: String, file: PickedFile): Boolean = welcomeCall {
        !accessing(file) { files.isEmpty(uri) }
    }

    override suspend fun writeNewKeyFile(uri: String) = welcomeCall {
        val content = core.generateKeyFile()
        try {
            // A key file cut short by cancelling is a key nobody can reproduce
            withContext(NonCancellable) {
                accessing(PickedFile.KeyFile) { files.writeReplacing(uri, content) }
            }
        } finally {
            // The content is a secret on par with the password; from here on it lives in the file only
            content.fill(0)
        }
    }
}
