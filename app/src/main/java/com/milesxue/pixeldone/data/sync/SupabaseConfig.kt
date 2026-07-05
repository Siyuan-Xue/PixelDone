package com.milesxue.pixeldone.data.sync

import java.net.URL

internal data class SupabaseConfig(
    val baseUrl: String,
    val publishableKey: String,
    val allowInsecureHttp: Boolean,
) {
    val normalizedBaseUrl: String = baseUrl.trim().trimEnd('/')
    val isConfigured: Boolean = normalizedBaseUrl.isNotEmpty() && publishableKey.trim().isNotEmpty()

    fun configurationError(): String? {
        if (!isConfigured) return "Supabase URL and publishable key are not configured."
        val parsed = runCatching { URL(normalizedBaseUrl) }.getOrNull()
            ?: return "Supabase URL is invalid."
        if (parsed.protocol == "http" && !allowInsecureHttp) {
            return "Cleartext HTTP is disabled for this build."
        }
        return null
    }
}