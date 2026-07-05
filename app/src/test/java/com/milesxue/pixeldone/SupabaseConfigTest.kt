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
    fun releaseHttpConfigIsBlocked() {
        val config = SupabaseConfig(
            baseUrl = "http://10.0.0.8:8000",
            publishableKey = "publishable",
            allowInsecureHttp = false,
        )

        assertTrue(config.isConfigured)
        assertEquals("Cleartext HTTP is disabled for this build.", config.configurationError())
    }
}