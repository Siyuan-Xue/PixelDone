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
    private val httpClient: SupabaseHttpClient,
    private val sessionStore: AuthSessionStore,
) : AuthSessionRepository {
    private val json = Json { encodeDefaults = false; ignoreUnknownKeys = true }
    private val mutableSession = MutableStateFlow(initialSession())
    override val session: StateFlow<AuthSession> = mutableSession.asStateFlow()

    override suspend fun signIn(email: String, password: String): AuthSession {
        config.configurationError()?.let { throw SyncConfigurationException(it) }
        val body = json.encodeToString(
            SignInRequest.serializer(),
            SignInRequest(email = email.trim(), password = password),
        )
        val response = httpClient.request(
            method = "POST",
            path = "/auth/v1/token",
            query = listOf("grant_type" to "password"),
            body = body,
        )
        val token = json.decodeFromString(TokenResponse.serializer(), response)
        val nowMillis = System.currentTimeMillis()
        val session = AuthSession(
            signedIn = true,
            userId = token.user.id,
            userEmail = token.user.email ?: email.trim(),
            displayLabel = token.user.email ?: email.trim(),
            cloudAvailable = true,
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            expiresAtMillis = token.expiresInSeconds?.let { nowMillis + it * 1_000L },
            insecureHttpAllowed = config.allowInsecureHttp,
        )
        sessionStore.save(session)
        mutableSession.value = session
        return session
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
}

@Serializable
private data class SignInRequest(
    val email: String,
    val password: String,
)

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresInSeconds: Long? = null,
    val user: TokenUser,
)

@Serializable
private data class TokenUser(
    val id: String,
    val email: String? = null,
)