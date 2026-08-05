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
                .header("User-Agent", "SoftLink-Android-SDK/0.1.0")
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

    // private fun parseParams(json: JSONObject?): Map<String, Any> {
    //     json ?: return emptyMap()
    //     val map = mutableMapOf<String, Any>()
    //     json.keys().forEach { key ->
    //         map[key] = json.get(key)
    //     }
    //     return map
    // }
}