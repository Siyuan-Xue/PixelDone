package com.milesxue.pixeldone

import com.milesxue.pixeldone.data.sync.parseSupabaseError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupabaseHttpClientTest {
    @Test
    fun parsesStableSupabaseErrorCodeAndMessage() {
        val error = parseSupabaseError(
            responseBody = """{"code":400,"error_code":"refresh_token_not_found","msg":"Invalid Refresh Token"}""",
            statusCode = 400,
        )

        assertEquals("refresh_token_not_found", error.code)
        assertEquals("Invalid Refresh Token", error.message)
    }

    @Test
    fun parsesPostgrestDatabaseErrorCode() {
        val error = parseSupabaseError(
            responseBody = """{"code":"42883","message":"operator does not exist"}""",
            statusCode = 404,
        )

        assertEquals("42883", error.code)
        assertEquals("operator does not exist", error.message)
    }

    @Test
    fun preservesNonJsonBodyWithoutInventingAnErrorCode() {
        val error = parseSupabaseError("gateway unavailable", 503)

        assertNull(error.code)
        assertEquals("gateway unavailable", error.message)
    }
}
