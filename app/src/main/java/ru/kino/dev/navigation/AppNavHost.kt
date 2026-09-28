package ru.kino.dev.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.kino.dev.databasecheck.DatabaseCheckRoute
import ru.kino.dev.databasecheck.DatabaseCheckScreen
import ru.kino.dev.home.HomeRoute
import ru.kino.dev.home.HomeScreen

@Composable
fun AppNavHost(navigator: Navigator) {
    val navController = rememberNavController()

    LaunchedEffect(navigator) {
        navigator.events.collect { event ->
            navController.handle(event)
        }
    }

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen()
        }

        // Step 23: a real database on a real file, checked from the app itself
        composable<DatabaseCheckRoute> {
            DatabaseCheckScreen()
        }
    }
}
