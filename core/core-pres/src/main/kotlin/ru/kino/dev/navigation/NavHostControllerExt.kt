package ru.kino.dev.navigation

import androidx.navigation.NavHostController

fun NavHostController.handle(event: MviNavEvent) {
    when (event) {
        MviNavEvent.NavigateBack -> {
            popBackStack()
        }

        is MviNavEvent.NavigateBackTo -> {
            popBackStack(route = event.popUpTo, inclusive = event.inclusive)
        }

        is MviNavEvent.NavigateTo -> {
            navigate(event.route) {
                launchSingleTop = event.isSingleTop
                event.popUpTo?.let { popUpTo ->
                    popUpTo(popUpTo) { inclusive = event.inclusive }
                }
            }
        }

        MviNavEvent.NavigateUp -> {
            navigateUp()
        }
    }
}
