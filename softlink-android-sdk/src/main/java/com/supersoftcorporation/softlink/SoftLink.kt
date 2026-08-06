package com.supersoftcorporation.softlink

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
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

    private const val TAG = "SoftLink"

    private var client: SoftLinkClient? = null
    private var onDeepLink: ((SoftLinkDeepLink) -> Unit)? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Deduplication state — matches Flutter's _lastHandledToken + _lastHandledTime
    private var lastHandledToken: String? = null
    private var lastHandledTime: Long = 0

    // Processing guard — matches Flutter's _processingToken
    private var processingToken: String? = null

    /**
     * Initialize the SoftLink SDK.
     * Call this in your Activity.onCreate() BEFORE handleInitialIntent()
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
        Log.d(TAG, "SoftLink SDK initialized")
        // Note: deferred check is triggered from handleInitialIntent
        // if no URI is present — matches Flutter's init() flow
    }

    /**
     * Call this in your Activity.onCreate() AFTER SoftLink.init()
     * to handle the initial deep link that launched the app.
     * If no deep link URI is present, triggers deferred deep link check.
     *
     * @param intent The intent from onCreate()
     */
    @JvmStatic
    fun handleInitialIntent(intent: Intent?) {
        val uri = intent?.data
        if (uri != null) {
            // Has a URI — resolve it directly, skip deferred check
            Log.d(TAG, "handleInitialIntent: URI found: $uri")
            resolveFromUri(uri)
        } else {
            // No URI — check for deferred deep link (matches Flutter's init flow)
            Log.d(TAG, "handleInitialIntent: No URI, checking deferred...")
            client?.let { checkDeferred(it.context) }
        }
    }

    /**
     * Call this in your Activity.onNewIntent() to handle incoming deep links
     * when the app is already running.
     *
     * @param intent The intent from onNewIntent()
     */
    @JvmStatic
    fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        Log.d(TAG, "handleIntent: URI: $uri")
        resolveFromUri(uri)
    }

    /**
     * Resolve a deep link by token directly.
     * Useful for testing or manual resolution.
     *
     * @param token The link token
     * @param utmSource Optional UTM source parameter
     * @param callback Callback with resolved SoftLinkDeepLink or null
     */
    @JvmStatic
    @JvmOverloads
    fun resolveToken(
        token: String,
        utmSource: String = "",
        callback: SoftLinkCallback<SoftLinkDeepLink?>
    ) {
        val c = client ?: run { callback.onResult(null); return }
        scope.launch(Dispatchers.IO) {
            val deepLink = c.resolveByToken(token, utmSource = utmSource)
            scope.launch(Dispatchers.Main) {
                callback.onResult(deepLink)
            }
        }
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
        scope.launch(Dispatchers.IO) {
            val url = c.generateReferralLink(screenKey, values, token, referrerId)
            scope.launch(Dispatchers.Main) {
                callback.onResult(url)
            }
        }
    }

    // Internal — resolve deep link from URI
    // Matches Flutter's _handleUri() with full deduplication logic
    internal fun resolveFromUri(uri: Uri) {
        val token = extractToken(uri) ?: return
        val c = client ?: return
        val ctx = c.context

        Log.d(TAG, "resolveFromUri: token=$token")

        // Deduplicate — ignore same token within 2 seconds
        // Matches Flutter's _lastHandledToken + _lastHandledTime check
        val now = System.currentTimeMillis()
        if (lastHandledToken == token && now - lastHandledTime < 2000) {
            Log.d(TAG, "resolveFromUri: duplicate URI ignored: $token")
            return
        }
        lastHandledToken = token
        lastHandledTime = now

        // Prevent duplicate handling of same token via storage
        // Matches Flutter's SoftLinkStorage.getLastUri() check
        if (SoftLinkStorage.getLastUri(ctx) == token) {
            Log.d(TAG, "resolveFromUri: already handled token: $token")
            return
        }
        SoftLinkStorage.setLastUri(ctx, token)

        // Processing guard — matches Flutter's _processingToken check
        if (processingToken == token) {
            Log.d(TAG, "resolveFromUri: already processing token: $token")
            return
        }
        processingToken = token

        // Extract utm_source from URI — matches Flutter's utmSource extraction
        val utmSource = uri.getQueryParameter("utm_source") ?: ""

        scope.launch(Dispatchers.IO) {
            try {
                val deepLink = c.resolveByToken(token, utmSource = utmSource)
                scope.launch(Dispatchers.Main) {
                    deepLink?.let {
                        Log.d(TAG, "resolveFromUri: resolved screen=${it.screen}")
                        onDeepLink?.invoke(it)
                    } ?: Log.d(TAG, "resolveFromUri: no deep link found for token=$token")
                }
            } finally {
                processingToken = null
                SoftLinkStorage.clearLastUri(ctx)
            }
        }
    }

    // Internal — check for deferred deep link on install
    // Matches Flutter's _checkDeferred()
    private fun checkDeferred(context: Context) {
        // Only check once — matches Flutter's one-time deferred resolution
//        if (SoftLinkStorage.isDeferredResolved(context)) {
//            Log.d(TAG, "checkDeferred: already resolved, skipping")
//            return
//        }

        val c = client ?: return
        scope.launch(Dispatchers.IO) {
            Log.d(TAG, "checkDeferred: starting...")
            val deviceId = SoftLinkDeviceInfo.getDeviceId(context)
            val referrer = SoftLinkInstallReferrer.getReferrer(context)

            Log.d(TAG, "checkDeferred: deviceId=$deviceId referrer=$referrer")

            if (deviceId.isNotEmpty()) {
                c.updateFingerprintDeviceId(deviceId, referrer)
            }

            val deepLink = c.resolveDeferred(deviceId = deviceId, referrer = referrer)
            deepLink?.let {
                Log.d(TAG, "checkDeferred: resolved screen=${it.screen}")
//                SoftLinkStorage.setDeferredResolved(context)
                scope.launch(Dispatchers.Main) {
                    onDeepLink?.invoke(it)
                }
            } ?: Log.d(TAG, "checkDeferred: no deferred deep link found")
        }
    }

    // Internal — extract token from URI
    // Handles: https://domain.com/l/TOKEN or scheme://l/TOKEN
    private fun extractToken(uri: Uri): String? {
        val segments = uri.pathSegments
        val lIndex = segments.indexOf("l")
        return if (lIndex != -1 && lIndex + 1 < segments.size) {
            segments[lIndex + 1]
        } else {
            // Fallback: last segment if no 'l' found
            uri.pathSegments.lastOrNull()?.takeIf { it.isNotEmpty() }
        }
    }
}