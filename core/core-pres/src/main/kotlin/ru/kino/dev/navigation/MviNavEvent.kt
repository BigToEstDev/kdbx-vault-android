package ru.kino.dev.navigation

sealed interface MviNavEvent {

    data class NavigateTo(
        val route: Any,
        val popUpTo: Any? = null,
        val inclusive: Boolean = false,
        val isSingleTop: Boolean = false,
    ) : MviNavEvent

    data object NavigateBack : MviNavEvent

    data class NavigateBackTo(
        val popUpTo: Any,
        val inclusive: Boolean = false,
    ) : MviNavEvent

    data object NavigateUp : MviNavEvent
}
