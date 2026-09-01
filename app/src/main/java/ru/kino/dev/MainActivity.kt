package ru.kino.dev

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import ru.kino.dev.navigation.AppNavHost
import ru.kino.dev.navigation.Navigator
import ru.kino.dev.ui.theme.PassappTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var navigator: Navigator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PassappTheme {
                AppNavHost(navigator = navigator)
            }
        }
    }
}
