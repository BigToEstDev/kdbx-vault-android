package ru.kino.dev.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer
import javax.inject.Inject

@HiltViewModel
class HomeProcessor @Inject constructor() :
    ViewModel(),
    OrbitContainerHost<HomeState, HomeState, Nothing> {

    override val container = orbitContainer<HomeState, Nothing>(HomeState())

    fun onClick() = intent {
        reduce {
            state.copy(clicksCount = state.clicksCount + 1)
        }
    }
}
