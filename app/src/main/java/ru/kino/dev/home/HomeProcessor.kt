package ru.kino.dev.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer
import ru.kino.dev.core.CoreException
import ru.kino.dev.core.NativeCore
import ru.kino.dev.core.PasswordOptions
import ru.kino.dev.databasecheck.DatabaseCheckRoute
import ru.kino.dev.navigation.MviNavEvent
import ru.kino.dev.navigation.Navigator
import javax.inject.Inject

@HiltViewModel
class HomeProcessor @Inject constructor(
    private val nativeCore: NativeCore,
    private val navigator: Navigator,
) :
    ViewModel(),
    OrbitContainerHost<HomeState, HomeState, Nothing> {

    override val container = orbitContainer<HomeState, Nothing>(HomeState()) {
        // Step 22: the end to end check of the Kotlin -> Rust -> Kotlin chain. buildInfo also tells a
        // stale .so apart from a fresh one, which is worth having in the log from the very start
        loadBuildInfo()
    }

    fun onClick() = intent {
        reduce {
            state.copy(clicksCount = state.clicksCount + 1)
        }
    }

    /** Generates a password in the core - the mock operation proving the bridge works. */
    fun onGeneratePassword() = nativeCall { generatePassword() }

    /**
     * Asks for a password of length zero, which the core rejects. Proves that a failure arrives as data
     * with its own kind rather than as a crash.
     */
    fun onProvokeCoreError() = nativeCall { generatePassword(PasswordOptions(length = 0)) }

    /**
     * Calls a command the bridge does not have. The other half of the failure path: a refusal by the
     * bridge rather than by the core, and it has to reach the screen the same way.
     */
    fun onProvokeBridgeError() = nativeCall { probeUnknownCommand() }

    /** Opens the screen that drives a real database through the bridge. */
    fun onOpenDatabaseCheck() = intent {
        navigator.navigate(MviNavEvent.NavigateTo(DatabaseCheckRoute))
    }

    private fun loadBuildInfo() = intent {
        val info = runCatching { nativeCore.buildInfo() }
        reduce {
            info.fold(
                onSuccess = { state.copy(nativeBuildInfo = it) },
                onFailure = { state.copy(nativeError = it.describe()) },
            )
        }
    }

    private fun nativeCall(call: suspend NativeCore.() -> String) = intent {
        reduce { state.copy(isNativeBusy = true, nativeError = null) }

        val result = runCatching { nativeCore.call() }

        reduce {
            result.fold(
                onSuccess = { state.copy(isNativeBusy = false, generatedPassword = it) },
                onFailure = {
                    state.copy(isNativeBusy = false, generatedPassword = null, nativeError = it.describe())
                },
            )
        }
    }

    private fun Throwable.describe(): String = when (this) {
        is CoreException -> "$kind: $message"
        else -> this.toString()
    }
}
