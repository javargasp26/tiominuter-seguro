package com.javargasp.tiominutermvi.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.javargasp.tiominutermvi.domain.model.Exercise
import com.javargasp.tiominutermvi.domain.model.GymRoutine
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutNameResolver

/** Language code ("es") of the Spanish names stored next to the English ones. */
const val SPANISH_LANGUAGE = "es"

/**
 * The single rule for catalog names (exercises.json, routines_for_gym.jsonc): in Spanish
 * [nameEs] when it is present and not blank, otherwise [name]; in any other language [name].
 */
fun localizedName(name: String, nameEs: String?, language: String): String =
    if (language == SPANISH_LANGUAGE) nameEs?.takeIf { it.isNotBlank() } ?: name else name

/** Language of the current configuration (follows language changes). */
@Composable
fun currentLanguage(): String = LocalConfiguration.current.locales[0].language

/** [localizedName] in the app's current language. */
@Composable
fun localizedName(name: String, nameEs: String?): String = localizedName(name, nameEs, currentLanguage())

@Composable
fun Exercise.localizedName(): String = localizedName(name, nameEs)

/** The routine's name in the app's current language, through WorkoutNameResolver. */
@Composable
fun GymRoutine.workoutName(): String = WorkoutNameResolver.gymName(this).text()
