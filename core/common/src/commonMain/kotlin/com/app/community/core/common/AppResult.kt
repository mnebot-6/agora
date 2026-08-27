package com.app.community.core.common

import kotlinx.coroutines.CancellationException

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

/**
 * Se captura Throwable y NO Exception. En wasmJs un fallo de fetch del navegador (CORS,
 * DNS, sin red) llega como JsException, que hereda de Throwable pero NO de Exception:
 * con `catch (e: Exception)` se escapaba, mataba la corrutina, y la pantalla se quedaba
 * con su spinner girando para siempre sin mensaje ni salida. En Android el mismo fallo
 * es una IOException y si se capturaba, asi que el bug solo se veia en web.
 *
 * CancellationException se relanza: si se tragara, cerrar una pantalla a media carga
 * dejaria un error en la UI en vez de cancelar en silencio.
 */
inline fun <T> safeCall(block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
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
