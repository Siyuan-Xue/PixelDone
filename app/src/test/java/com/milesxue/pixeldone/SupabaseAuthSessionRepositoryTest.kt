package com.milesxue.pixeldone

import com.milesxue.pixeldone.data.sync.AuthSessionStore
import com.milesxue.pixeldone.data.sync.AuthSessionExpiredException
import com.milesxue.pixeldone.data.sync.SupabaseAuthSessionRepository
import com.milesxue.pixeldone.data.sync.SupabaseConfig
import com.milesxue.pixeldone.data.sync.SyncRemoteException
import com.milesxue.pixeldone.data.sync.SupabaseRequestClient
import com.milesxue.pixeldone.domain.sync.AuthSession
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseAuthSessionRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun refreshSessionIfNeededRefreshesExpiredSessionAndPersistsRotatedToken() = runTest {
        val expired = signedInSession(
            accessToken = "old-access",
            refreshToken = "old-refresh",
            expiresAtMillis = 1_000L,
        )
        val store = InMemoryAuthSessionStore(expired)
        val client = RecordingSessionRequestClient()
        val repository = SupabaseAuthSessionRepository(
            config = TestConfig,
            httpClient = client,
            sessionStore = store,
        )

        val refreshed = repository.refreshSessionIfNeeded(nowMillis = 2_000L)

        assertEquals("new-access", refreshed.accessToken)
        assertEquals("new-refresh", refreshed.refreshToken)
        assertEquals(3_602_000L, refreshed.expiresAtMillis)
        assertEquals(refreshed, store.savedSession)
        assertEquals(refreshed, repository.session.value)

        val request = client.requests.single()
        assertEquals("POST", request.method)
        assertEquals("/auth/v1/token", request.path)
        assertEquals(listOf("grant_type" to "refresh_token"), request.query)
        val body = json.parseToJsonElement(requireNotNull(request.body)).jsonObject
        assertEquals("old-refresh", body.getValue("refresh_token").jsonPrimitive.content)
    }

    @Test
    fun refreshSessionIfNeededKeepsFreshSessionWithoutNetwork() = runTest {
        val fresh = signedInSession(expiresAtMillis = 10_000_000L)
        val store = InMemoryAuthSessionStore(fresh)
        val client = RecordingSessionRequestClient()
        val repository = SupabaseAuthSessionRepository(
            config = TestConfig,
            httpClient = client,
            sessionStore = store,
        )

        val result = repository.refreshSessionIfNeeded(nowMillis = 2_000L)

        assertEquals(fresh, result)
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun forceRefreshRefreshesFreshSession() = runTest {
        val fresh = signedInSession(expiresAtMillis = 10_000_000L)
        val store = InMemoryAuthSessionStore(fresh)
        val client = RecordingSessionRequestClient()
        val repository = SupabaseAuthSessionRepository(
            config = TestConfig,
            httpClient = client,
            sessionStore = store,
        )

        val refreshed = repository.refreshSessionIfNeeded(nowMillis = 2_000L, force = true)

        assertEquals("new-access", refreshed.accessToken)
        assertEquals(1, client.requests.size)
    }

    @Test
    fun terminalRefreshErrorsClearCredentialsAndRequireSignIn() = runTest {
        val terminalCodes = listOf(
            "refresh_token_not_found",
            "refresh_token_already_used",
            "session_expired",
            "session_not_found",
            "invalid_credentials",
        )

        terminalCodes.forEach { remoteCode ->
            val original = signedInSession(expiresAtMillis = 1_000L)
            val store = InMemoryAuthSessionStore(original)
            val repository = SupabaseAuthSessionRepository(
                config = TestConfig,
                httpClient = FailingSessionRequestClient(SyncRemoteException("Rejected", 400, remoteCode)),
                sessionStore = store,
            )

            val error = runCatching {
                repository.refreshSessionIfNeeded(nowMillis = 2_000L)
            }.exceptionOrNull()

            assertTrue("$remoteCode must end the local session", error is AuthSessionExpiredException)
            assertNull(store.load())
            assertEquals(1, store.clearCount)
            assertEquals(false, repository.session.value.signedIn)
        }
    }

    @Test
    fun transientRefreshErrorsPreserveCredentials() = runTest {
        val failures = listOf(
            SyncRemoteException("Rate limited", 429, "over_request_rate_limit"),
            SyncRemoteException("Unavailable", 503, null),
            com.milesxue.pixeldone.data.sync.SyncNetworkException("offline"),
        )

        failures.forEach { failure ->
            val original = signedInSession(expiresAtMillis = 1_000L)
            val store = InMemoryAuthSessionStore(original)
            val repository = SupabaseAuthSessionRepository(
                config = TestConfig,
                httpClient = FailingSessionRequestClient(failure),
                sessionStore = store,
            )

            val error = runCatching {
                repository.refreshSessionIfNeeded(nowMillis = 2_000L)
            }.exceptionOrNull()

            assertSame(failure, error)
            assertEquals(original, store.load())
            assertEquals(original, repository.session.value)
            assertEquals(0, store.clearCount)
        }
    }

    @Test
    fun signUpPostsSignupRequestAndPersistsReturnedSession() = runTest {
        val store = InMemoryAuthSessionStore(null)
        val client = RecordingSessionRequestClient(SignUpResponse)
        val repository = SupabaseAuthSessionRepository(
            config = TestConfig,
            httpClient = client,
            sessionStore = store,
        )

        val session = repository.signUp(" new@example.com ", "secret")

        assertEquals(true, session.signedIn)
        assertEquals("signup-access", session.accessToken)
        assertEquals("signup-refresh", session.refreshToken)
        assertEquals("new@example.com", session.userEmail)
        assertEquals(session, store.savedSession)
        assertEquals(session, repository.session.value)

        val request = client.requests.single()
        assertEquals("POST", request.method)
        assertEquals("/auth/v1/signup", request.path)
        assertEquals(emptyList<Pair<String, String>>(), request.query)
        val body = json.parseToJsonElement(requireNotNull(request.body)).jsonObject
        assertEquals("new@example.com", body.getValue("email").jsonPrimitive.content)
        assertEquals("secret", body.getValue("password").jsonPrimitive.content)
    }

    @Test
    fun signUpWithoutAutoconfirmSessionFailsClearly() = runTest {
        val store = InMemoryAuthSessionStore(null)
        val client = RecordingSessionRequestClient(SignUpWithoutSessionResponse)
        val repository = SupabaseAuthSessionRepository(
            config = TestConfig,
            httpClient = client,
            sessionStore = store,
        )

        val error = runCatching {
            repository.signUp("new@example.com", "secret")
        }.exceptionOrNull()

        assertTrue(error is SyncRemoteException)
        assertEquals("Sign up did not return a session. Enable Supabase email autoconfirm.", error?.message)
        assertNull(store.savedSession)
    }

    @Test
    fun changePasswordReauthenticatesUpdatesAndGloballySignsOut() = runTest {
        val original = signedInSession(expiresAtMillis = 10_000_000L)
        val store = InMemoryAuthSessionStore(original)
        val client = PasswordChangeRequestClient()
        val repository = SupabaseAuthSessionRepository(TestConfig, client, store)

        val result = repository.changePassword("old-secret", "new-secret")

        assertTrue(result.globalLogoutCompleted)
        assertEquals(false, repository.session.value.signedIn)
        assertNull(store.load())
        assertEquals(listOf("/auth/v1/token", "/auth/v1/user", "/auth/v1/logout"), client.requests.map { it.path })
        assertEquals(listOf("grant_type" to "password"), client.requests[0].query)
        assertEquals("reauth-access", client.requests[1].bearerToken)
        assertEquals(listOf("scope" to "global"), client.requests[2].query)
        val updateBody = json.parseToJsonElement(requireNotNull(client.requests[1].body)).jsonObject
        assertEquals("new-secret", updateBody.getValue("password").jsonPrimitive.content)
    }

    @Test
    fun changePasswordLeavesCurrentSessionWhenOldPasswordIsRejected() = runTest {
        val original = signedInSession(expiresAtMillis = 10_000_000L)
        val store = InMemoryAuthSessionStore(original)
        val client = PasswordChangeRequestClient(rejectReauthentication = true)
        val repository = SupabaseAuthSessionRepository(TestConfig, client, store)

        val error = runCatching { repository.changePassword("wrong", "new-secret") }.exceptionOrNull()

        assertTrue(error is SyncRemoteException)
        assertEquals(original, repository.session.value)
        assertEquals(original, store.load())
        assertEquals(1, client.requests.size)
    }

    private fun signedInSession(
        accessToken: String = "access-token",
        refreshToken: String = "refresh-token",
        expiresAtMillis: Long,
    ): AuthSession = AuthSession(
        signedIn = true,
        userId = "user-1",
        userEmail = "user@example.com",
        displayLabel = "user@example.com",
        cloudAvailable = true,
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAtMillis = expiresAtMillis,
        insecureHttpAllowed = true,
    )

    private companion object {
        val TestConfig = SupabaseConfig(
            baseUrl = "http://127.0.0.1:8000",
            publishableKey = "publishable-key",
            allowInsecureHttp = true,
        )
        const val SignUpResponse = """
            {
              "access_token":"signup-access",
              "refresh_token":"signup-refresh",
              "expires_in":3600,
              "user":{"id":"user-2","email":"new@example.com"}
            }
        """
        const val SignUpWithoutSessionResponse = """
            {
              "user":{"id":"user-2","email":"new@example.com"}
            }
        """
    }
}

private class PasswordChangeRequestClient(
    private val rejectReauthentication: Boolean = false,
) : SupabaseRequestClient {
    val requests = mutableListOf<RecordedSessionRequest>()

    override suspend fun request(
        method: String,
        path: String,
        bearerToken: String?,
        query: List<Pair<String, String>>,
        prefer: String?,
        body: String?,
    ): String {
        requests += RecordedSessionRequest(method, path, bearerToken, query, prefer, body)
        if (path == "/auth/v1/token") {
            if (rejectReauthentication) throw SyncRemoteException("Invalid login credentials", 400)
            return """{"access_token":"reauth-access","refresh_token":"reauth-refresh","expires_in":3600,"user":{"id":"user-1","email":"user@example.com"}}"""
        }
        return "{}"
    }
}

private data class RecordedSessionRequest(
    val method: String,
    val path: String,
    val bearerToken: String?,
    val query: List<Pair<String, String>>,
    val prefer: String?,
    val body: String?,
)

private class RecordingSessionRequestClient(
    private val responseBody: String = RefreshResponse,
) : SupabaseRequestClient {
    val requests = mutableListOf<RecordedSessionRequest>()

    override suspend fun request(
        method: String,
        path: String,
        bearerToken: String?,
        query: List<Pair<String, String>>,
        prefer: String?,
        body: String?,
    ): String {
        requests += RecordedSessionRequest(
            method = method,
            path = path,
            bearerToken = bearerToken,
            query = query,
            prefer = prefer,
            body = body,
        )
        return responseBody
    }

    private companion object {
        const val RefreshResponse = """
            {
              "access_token":"new-access",
              "refresh_token":"new-refresh",
              "expires_in":3600,
              "user":{"id":"user-1","email":"user@example.com"}
            }
        """
    }
}

private class FailingSessionRequestClient(
    private val failure: Exception,
) : SupabaseRequestClient {
    override suspend fun request(
        method: String,
        path: String,
        bearerToken: String?,
        query: List<Pair<String, String>>,
        prefer: String?,
        body: String?,
    ): String = throw failure
}

private class InMemoryAuthSessionStore(initialSession: AuthSession?) : AuthSessionStore {
    private var storedSession: AuthSession? = initialSession
    var savedSession: AuthSession? = null
        private set
    var clearCount: Int = 0
        private set

    override fun load(): AuthSession? = storedSession

    override fun save(session: AuthSession) {
        storedSession = session
        savedSession = session
    }

    override fun clear() {
        storedSession = null
        clearCount += 1
    }
}
