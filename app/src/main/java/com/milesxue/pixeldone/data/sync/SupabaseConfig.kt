package com.milesxue.pixeldone.data.sync

import java.net.URI

private fun normalizeSupabaseConfigValue(value: String): String {
    var normalized = value
    do {
        val previous = normalized
        normalized = normalized
            .trim { character -> character.isWhitespace() || character == '\uFEFF' }
            .removePrefix("\u00EF\u00BB\u00BF")
            .removeSuffix("\u00EF\u00BB\u00BF")
    } while (normalized != previous)
    return normalized
}

internal data class SupabaseConfig(
    val baseUrl: String,
    val publishableKey: String,
    val allowInsecureHttp: Boolean,
) {
    val normalizedBaseUrl: String = normalizeSupabaseConfigValue(baseUrl).trimEnd('/')
    val normalizedPublishableKey: String = normalizeSupabaseConfigValue(publishableKey)
    val isConfigured: Boolean = normalizedBaseUrl.isNotEmpty() && normalizedPublishableKey.isNotEmpty()

    fun configurationError(): String? {
        if (!isConfigured) return "Supabase URL and publishable key are not configured."
        if (normalizedPublishableKey.any { character ->
                character.isWhitespace() || character == '\uFEFF' || character == '\"' || character == '\''
            }
        ) {
            return "Supabase publishable key is invalid."
        }
        if (normalizedBaseUrl.any { it == '\uFEFF' || it == '\"' || it == '\'' }) {
            return "Supabase URL is invalid."
        }
        val parsed = runCatching { URI(normalizedBaseUrl) }.getOrNull()
            ?: return "Supabase URL is invalid."
        val scheme = parsed.scheme?.lowercase()
        if (scheme !in setOf("http", "https") || parsed.host.isNullOrBlank()) {
            return "Supabase URL is invalid."
        }
        if (scheme == "http" && !allowInsecureHttp) {
            return "Cleartext HTTP is disabled for this build."
        }
        return null
    }
}
