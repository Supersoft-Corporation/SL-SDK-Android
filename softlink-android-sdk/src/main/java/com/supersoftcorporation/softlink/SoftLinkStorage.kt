package com.supersoftcorporation.softlink

import android.content.Context
import android.content.SharedPreferences

internal object SoftLinkStorage {

    private const val PREFS_NAME = "softlink_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_LAST_URI = "last_handled_uri" // stores token, not full URI — matches Flutter
    private const val KEY_DEFERRED_RESOLVED = "deferred_resolved"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Device ID — matches Flutter's getDeviceId() / setDeviceId()
    fun getDeviceId(context: Context): String? {
        return prefs(context).getString(KEY_DEVICE_ID, null)
    }

    fun setDeviceId(context: Context, deviceId: String) {
        prefs(context).edit().putString(KEY_DEVICE_ID, deviceId).apply()
    }

    // Last handled token — prevents duplicate deep link handling
    // Stores token only (uri.pathSegments.last) — matches Flutter's setLastUri(uri.pathSegments.last)
    fun getLastUri(context: Context): String? {
        return prefs(context).getString(KEY_LAST_URI, null)
    }

    fun setLastUri(context: Context, token: String) {
        prefs(context).edit().putString(KEY_LAST_URI, token).apply()
    }

    fun clearLastUri(context: Context) {
        prefs(context).edit().remove(KEY_LAST_URI).apply()
    }

    // Deferred deep link resolved flag — Android only (not in Flutter)
    // Flutter handles this differently via init() flow
    fun isDeferredResolved(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_DEFERRED_RESOLVED, false)
    }

    fun setDeferredResolved(context: Context) {
        prefs(context).edit().putBoolean(KEY_DEFERRED_RESOLVED, true).apply()
    }
}