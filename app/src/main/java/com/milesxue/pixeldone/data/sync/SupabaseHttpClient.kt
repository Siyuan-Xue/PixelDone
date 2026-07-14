package com.milesxue.pixeldone.data.sync

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal interface SupabaseRequestClient {
    suspend fun request(
        method: String,
        path: String,
        bearerToken: String? = null,
        query: List<Pair<String, String>> = emptyList(),
        prefer: String? = null,
        body: String? = null,
    ): String
}

internal class SupabaseHttpClient(
    private val config: SupabaseConfig,
) : SupabaseRequestClient {
    override suspend fun request(
        method: String,
        path: String,
        bearerToken: String?,
        query: List<Pair<String, String>>,
        prefer: String?,
        body: String?,
    ): String = withContext(Dispatchers.IO) {
        config.configurationError()?.let { throw SyncConfigurationException(it) }
        val url = URL(config.normalizedBaseUrl + path + query.toQuerySuffix())
        var connection: HttpURLConnection? = null
        try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("apikey", config.publishableKey)
                bearerToken?.let { setRequestProperty("Authorization", "Bearer $it") }
                prefer?.let { setRequestProperty("Prefer", it) }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
            if (body != null) {
                connection.outputStream.use { output ->
                    output.write(body.toByteArray(Charsets.UTF_8))
                }
            }
            val code = connection.responseCode
            val responseBody = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            if (code !in 200..299) {
                val error = parseSupabaseError(responseBody, code)
                throw SyncRemoteException(
                    message = error.message,
                    statusCode = code,
                    remoteCode = error.code,
                )
            }
            responseBody
        } catch (error: CancellationException) {
            throw error
        } catch (error: SyncConfigurationException) {
            throw error
        } catch (error: SyncRemoteException) {
            throw error
        } catch (error: Exception) {
            throw SyncNetworkException(
                message = error.message ?: "Supabase network request failed.",
                cause = error,
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun List<Pair<String, String>>.toQuerySuffix(): String {
        if (isEmpty()) return ""
        return joinToString(prefix = "?", separator = "&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
}

internal data class SupabaseErrorDetails(
    val code: String?,
    val message: String,
)

private val SupabaseErrorJson = Json { ignoreUnknownKeys = true }

internal fun parseSupabaseError(responseBody: String, statusCode: Int): SupabaseErrorDetails {
    val payload = responseBody.takeIf(String::isNotBlank)?.let { body ->
        runCatching { SupabaseErrorJson.decodeFromString(SupabaseErrorPayload.serializer(), body) }.getOrNull()
    }
    return SupabaseErrorDetails(
        code = payload?.errorCode?.trim()?.takeIf(String::isNotEmpty),
        message = payload?.message?.takeIf(String::isNotBlank)
            ?: payload?.msg?.takeIf(String::isNotBlank)
            ?: responseBody.takeIf(String::isNotBlank)
            ?: "Supabase request failed with HTTP $statusCode.",
    )
}

@Serializable
private data class SupabaseErrorPayload(
    @SerialName("error_code") val errorCode: String? = null,
    val message: String? = null,
    val msg: String? = null,
)
