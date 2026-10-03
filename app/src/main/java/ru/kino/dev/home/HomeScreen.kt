package ru.kino.dev.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The start destination while the app has no real screens: empty on purpose.
 *
 * The debug buttons that lived here went with Step 28 in pass-docs - the data layer is checked by tests
 * now. Step 27 replaces this screen with the first screen of the welcome module.
 */
@Composable
fun HomeScreen() {
    Box(modifier = Modifier.fillMaxSize())
}
