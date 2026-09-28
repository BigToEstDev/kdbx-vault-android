package ru.kino.dev.database

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.kino.dev.core.DatabaseFiles
import ru.kino.dev.core.DatabaseRepository
import ru.kino.dev.core.RecentDatabases
import ru.kino.dev.storage.RecentDatabasesStore
import ru.kino.dev.storage.SafDatabaseFiles

/** Binds the database repository and the storage it writes through. */
@Module
@InstallIn(SingletonComponent::class)
internal interface DatabaseModule {

    @Binds
    fun bindDatabaseRepository(impl: DatabaseRepositoryImpl): DatabaseRepository

    @Binds
    fun bindDatabaseFiles(impl: SafDatabaseFiles): DatabaseFiles

    @Binds
    fun bindRecentDatabases(impl: RecentDatabasesStore): RecentDatabases
}
