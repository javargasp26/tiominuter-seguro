package com.javargasp.tiominutermvi.data.program.local

import androidx.room.Embedded
import com.javargasp.tiominutermvi.data.program.local.entity.ProgramEntity

/** A program with its workout counts, for the list screen. */
data class ProgramSummaryRow(
    @Embedded val program: ProgramEntity,
    val totalWorkouts: Int,
    val completedWorkouts: Int
)
