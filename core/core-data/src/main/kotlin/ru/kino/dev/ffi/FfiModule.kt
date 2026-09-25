package ru.kino.dev.ffi

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.kino.dev.core.NativeCore

/**
 * Binds the native implementation of the core.
 *
 * Singleton scope is not a convenience here: the native library holds the open databases in process
 * global state keyed by `db_key`, so there is exactly one core per process no matter how many callers
 * there are.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface FfiModule {

    @Binds
    fun bindNativeCore(impl: NativeCoreFfi): NativeCore
}
