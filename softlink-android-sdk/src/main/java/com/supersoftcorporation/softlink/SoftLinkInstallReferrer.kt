package com.supersoftcorporation.softlink

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal object SoftLinkInstallReferrer {

    /**
     * Get the Play Store install referrer string
     * Used for campaign attribution when user installs from an ad
     */
    suspend fun getReferrer(context: Context): String? {
        return try {
            suspendCancellableCoroutine { continuation ->
                val referrerClient = InstallReferrerClient.newBuilder(context).build()

                referrerClient.startConnection(object : InstallReferrerStateListener {
                    override fun onInstallReferrerSetupFinished(responseCode: Int) {
                        when (responseCode) {
                            InstallReferrerClient.InstallReferrerResponse.OK -> {
                                try {
                                    val referrer = referrerClient
                                        .installReferrer
                                        .installReferrer
                                    referrerClient.endConnection()
                                    continuation.resume(referrer)
                                } catch (e: Exception) {
                                    referrerClient.endConnection()
                                    continuation.resume(null)
                                }
                            }
                            else -> {
                                referrerClient.endConnection()
                                continuation.resume(null)
                            }
                        }
                    }

                    override fun onInstallReferrerServiceDisconnected() {
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                })

                continuation.invokeOnCancellation {
                    try { referrerClient.endConnection() } catch (e: Exception) { }
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
