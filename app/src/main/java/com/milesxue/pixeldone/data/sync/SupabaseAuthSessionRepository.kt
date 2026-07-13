package com.milesxue.pixeldone.data.sync

import com.milesxue.pixeldone.domain.sync.AuthSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal class SupabaseAuthSessionRepository(
    private val config: SupabaseConfig,
    private val httpClient: SupabaseRequestClient,
    private val sessionStore: AuthSessionStore,
) : AuthSessionRepository {
    private val json = Json { encodeDefaults = false; ignoreUnknownKeys = true }
    private val mutableSession = MutableStateFlow(initialSession())
    override val session: StateFlow<AuthSession> = mutableSession.asStateFlow()

    override suspend fun signIn(email: String, password: String): AuthSession {
        config.configurationError()?.let { throw SyncConfigurationException(it) }
        val emailFallback = email.trim()
        val body = json.encodeToString(
            EmailPasswordRequest.serializer(),
            EmailPasswordRequest(email = emailFallback, password = password),
        )
        val response = httpClient.request(
            method = "POST",
            path = "/auth/v1/token",
            query = listOf("grant_type" to "password"),
            body = body,
        )
        val token = json.decodeFromString(TokenResponse.serializer(), response)
        val session = token.toSession(
            nowMillis = System.currentTimeMillis(),
            allowInsecureHttp = config.allowInsecureHttp,
            emailFallback = emailFallback,
            fallbackRefreshToken = null,
            missingSessionMessage = "Supabase sign in did not return a session.",
        )
        sessionStore.save(session)
        mutableSession.value = session
        return session
    }

    override suspend fun signUp(email: String, password: String): AuthSession {
        config.configurationError()?.let { throw SyncConfigurationException(it) }
        val emailFallback = email.trim()
        val body = json.encodeToString(
            EmailPasswordRequest.serializer(),
            EmailPasswordRequest(email = emailFallback, password = password),
        )
        val response = httpClient.request(
            method = "POST",
            path = "/auth/v1/signup",
            body = body,
        )
        val token = json.decodeFromString(TokenResponse.serializer(), response)
        val session = token.toSession(
            nowMillis = System.currentTimeMillis(),
            allowInsecureHttp = config.allowInsecureHttp,
            emailFallback = emailFallback,
            fallbackRefreshToken = null,
            missingSessionMessage = "Sign up did not return a session. Enable Supabase email autoconfirm.",
        )
        sessionStore.save(session)
        mutableSession.value = session
        return session
    }

    override suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
    ): PasswordChangeResult {
        config.configurationError()?.let { throw SyncConfigurationException(it) }
        val current = mutableSession.value
        val email = current.userEmail?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw SyncConfigurationException("The signed-in account has no email address.")

        // Reauthentication deliberately does not replace the stored session. A bad
        // current password therefore leaves every existing device session untouched.
        val reauthResponse = httpClient.request(
            method = "POST",
            path = "/auth/v1/token",
            query = listOf("grant_type" to "password"),
            body = json.encodeToString(
                EmailPasswordRequest.serializer(),
                EmailPasswordRequest(email = email, password = currentPassword),
            ),
        )
        val reauthenticated = json.decodeFromString(TokenResponse.serializer(), reauthResponse)
        val reauthenticatedToken = reauthenticated.accessToken
            ?: throw SyncRemoteException("Supabase password verification did not return a session.")

        httpClient.request(
            method = "PUT",
            path = "/auth/v1/user",
            bearerToken = reauthenticatedToken,
            body = json.encodeToString(
                PasswordUpdateRequest.serializer(),
                PasswordUpdateRequest(password = newPassword),
            ),
        )

        val globalLogoutCompleted = runCatching {
            httpClient.request(
                method = "POST",
                path = "/auth/v1/logout",
                bearerToken = reauthenticatedToken,
                query = listOf("scope" to "global"),
            )
        }.isSuccess
        sessionStore.clear()
        mutableSession.value = signedOutSession()
        return PasswordChangeResult(globalLogoutCompleted)
    }

    override suspend fun signOut() {
        mutableSession.value.accessToken?.let { token ->
            runCatching {
                httpClient.request(
                    method = "POST",
                    path = "/auth/v1/logout",
                    bearerToken = token,
                )
            }
        }
        sessionStore.clear()
        mutableSession.value = signedOutSession()
    }

    override suspend fun refreshSessionIfNeeded(nowMillis: Long, force: Boolean): AuthSession {
        config.configurationError()?.let { throw SyncConfigurationException(it) }
        val current = mutableSession.value
        if (!current.signedIn) return current
        val refreshToken = current.refreshToken ?: return current
        val expiresAtMillis = current.expiresAtMillis
        val isFresh = expiresAtMillis != null && expiresAtMillis - RefreshSkewMillis > nowMillis
        if (!force && isFresh) return current

        val body = json.encodeToString(
            RefreshRequest.serializer(),
            RefreshRequest(refreshToken = refreshToken),
        )
        val response = httpClient.request(
            method = "POST",
            path = "/auth/v1/token",
            query = listOf("grant_type" to "refresh_token"),
            body = body,
        )
        val token = json.decodeFromString(TokenResponse.serializer(), response)
        val session = token.toSession(
            nowMillis = nowMillis,
            allowInsecureHttp = config.allowInsecureHttp,
            emailFallback = current.userEmail ?: current.displayLabel.takeIf { it != "SIGNED IN" },
            fallbackRefreshToken = refreshToken,
            missingSessionMessage = "Supabase refresh did not return a session.",
        )
        sessionStore.save(session)
        mutableSession.value = session
        return session
    }

    private fun initialSession(): AuthSession {
        val error = config.configurationError()
        if (error != null) {
            return AuthSession(
                displayLabel = if (config.isConfigured) "HTTP BLOCKED" else "NEEDS SETUP",
                cloudAvailable = false,
                configurationError = error,
                insecureHttpAllowed = config.allowInsecureHttp,
            )
        }
        return sessionStore.load()?.copy(
            cloudAvailable = true,
            insecureHttpAllowed = config.allowInsecureHttp,
        ) ?: signedOutSession()
    }

    private fun signedOutSession(): AuthSession = AuthSession(
        displayLabel = "SIGNED OUT",
        cloudAvailable = true,
        insecureHttpAllowed = config.allowInsecureHttp,
    )

    private fun TokenResponse.toSession(
        nowMillis: Long,
        allowInsecureHttp: Boolean,
        emailFallback: String?,
        fallbackRefreshToken: String?,
        missingSessionMessage: String,
    ): AuthSession {
        val sessionAccessToken = accessToken ?: throw SyncRemoteException(missingSessionMessage)
        val email = user.email ?: emailFallback
        return AuthSession(
            signedIn = true,
            userId = user.id,
            userEmail = email,
            displayLabel = email ?: "SIGNED IN",
            cloudAvailable = true,
            accessToken = sessionAccessToken,
            refreshToken = refreshToken ?: fallbackRefreshToken,
            expiresAtMillis = expiresInSeconds?.let { nowMillis + it * 1_000L },
            insecureHttpAllowed = allowInsecureHttp,
        )
    }

    private companion object {
        const val RefreshSkewMillis = 60_000L
    }
}

@Serializable
private data class EmailPasswordRequest(
    val email: String,
    val password: String,
)

@Serializable
private data class PasswordUpdateRequest(
    val password: String,
)

@Serializable
private data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresInSeconds: Long? = null,
    val user: TokenUser,
)

@Serializable
private data class TokenUser(
    val id: String,
    val email: String? = null,
)
