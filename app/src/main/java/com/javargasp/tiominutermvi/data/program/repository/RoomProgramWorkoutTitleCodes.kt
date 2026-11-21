package com.javargasp.tiominutermvi.data.program.repository

import com.javargasp.tiominutermvi.data.program.local.dao.ProgramDao
import com.javargasp.tiominutermvi.data.program.mapper.toProgramWorkoutOrNull
import com.javargasp.tiominutermvi.domain.program.model.titleCode
import com.javargasp.tiominutermvi.domain.program.repository.programWorkoutOf
import com.javargasp.tiominutermvi.domain.traininglog.ProgramTitleCode
import com.javargasp.tiominutermvi.domain.traininglog.ProgramWorkoutTitleCodes

/**
 * The program feature's answer to the core's [ProgramWorkoutTitleCodes]: the stored workout of a
 * "program:<programId>:<week>:<dayIndex>" refId (one row), or null when it no longer exists.
 */
class RoomProgramWorkoutTitleCodes(private val programDao: ProgramDao) : ProgramWorkoutTitleCodes {
    override suspend fun titleCodeOf(refId: String): ProgramTitleCode? {
        val (programId, week, dayIndex) = programWorkoutOf(refId) ?: return null
        return programDao.getWorkout(programId, week, dayIndex)?.payload?.toProgramWorkoutOrNull()?.titleCode()
    }
}
