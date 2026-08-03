package com.supersoftcorporation.softlink

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal class SoftLinkClient(
    private val context: Context,
    private val baseUrl: String,
    private val apiKey: String
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    /**
     * Resolve a deep link by token (when app opens via deep link)
     */
    suspend fun resolveByToken(token: String): SoftLinkDeepLink? {
        return try {
            val deviceInfo = SoftLinkDeviceInfo.getDeviceFingerprint(context)
            val url = "$baseUrl/api/links/token/$token?" +
                "platform=${deviceInfo["platform"]}" +
                "&device_id=${deviceInfo["device_id"]}"

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            if (json.optBoolean("found", false)) {
                SoftLinkDeepLink(
                    token = json.optString("token", token),
                    screen = json.optString("screen", ""),
                    params = parseParams(json.optJSONObject("params")),
                    linkType = json.optString("link_type", "static")
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolve deferred deep link (called on first install)
     */
    suspend fun resolveDeferred(deviceId: String, referrer: String?): SoftLinkDeepLink? {
        return try {
            val deviceInfo = SoftLinkDeviceInfo.getDeviceFingerprint(context)
            val urlBuilder = StringBuilder("$baseUrl/api/links/resolve?")
            urlBuilder.append("platform=${deviceInfo["platform"]}")
            urlBuilder.append("&device_id=$deviceId")
            if (!referrer.isNullOrEmpty()) {
                urlBuilder.append("&referrer=${referrer}")
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("User-Agent", "SoftLink-Android-SDK/0.1.0")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            if (json.optBoolean("found", false)) {
                SoftLinkDeepLink(
                    token = json.optString("token", ""),
                    screen = json.optString("screen", ""),
                    params = parseParams(json.optJSONObject("params")),
                    linkType = json.optString("link_type", "static")
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Update fingerprint with device ID (for improved deferred deep link matching)
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
            // Silent fail — non-critical
        }
    }

    /**
     * Generate a referral/runtime link
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
            val responseBody = response.body?.string() ?: return null
            val json = JSONObject(responseBody)
            json.optString("url").takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseParams(json: JSONObject?): Map<String, Any> {
        json ?: return emptyMap()
        val map = mutableMapOf<String, Any>()
        json.keys().forEach { key ->
            map[key] = json.get(key)
        }
        return map
    }
}
