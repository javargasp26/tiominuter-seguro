package com.javargasp.tiominutermvi.domain.auth

/**
 * Where auth failures are logged (`Log.w` in the app, a list in tests). Every failure except a
 * cancellation is logged once, with [describeAuthFailure]: never ID tokens, emails or names.
 */
fun interface AuthFailureLog {
    fun warn(message: String)
}

private val EMAIL = Regex("""[\w.+-]+@[\w-]+(\.[\w-]+)+""")
private val JWT = Regex("""eyJ[\w-]*\.[\w-]+\.[\w-]*""")

/**
 * "GetCredentialCustomException type=… message=…": the exception's class, the Credential Manager
 * [type] when there is one, and its message with anything that looks like an email or a token removed.
 */
fun describeAuthFailure(error: Throwable, type: String? = null): String {
    val message = error.message
        ?.replace(EMAIL, "<email>")
        ?.replace(JWT, "<token>")
    return buildString {
        append(error.javaClass.simpleName)
        if (type != null) append(" type=").append(type)
        append(" message=").append(message)
    }
}
