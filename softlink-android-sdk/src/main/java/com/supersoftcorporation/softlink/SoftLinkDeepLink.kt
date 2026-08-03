package com.supersoftcorporation.softlink

/**
 * Represents a resolved SoftLink deep link.
 *
 * @property token Unique link token
 * @property screen Screen key in UPPERCASE_WITH_UNDERSCORES format (e.g. "DOCTOR_PROFILE")
 * @property params Map of parameters associated with the link
 * @property linkType Type of link: "static" or "dynamic"
 */
data class SoftLinkDeepLink(
    val token: String,
    val screen: String,
    val params: Map<String, Any>,
    val linkType: String
) {
    /**
     * Get a parameter value as String
     */
    fun getParam(key: String): String? = params[key]?.toString()

    /**
     * Get a parameter value as String with default
     */
    fun getParam(key: String, default: String): String = params[key]?.toString() ?: default
}
