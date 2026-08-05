package com.supersoftcorporation.softlink

/**
 * Callback type for handling resolved deep links.
 * Matches Flutter's OnSoftLinkDeepLink typedef.
 */
typealias OnSoftLinkDeepLink = (SoftLinkDeepLink?) -> Unit

/**
 * Represents a resolved deep link from SoftLink.
 * Matches Flutter's SoftLinkDeepLink model.
 *
 * @property token Unique link token
 * @property screen Screen key to navigate to (e.g. "DOCTOR_PROFILE")
 * @property params Parameters associated with this link
 * @property linkType Type of link: "static" or "dynamic"
 */
data class SoftLinkDeepLink(
    val token: String,
    val screen: String,
    val params: Map<String, Any>,
    val linkType: String
) {
    companion object {
        /**
         * Creates a SoftLinkDeepLink from a JSON map.
         * Matches Flutter's SoftLinkDeepLink.fromJson()
         */
        @JvmStatic
        fun fromJson(json: org.json.JSONObject): SoftLinkDeepLink {
            return SoftLinkDeepLink(
                token = json.optString("token", ""),
                screen = json.optString("screen", ""),
                params = parseParams(json.optJSONObject("params")),
                linkType = json.optString("link_type", "static")
            )
        }

        private fun parseParams(json: org.json.JSONObject?): Map<String, Any> {
            json ?: return emptyMap()
            val map = mutableMapOf<String, Any>()
            json.keys().forEach { key -> map[key] = json.get(key) }
            return map
        }
    }

    /**
     * Get a parameter value as String
     */
    fun getParam(key: String): String? = params[key]?.toString()

    /**
     * Get a parameter value as String with default
     */
    fun getParam(key: String, default: String): String = params[key]?.toString() ?: default
}