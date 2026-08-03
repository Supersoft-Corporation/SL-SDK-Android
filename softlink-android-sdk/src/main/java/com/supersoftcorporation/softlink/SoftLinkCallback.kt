package com.supersoftcorporation.softlink

/**
 * Callback interface for Java compatibility.
 * Kotlin users can use lambdas directly.
 */
fun interface SoftLinkCallback<T> {
    fun onResult(result: T)
}
