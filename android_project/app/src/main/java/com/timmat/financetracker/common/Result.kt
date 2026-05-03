package com.timmat.financetracker.common

/**
 * Generic result wrapper for repository calls. Prefer this over throwing because
 * Firestore errors are common and usually user-recoverable (no network, denied by rules).
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val throwable: Throwable) : Result<Nothing>() {
        val message: String get() = throwable.message ?: "Unknown error"
    }
    data object Loading : Result<Nothing>()
}

inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> = when (this) {
    is Result.Success -> Result.Success(transform(data))
    is Result.Error -> this
    is Result.Loading -> this
}
