package ru.kino.dev.storage

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.kino.dev.core.DatabaseFiles

/**
 * Binds the storage the features read and write their files through.
 *
 * The repositories themselves live in the features (`welcome`, `vault`); what stays here is the part they
 * share - the storage access framework.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface StorageModule {

    @Binds
    fun bindDatabaseFiles(impl: SafDatabaseFiles): DatabaseFiles
}
