package ru.kino.dev.storage

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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import ru.kino.dev.core.RecentDatabase
import ru.kino.dev.core.RecentDatabases
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [RecentDatabases] on a preferences data store.
 *
 * The whole list is one json string. A proto schema would be the textbook answer, but the list is a handful
 * of entries with four fields, and a schema plus its generated code is more machinery than that deserves.
 *
 * Nothing secret is written here: a document uri, the file name and the name of the database. The password
 * never comes near this class.
 */
@Singleton
internal class RecentDatabasesStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : RecentDatabases {

    override val all: Flow<List<RecentDatabase>> = context.recentDatabases.data.map { preferences ->
        decode(preferences[ENTRIES]).sortedByDescending { it.openedAt }
    }

    override suspend fun remember(database: RecentDatabase) {
        context.recentDatabases.edit { preferences ->
            // One entry per uri: opening the same file again moves it to the front instead of doubling it
            val others = decode(preferences[ENTRIES]).filterNot { it.uri == database.uri }
            val kept = (listOf(database.toDto()) + others.map { it.toDto() }).take(MAX_ENTRIES)

            preferences[ENTRIES] = json.encodeToString(ListSerializer(RecentDto.serializer()), kept)
        }
    }

    override suspend fun forget(uri: String) {
        context.recentDatabases.edit { preferences ->
            val kept = decode(preferences[ENTRIES]).filterNot { it.uri == uri }.map { it.toDto() }

            preferences[ENTRIES] = json.encodeToString(ListSerializer(RecentDto.serializer()), kept)
        }
    }

    // A list that cannot be read is treated as empty: the entries are a convenience, and losing them is
    // better than a launch that fails because of them
    private fun decode(stored: String?): List<RecentDatabase> =
        stored
            ?.let { runCatching { json.decodeFromString(ListSerializer(RecentDto.serializer()), it) }.getOrNull() }
            ?.map { it.toDomain() }
            ?: emptyList()

    @Serializable
    private data class RecentDto(
        val uri: String,
        @SerialName("file_name") val fileName: String? = null,
        @SerialName("database_name") val databaseName: String? = null,
        @SerialName("opened_at") val openedAt: Long = 0,
    )

    private fun RecentDatabase.toDto() = RecentDto(uri, fileName, databaseName, openedAt)

    private fun RecentDto.toDomain() = RecentDatabase(uri, fileName, databaseName, openedAt)

    private companion object {
        val ENTRIES: Preferences.Key<String> = stringPreferencesKey("entries")

        // Enough to cover "the database I use" and a couple of others; the list is a shortcut, not history
        const val MAX_ENTRIES = 10

        val json = Json { ignoreUnknownKeys = true }
    }
}

// The extension is the documented way to declare a store: one instance per file for the whole process
private val Context.recentDatabases: DataStore<Preferences> by preferencesDataStore(name = "recent_databases")
