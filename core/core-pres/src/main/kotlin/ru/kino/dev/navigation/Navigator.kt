package ru.kino.dev.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Navigator @Inject constructor() {

    private val _events = MutableSharedFlow<MviNavEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<MviNavEvent> = _events

    suspend fun navigate(event: MviNavEvent) {
        _events.emit(event)
    }
}
