package com.milesxue.pixeldone.data.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.milesxue.pixeldone.domain.sync.AuthSession
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal interface AuthSessionStore {
    fun load(): AuthSession?
    fun save(session: AuthSession)
    fun clear()
}

internal class SecureAuthSessionStore(context: Context) : AuthSessionStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        "pixel_done_auth_session",
        Context.MODE_PRIVATE,
    )
    private val json = Json { encodeDefaults = false; ignoreUnknownKeys = true }

    override fun load(): AuthSession? {
        val encoded = preferences.getString(SessionKey, null) ?: return null
        return runCatching {
            val parts = encoded.split(Separator)
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(Transformation).apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GcmTagBits, iv))
            }
            val plainText = cipher.doFinal(cipherBytes).toString(Charsets.UTF_8)
            json.decodeFromString(StoredAuthSession.serializer(), plainText).toDomain()
        }.getOrElse {
            clear()
            null
        }
    }

    override fun save(session: AuthSession) {
        if (!session.signedIn) {
            clear()
            return
        }
        val plainText = json.encodeToString(StoredAuthSession.serializer(), StoredAuthSession.fromDomain(session))
        val cipher = Cipher.getInstance(Transformation).apply {
            init(Cipher.ENCRYPT_MODE, secretKey())
        }
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val encoded = listOf(
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            Base64.encodeToString(cipherBytes, Base64.NO_WRAP),
        ).joinToString(Separator)
        preferences.edit().putString(SessionKey, encoded).apply()
    }

    override fun clear() {
        preferences.edit().remove(SessionKey).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
        val existing = keyStore.getEntry(KeyAlias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore).run {
            init(
                KeyGenParameterSpec.Builder(
                    KeyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    companion object {
        private const val AndroidKeyStore = "AndroidKeyStore"
        private const val KeyAlias = "PixelDoneSupabaseSessionKey"
        private const val Transformation = "AES/GCM/NoPadding"
        private const val GcmTagBits = 128
        private const val SessionKey = "session"
        private const val Separator = ":"
    }
}

@Serializable
private data class StoredAuthSession(
    @SerialName("user_id") val userId: String,
    @SerialName("user_email") val userEmail: String? = null,
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_at_millis") val expiresAtMillis: Long? = null,
) {
    fun toDomain(): AuthSession = AuthSession(
        signedIn = true,
        userId = userId,
        userEmail = userEmail,
        displayLabel = userEmail ?: "SIGNED IN",
        cloudAvailable = true,
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAtMillis = expiresAtMillis,
    )

    companion object {
        fun fromDomain(session: AuthSession): StoredAuthSession = StoredAuthSession(
            userId = requireNotNull(session.userId),
            userEmail = session.userEmail,
            accessToken = requireNotNull(session.accessToken),
            refreshToken = session.refreshToken,
            expiresAtMillis = session.expiresAtMillis,
        )
    }
}