package com.milesxue.pixeldone

import com.milesxue.pixeldone.data.sync.SupabaseConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseConfigTest {
    @Test
    fun emptyConfigIsNotConfigured() {
        val config = SupabaseConfig(baseUrl = "", publishableKey = "", allowInsecureHttp = true)

        assertFalse(config.isConfigured)
        assertEquals("Supabase URL and publishable key are not configured.", config.configurationError())
    }

    @Test
    fun debugHttpConfigIsAllowedWhenExplicitlyEnabled() {
        val config = SupabaseConfig(
            baseUrl = "http://10.0.0.8:8000/",
            publishableKey = "publishable",
            allowInsecureHttp = true,
        )

        assertTrue(config.isConfigured)
        assertEquals("http://10.0.0.8:8000", config.normalizedBaseUrl)
        assertNull(config.configurationError())
    }

    @Test
    fun formalHttpConfigIsAllowedByBuildPolicy() {
        val config = SupabaseConfig(
            baseUrl = "http://10.0.0.8:8000",
            publishableKey = "publishable",
            allowInsecureHttp = true,
        )

        assertTrue(config.isConfigured)
        assertNull(config.configurationError())
    }

    @Test
    fun boundaryBomAndWhitespaceAreRemovedFromUrlAndKey() {
        val config = SupabaseConfig(
            baseUrl = "\uFEFF  http://10.0.0.8:8000/ \uFEFF",
            publishableKey = "\uFEFF publishable-key \uFEFF",
            allowInsecureHttp = true,
        )

        assertEquals("http://10.0.0.8:8000", config.normalizedBaseUrl)
        assertEquals("publishable-key", config.normalizedPublishableKey)
        assertNull(config.configurationError())
    }

    @Test
    fun boundaryUtf8BomMojibakeIsAlsoRemoved() {
        val config = SupabaseConfig(
            baseUrl = "\u00EF\u00BB\u00BFhttp://10.0.0.8:8000",
            publishableKey = "\u00EF\u00BB\u00BFpublishable-key",
            allowInsecureHttp = true,
        )

        assertEquals("http://10.0.0.8:8000", config.normalizedBaseUrl)
        assertEquals("publishable-key", config.normalizedPublishableKey)
        assertNull(config.configurationError())
    }

    @Test
    fun httpConfigIsRejectedWhenCleartextIsDisabled() {
        val config = SupabaseConfig(
            baseUrl = "http://10.0.0.8:8000",
            publishableKey = "publishable",
            allowInsecureHttp = false,
        )

        assertEquals("Cleartext HTTP is disabled for this build.", config.configurationError())
    }

    @Test
    fun httpsConfigIsAllowedWhenCleartextIsDisabled() {
        val config = SupabaseConfig(
            baseUrl = "https://cloud.example.com",
            publishableKey = "publishable",
            allowInsecureHttp = false,
        )

        assertNull(config.configurationError())
    }

    @Test
    fun malformedAndUnsupportedUrlsAreRejected() {
        val invalidUrls = listOf(
            "\"http://10.0.0.8:8000\"",
            "http:///rest/v1",
            "ftp://cloud.example.com",
            "http://cloud.\uFEFFexample.com",
        )

        invalidUrls.forEach { invalidUrl ->
            val config = SupabaseConfig(
                baseUrl = invalidUrl,
                publishableKey = "publishable",
                allowInsecureHttp = true,
            )

            assertEquals("Supabase URL is invalid.", config.configurationError())
        }
    }

    @Test
    fun injectedBuildConfigIsEitherAbsentOrFullySanitizedAndUsable() {
        val url = BuildConfig.SUPABASE_URL
        val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        if (url.isBlank() && key.isBlank()) return

        assertTrue(url.isNotBlank())
        assertTrue(key.isNotBlank())
        assertFalse(url.contains('\uFEFF'))
        assertFalse(key.contains('\uFEFF'))
        val config = SupabaseConfig(
            baseUrl = url,
            publishableKey = key,
            allowInsecureHttp = BuildConfig.ALLOW_INSECURE_SUPABASE_HTTP,
        )
        assertNull(config.configurationError())
    }
}
