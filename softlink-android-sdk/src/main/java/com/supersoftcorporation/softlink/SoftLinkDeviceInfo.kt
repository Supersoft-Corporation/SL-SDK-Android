package com.supersoftcorporation.softlink

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.android.gms.appset.AppSet
import kotlinx.coroutines.tasks.await

internal object SoftLinkDeviceInfo {

    /**
     * Get device fingerprint map for API calls
     * Matches Flutter's getDeviceFingerprint()
     */
    suspend fun getDeviceFingerprint(context: Context): Map<String, String> {
        return mapOf(
            "platform" to "android",
            "device_id" to getDeviceId(context),
            "model" to Build.MODEL,
            "brand" to Build.BRAND,
            "os_version" to Build.VERSION.RELEASE
        )
    }

    /**
     * Get a stable device ID with caching
     * Matches Flutter's getDeviceId() with cached check
     * Priority: Cached → Android ID → AppSet ID → Build.ID
     */
    suspend fun getDeviceId(context: Context): String {
        // Return cached device ID if available — matches Flutter
        val cached = SoftLinkStorage.getDeviceId(context)
        if (!cached.isNullOrEmpty()) return cached

        var deviceId = ""

        // Try Android ID first (matches Flutter's info.id)
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        if (!androidId.isNullOrEmpty() && androidId != "9774d56d682e549c") {
            deviceId = androidId
        }

        // Fallback to AppSet ID (Google's privacy-safe device ID)
        if (deviceId.isEmpty()) {
            deviceId = try {
                val appSetIdClient = AppSet.getClient(context)
                val appSetInfo = appSetIdClient.appSetIdInfo.await()
                appSetInfo.id
            } catch (e: Exception) {
                Build.ID
            }
        }

        // Cache it — matches Flutter's SoftLinkStorage.setDeviceId()
        if (deviceId.isNotEmpty()) {
            SoftLinkStorage.setDeviceId(context, deviceId)
        }

        return deviceId
    }
}