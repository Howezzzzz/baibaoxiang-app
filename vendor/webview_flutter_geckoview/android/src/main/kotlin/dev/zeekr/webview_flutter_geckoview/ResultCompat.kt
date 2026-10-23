// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package dev.zeekr.webview_flutter_geckoview

/**
 * ResultCompat.
 *
 * It is intended to solve the problem of being unable to obtain [kotlin.Result] in Java.
 *
 * [kotlin.Result] has a weird quirk when it is passed to Java where it seems to wrap itself.
 */
@Suppress("UNCHECKED_CAST")
class ResultCompat<T>(val result: Result<T>) {
    private val value: T? = result.getOrNull()
    private val exception = result.exceptionOrNull()
    val isSuccess = result.isSuccess
    val isFailure = result.isFailure

    companion object {
        @JvmStatic
        fun <T> success(value: T, callback: (Result<T>) -> Unit) {
            callback(Result.success(value))
        }

        @JvmStatic
        fun <T> failure(throwable: Throwable?, callback: (Result<T>) -> Unit) {
            val exception = throwable ?: NullPointerException("throwable is null")
            callback(Result.failure(exception))
        }

        @JvmStatic
        fun <T> asCompatCallback(result: (ResultCompat<T>) -> Unit): (Result<T>) -> Unit {
            return { result(ResultCompat(it)) }
        }
    }

    fun getOrNull(): T? {
        return value
    }

    fun exceptionOrNull(): Throwable? {
        return exception
    }
}
