package com.supersoftcorporation.softlink

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal class SoftLinkClient(
    val context: Context,
    private val baseUrl: String,
    private val apiKey: String
) {

    private val TAG = "SoftLinkClient"
    private val SDK_VERSION = "0.0.17"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    /**
     * Resolve a deep link by token (when app opens via deep link)
     * Matches Flutter's resolveToken()
     */
    suspend fun resolveByToken(token: String, utmSource: String = ""): SoftLinkDeepLink? {
        return try {
            val fingerprint = SoftLinkDeviceInfo.getDeviceFingerprint(context)
            val queryParams = mutableMapOf<String, String>()
            queryParams.putAll(fingerprint)
            if (utmSource.isNotEmpty()) queryParams["utm_source"] = utmSource
            val urlBuilder = StringBuilder("$baseUrl/api/links/token/$token?")
            Log.d(TAG, "resolveByToken URL: ${urlBuilder.toString()}")
            queryParams.entries.forEachIndexed { index, entry ->
                if (index > 0) urlBuilder.append("&")
                urlBuilder.append("${entry.key}=${entry.value}")
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.code != 200) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            if (json.optBoolean("found", false)) {
                // SoftLinkDeepLink(
                //     token = json.optString("token", token),
                //     screen = json.optString("screen", ""),
                //     params = parseParams(json.optJSONObject("params")),
                //     linkType = json.optString("link_type", "static")
                // )
                SoftLinkDeepLink.fromJson(json)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "resolveByToken error: ${e.message}")
            Log.e(TAG, "resolveByToken exception: ${e.javaClass.simpleName}")
            Log.e(TAG, "resolveByToken stack: ${e.stackTraceToString()}")
            null
        }
    }

    /**
     * Resolve deferred deep link (called on first install)
     * Matches Flutter's resolveDeferred()
     */
    suspend fun resolveDeferred(deviceId: String, referrer: String?): SoftLinkDeepLink? {
        return try {
            // Include full fingerprint — matches Flutter's approach
            val fingerprint = SoftLinkDeviceInfo.getDeviceFingerprint(context)
            val queryParams = mutableMapOf<String, String>()
            queryParams.putAll(fingerprint)
            if (!referrer.isNullOrEmpty()) queryParams["referrer"] = referrer
            if (deviceId.isNotEmpty()) queryParams["device_id"] = deviceId

            val urlBuilder = StringBuilder("$baseUrl/api/links/resolve?")
            queryParams.entries.forEachIndexed { index, entry ->
                if (index > 0) urlBuilder.append("&")
                urlBuilder.append("${entry.key}=${entry.value}")
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("User-Agent", "SoftLink-Android-SDK/$SDK_VERSION")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.code != 200) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            if (json.optBoolean("found", false)) {
                // SoftLinkDeepLink(
                //     token = json.optString("token", ""),
                //     screen = json.optString("screen", ""),
                //     params = parseParams(json.optJSONObject("params")),
                //     linkType = json.optString("link_type", "static")
                // )
                SoftLinkDeepLink.fromJson(json)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "resolveDeferred error: ${e.message}")
            null
        }
    }

    /**
     * Update fingerprint with device ID (for improved deferred deep link matching)
     * Matches Flutter's updateFingerprintDeviceId()
     */
    suspend fun updateFingerprintDeviceId(deviceId: String, referrer: String?) {
        try {
            val body = JSONObject().apply {
                put("device_id", deviceId)
                if (!referrer.isNullOrEmpty()) put("referrer", referrer)
            }

            val request = Request.Builder()
                .url("$baseUrl/api/links/fingerprint/update")
                .header("User-Agent", "SoftLink-Android-SDK/0.1.0")
                .post(body.toString().toRequestBody(JSON))
                .build()

            httpClient.newCall(request).execute()
        } catch (e: Exception) {
            // Silent fail — non-critical — matches Flutter's catch (_) {}
        }
    }

    /**
     * Generate a referral/runtime link
     * Matches Flutter's generateReferralLink()
     */
    suspend fun generateReferralLink(
        screenKey: String,
        values: Map<String, String>,
        token: String?,
        referrerId: String?
    ): String? {
        return try {
            val valuesJson = JSONObject()
            values.forEach { (k, v) -> valuesJson.put(k, v) }
            if (referrerId != null) valuesJson.put("ref", referrerId)

            val body = JSONObject().apply {
                put("screen", screenKey)
                put("values", valuesJson)
            }

            val urlSuffix = if (token != null) "?token=$token" else ""
            val request = Request.Builder()
                .url("$baseUrl/api/runtime/link$urlSuffix")
                .header("X-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON))
                .build()

            val response = httpClient.newCall(request).execute()
            // Accept both 200 and 201 — matches Flutter's statusCode check
            if (response.code != 200 && response.code != 201) return null
            val responseBody = response.body?.string() ?: return null
            val json = JSONObject(responseBody)
            json.optString("url").takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "generateReferralLink error: ${e.message}")
            null
        }
    }

    /**
 * Update fingerprint with device ID and MAID
 * Matches Flutter's updateFingerprintDeviceId()
 */
suspend fun updateFingerprintDeviceId(deviceId: String, referrer: String?, maid: String? = null) {
    try {
        val body = JSONObject().apply {
            put("device_id", deviceId)
            if (!referrer.isNullOrEmpty()) put("referrer", referrer)
            if (!maid.isNullOrEmpty()) put("maid", maid)
        }
        val request = Request.Builder()
            .url("$baseUrl/api/links/fingerprint/update")
            .header("User-Agent", "SoftLink-Android-SDK/0.1.0")
            .post(body.toString().toRequestBody(JSON))
            .build()
        httpClient.newCall(request).execute()
    } catch (e: Exception) { }
}

/**
 * Set user data for improved ad platform signal quality
 * Matches Flutter's setUserData()
 */
suspend fun setUserData(deviceId: String, email: String? = null, phone: String? = null, maid: String? = null): Boolean {
    return try {
        val hashedEmail = email?.lowercase()?.trim()?.let { sha256(it) }
        val hashedPhone = phone?.replace(Regex("[\\s\\-()]"), "")?.let { sha256(it) }

        val body = JSONObject().apply {
            put("device_id", deviceId)
            if (hashedEmail != null) put("hashed_email", hashedEmail)
            if (hashedPhone != null) put("hashed_phone", hashedPhone)
            if (!maid.isNullOrEmpty()) put("maid", maid)
        }
        val request = Request.Builder()
            .url("$baseUrl/api/links/user-data")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
        val response = httpClient.newCall(request).execute()
        response.code == 200 || response.code == 201
    } catch (e: Exception) {
        Log.e(TAG, "setUserData error: ${e.message}")
        false
    }
}

/**
 * Report app open for ad platform tracking
 * Matches Flutter's reportAppOpen()
 */
suspend fun reportAppOpen(
    deviceId: String,
    platform: String,
    osVersion: String? = null,
    deviceModel: String? = null,
    screenWidth: Int? = null,
    screenHeight: Int? = null,
    locale: String? = null
): Boolean {
    return try {
        val body = JSONObject().apply {
            put("device_id", deviceId)
            put("platform", platform)
            if (osVersion != null) put("os_version", osVersion)
            if (deviceModel != null) put("device_model", deviceModel)
            if (screenWidth != null) put("screen_width", screenWidth)
            if (screenHeight != null) put("screen_height", screenHeight)
            if (locale != null) put("locale", locale)
        }
        val request = Request.Builder()
            .url("$baseUrl/api/links/app-open")
            .header("X-API-Key", apiKey)
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
        val response = httpClient.newCall(request).execute()
        response.code == 200 || response.code == 201
    } catch (e: Exception) {
        Log.e(TAG, "reportAppOpen error: ${e.message}")
        false
    }
}

/**
 * Trigger a custom event defined in SoftLink portal
 * Matches Flutter's triggerEvent()
 */
suspend fun triggerEvent(
    eventKey: String,
    linkToken: String? = null,
    sequence: Int? = null,
    lastEventKey: String? = null,
    metadata: Map<String, Any>? = null
): Boolean {
    return try {
        val body = JSONObject().apply {
            put("event_key", eventKey)
            if (!linkToken.isNullOrEmpty()) put("link_token", linkToken)
            if (sequence != null) put("sequence", sequence)
            if (!lastEventKey.isNullOrEmpty()) put("last_event_key", lastEventKey)
            if (metadata != null) {
                val metaJson = JSONObject()
                metadata.forEach { (k, v) -> metaJson.put(k, v) }
                put("metadata", metaJson)
                put("platform", "android")
            }
        }
        val request = Request.Builder()
            .url("$baseUrl/api/events/trigger")
            .header("X-API-Key", apiKey)
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
        val response = httpClient.newCall(request).execute()
        response.code == 200 || response.code == 201
    } catch (e: Exception) {
        Log.e(TAG, "triggerEvent error: ${e.message}")
        false
    }
}

private fun sha256(input: String): String {
    val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
}