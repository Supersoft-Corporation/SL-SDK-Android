package com.supersoftcorporation.softlink

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.android.gms.appset.AppSet
import kotlinx.coroutines.tasks.await

internal object SoftLinkDeviceInfo {

    /**
     * Get device fingerprint map for API calls
     */
    suspend fun getDeviceFingerprint(context: Context): Map<String, String> {
        return mapOf(
            "platform" to "android",
            "device_id" to getDeviceId(context),
            "model" to Build.MODEL,
            "manufacturer" to Build.MANUFACTURER,
            "os_version" to Build.VERSION.RELEASE
        )
    }

    /**
     * Get a stable device ID
     * Priority: Android ID → AppSet ID (for Android 12+)
     */
    suspend fun getDeviceId(context: Context): String {
        // Try Android ID first (stable across app reinstalls on most devices)
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        if (!androidId.isNullOrEmpty() && androidId != "9774d56d682e549c") {
            return androidId
        }

        // Fallback to AppSet ID (Google's privacy-safe device ID)
        return try {
            val appSetIdClient = AppSet.getClient(context)
            val appSetInfo = appSetIdClient.appSetIdInfo.await()
            appSetInfo.id
        } catch (e: Exception) {
            Build.ID
        }
    }
}
