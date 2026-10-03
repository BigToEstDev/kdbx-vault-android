package ru.kino.dev.welcome.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.kino.dev.welcome.CurrentDatabase
import ru.kino.dev.welcome.CurrentDatabaseRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The remembered database on a preferences data store, as one json string.
 *
 * Nothing secret is written here: uris, names of files and of the database. The password and the content
 * of the key file never come near this class.
 */
@Singleton
internal class CurrentDatabaseStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : CurrentDatabaseRepository, CurrentDatabaseWriter {

    override val current: Flow<CurrentDatabase?> = context.currentDatabase.data.map { preferences ->
        decode(preferences[ENTRY])
    }

    override suspend fun remember(database: CurrentDatabase) {
        context.currentDatabase.edit { preferences ->
            preferences[ENTRY] = json.encodeToString(CurrentDto.serializer(), database.toDto())
        }
    }

    // A record that cannot be read is treated as none: the next launch offers to pick the file again, which
    // is better than a launch that fails because of it
    private fun decode(stored: String?): CurrentDatabase? =
        stored
            ?.let { runCatching { json.decodeFromString(CurrentDto.serializer(), it) }.getOrNull() }
            ?.toDomain()

    @Serializable
    private data class CurrentDto(
        val uri: String,
        @SerialName("file_name") val fileName: String? = null,
        @SerialName("database_name") val databaseName: String? = null,
        @SerialName("key_file_uri") val keyFileUri: String? = null,
        @SerialName("key_file_name") val keyFileName: String? = null,
    )

    private fun CurrentDatabase.toDto() = CurrentDto(uri, fileName, databaseName, keyFileUri, keyFileName)

    private fun CurrentDto.toDomain() = CurrentDatabase(uri, fileName, databaseName, keyFileUri, keyFileName)

    private companion object {
        val ENTRY: Preferences.Key<String> = stringPreferencesKey("current")

        val json = Json { ignoreUnknownKeys = true }
    }
}

// The extension is the documented way to declare a store: one instance per file for the whole process
private val Context.currentDatabase: DataStore<Preferences> by preferencesDataStore(name = "current_database")
