package com.jsi.metronome.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface Screen {
    @Serializable
    data object Metronome : Screen

    @Serializable
    data object PitchPipe : Screen

    @Serializable
    data object Tuner : Screen

    @Serializable
    data object Settings : Screen
}
