package com.camelcreatives.rekodi.common

sealed class RekodiResult<out T> {
    data class Success<T>(val data: T) : RekodiResult<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : RekodiResult<Nothing>()
}

inline fun <T, R> RekodiResult<T>.map(transform: (T) -> R): RekodiResult<R> = when (this) {
    is RekodiResult.Success -> RekodiResult.Success(transform(data))
    is RekodiResult.Error -> this
}

inline fun <T> RekodiResult<T>.onSuccess(action: (T) -> Unit): RekodiResult<T> {
    if (this is RekodiResult.Success) action(data)
    return this
}

inline fun <T> RekodiResult<T>.onError(action: (String, Throwable?) -> Unit): RekodiResult<T> {
    if (this is RekodiResult.Error) action(message, throwable)
    return this
}
