package com.javargasp.tiominutermvi.data.auth

import android.util.Log
import com.javargasp.tiominutermvi.domain.auth.AuthFailureLog

/** [AuthFailureLog] on logcat (`Log.w`), with the class name as the tag like the rest of the app. */
class LogcatAuthFailureLog(private val tag: String) : AuthFailureLog {
    override fun warn(message: String) {
        Log.w(tag, message)
    }
}
