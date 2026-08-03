package com.supersoftcorporation.softlink

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * SoftLink Android SDK
 *
 * Main entry point for the SoftLink deep link management SDK.
 *
 * Usage:
 * ```kotlin
 * SoftLink.init(
 *     context = this,
 *     baseUrl = "https://api.supersoftlink.com",
 *     apiKey = "sl_your_api_key",
 *     onDeepLink = { deepLink ->
 *         // handle deep link navigation
 *     }
 * )
 * ```
 */
object SoftLink {

    private var client: SoftLinkClient? = null
    private var onDeepLink: ((SoftLinkDeepLink) -> Unit)? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Initialize the SoftLink SDK.
     * Call this in your Application.onCreate() or MainActivity.onCreate()
     *
     * @param context Application or Activity context
     * @param baseUrl Your SoftLink backend URL (default: https://api.supersoftlink.com)
     * @param apiKey Your app's SoftLink API key (starts with sl_)
     * @param onDeepLink Callback fired when a deep link is resolved
     */
    @JvmStatic
    @JvmOverloads
    fun init(
        context: Context,
        baseUrl: String = "https://api.supersoftlink.com",
        apiKey: String,
        onDeepLink: ((SoftLinkDeepLink) -> Unit)? = null
    ) {
        this.onDeepLink = onDeepLink
        this.client = SoftLinkClient(
            context = context.applicationContext,
            baseUrl = baseUrl.trimEnd('/'),
            apiKey = apiKey
        )

        // Check deferred deep link on first install
        checkDeferred(context)
    }

    /**
     * Call this in your Activity.onNewIntent() to handle incoming deep links
     * when the app is already running.
     *
     * @param intent The intent from onNewIntent()
     */
    @JvmStatic
    fun handleIntent(intent: Intent?) {
        intent ?: return
        val uri = intent.data ?: return
        resolveFromUri(uri)
    }

    /**
     * Call this in your Activity.onCreate() to handle the initial deep link
     * that launched the app.
     *
     * @param intent The intent from onCreate()
     */
    @JvmStatic
    fun handleInitialIntent(intent: Intent?) {
        intent ?: return
        val uri = intent.data ?: return
        resolveFromUri(uri)
    }

    /**
     * Generate a shareable referral/runtime link at runtime.
     *
     * @param screenKey The screen key (e.g. "DOCTOR_PROFILE")
     * @param values Map of parameter values (e.g. mapOf("doctorId" to "123"))
     * @param token Optional parent dynamic link token
     * @param referrerId Optional referrer user ID for referral tracking
     * @param callback Callback with generated URL or null on failure
     */
    @JvmStatic
    @JvmOverloads
    fun generateReferralLink(
        screenKey: String,
        values: Map<String, String>,
        token: String? = null,
        referrerId: String? = null,
        callback: SoftLinkCallback<String?>
    ) {
        val c = client ?: run {
            callback.onResult(null)
            return
        }
        scope.launch {
            val url = c.generateReferralLink(screenKey, values, token, referrerId)
            callback.onResult(url)
        }
    }

    // Internal — resolve deep link from URI
    internal fun resolveFromUri(uri: Uri) {
    val token = extractToken(uri) ?: return
    val c = client ?: return
    val ctx = c.context
    
    // Prevent duplicate handling of same URI
    val uriString = uri.toString()
    if (SoftLinkStorage.getLastUri(ctx) == uriString) return
    SoftLinkStorage.setLastUri(ctx, uriString)
    
    scope.launch {
        val deepLink = c.resolveByToken(token)
        deepLink?.let { onDeepLink?.invoke(it) }
        // Clear after handling
        SoftLinkStorage.clearLastUri(ctx)
    }
}
    // internal fun resolveFromUri(uri: Uri) {
    //     val token = extractToken(uri) ?: return
    //     val c = client ?: return
    //     scope.launch {
    //         val deepLink = c.resolveByToken(token)
    //         deepLink?.let { onDeepLink?.invoke(it) }
    //     }
    // }

    // Internal — check for deferred deep link on install
    private fun checkDeferred(context: Context) {
        val c = client ?: return
        scope.launch(Dispatchers.IO) {
            // Update fingerprint with device ID first
            val deviceId = SoftLinkDeviceInfo.getDeviceId(context)
            val referrer = SoftLinkInstallReferrer.getReferrer(context)
            if (deviceId.isNotEmpty()) {
                c.updateFingerprintDeviceId(deviceId, referrer)
            }
            // Then resolve deferred deep link
            val deepLink = c.resolveDeferred(deviceId = deviceId, referrer = referrer)
            deepLink?.let {
                scope.launch(Dispatchers.Main) {
                    onDeepLink?.invoke(it)
                }
            }
        }
    }

    // Internal — extract token from URI
    private fun extractToken(uri: Uri): String? {
        // Handles: https://domain.com/l/TOKEN or scheme://l/TOKEN
        val segments = uri.pathSegments
        val lIndex = segments.indexOf("l")
        return if (lIndex != -1 && lIndex + 1 < segments.size) {
            segments[lIndex + 1]
        } else {
            null
        }
    }
}
