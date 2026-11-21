package com.javargasp.tiominutermvi.domain.program.repository

/**
 * Clears every stored program and its progress. The Program feature's only operation for the
 * app's local wipe (account deletion); separate from [ProgramRepository] so the program screens
 * never see it.
 */
fun interface ProgramDataCleaner {
    suspend fun deleteAllProgramData()
}
