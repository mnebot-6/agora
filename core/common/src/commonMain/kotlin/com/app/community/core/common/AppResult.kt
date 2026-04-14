package com.app.community.core.common

sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : AppResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw cause ?: IllegalStateException(message)
    }

    inline fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }

    inline fun onSuccess(action: (T) -> Unit): AppResult<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onError(action: (String, Throwable?) -> Unit): AppResult<T> {
        if (this is Error) action(message, cause)
        return this
    }
}

inline fun <T> safeCall(block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: Exception) {
    AppResult.Error(extractUserMessage(e.message), e)
}

/**
 * Extracts a clean, user-facing message from Supabase/HTTP exception messages
 * that may contain URLs, headers, and JWT tokens.
 */
fun extractUserMessage(raw: String?): String {
    if (raw.isNullOrBlank()) return "Error desconocido"
    // Supabase exceptions format: "message\nURL: ...\nHeaders: ..."
    // Extract only the first line (the actual error message)
    val firstLine = raw.lineSequence().first().trim()
    return firstLine.ifBlank { "Error desconocido" }
}
