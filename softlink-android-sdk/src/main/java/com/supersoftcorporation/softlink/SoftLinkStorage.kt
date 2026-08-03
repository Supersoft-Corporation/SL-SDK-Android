package com.supersoftcorporation.softlink

import android.content.Context
import android.content.SharedPreferences

internal object SoftLinkStorage {

    private const val PREFS_NAME = "softlink_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_LAST_URI = "last_handled_uri"
    private const val KEY_DEFERRED_RESOLVED = "deferred_resolved"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Device ID
    fun getDeviceId(context: Context): String? {
        return prefs(context).getString(KEY_DEVICE_ID, null)
    }

    fun setDeviceId(context: Context, deviceId: String) {
        prefs(context).edit().putString(KEY_DEVICE_ID, deviceId).apply()
    }

    // Last handled URI — prevents duplicate deep link handling
    fun getLastUri(context: Context): String? {
        return prefs(context).getString(KEY_LAST_URI, null)
    }

    fun setLastUri(context: Context, uri: String) {
        prefs(context).edit().putString(KEY_LAST_URI, uri).apply()
    }

    fun clearLastUri(context: Context) {
        prefs(context).edit().remove(KEY_LAST_URI).apply()
    }

    // Deferred deep link resolved flag
    fun isDeferredResolved(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_DEFERRED_RESOLVED, false)
    }

    fun setDeferredResolved(context: Context) {
        prefs(context).edit().putBoolean(KEY_DEFERRED_RESOLVED, true).apply()
    }
}