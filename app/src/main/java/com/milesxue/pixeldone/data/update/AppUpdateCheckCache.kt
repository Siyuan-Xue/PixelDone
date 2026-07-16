package com.milesxue.pixeldone.data.update

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

private const val UpdateCheckPreferences = "pixel_done_update_checks"
private const val CachedAppVersionKey = "cached_app_version"
private const val CachedAtKey = "cached_at"
private const val CachedResultKey = "cached_result"
private const val SuccessfulCheckTtlMillis = 6 * 60 * 60 * 1_000L
private const val FailedCheckTtlMillis = 30 * 60 * 1_000L

internal class AppUpdateCheckCache(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(UpdateCheckPreferences, Context.MODE_PRIVATE)

    fun load(currentVersion: String, nowMillis: Long = System.currentTimeMillis()): AppUpdateCheckResult? {
        if (preferences.getString(CachedAppVersionKey, null) != currentVersion) return null
        val cachedAt = preferences.getLong(CachedAtKey, -1L).takeIf { it >= 0L } ?: return null
        val json = preferences.getString(CachedResultKey, null) ?: return null
        val result = runCatching { decode(JSONObject(json)) }.getOrNull() ?: return null
        val ttl = if (result == AppUpdateCheckResult.Unavailable) {
            FailedCheckTtlMillis
        } else {
            SuccessfulCheckTtlMillis
        }
        return result.takeIf { nowMillis - cachedAt in 0 until ttl }
    }

    fun save(
        currentVersion: String,
        result: AppUpdateCheckResult,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        preferences.edit()
            .putString(CachedAppVersionKey, currentVersion)
            .putLong(CachedAtKey, nowMillis)
            .putString(CachedResultKey, encode(result).toString())
            .apply()
    }

    private fun encode(result: AppUpdateCheckResult): JSONObject = when (result) {
        AppUpdateCheckResult.Current -> JSONObject().put("kind", "current")
        AppUpdateCheckResult.Unavailable -> JSONObject().put("kind", "unavailable")
        is AppUpdateCheckResult.Available -> JSONObject()
            .put("kind", "available")
            .put("version", result.info.version)
            .put("releasePageUrl", result.info.releasePageUrl)
            .put("fileName", result.info.fileName)
            .put(
                "sources",
                JSONArray().apply {
                    result.info.downloadSources.forEach { source ->
                        put(
                            JSONObject()
                                .put("source", source.source.name)
                                .put("url", source.url)
                                .put("checksumUrl", source.checksumUrl),
                        )
                    }
                },
            )
    }

    private fun decode(json: JSONObject): AppUpdateCheckResult? = when (json.getString("kind")) {
        "current" -> AppUpdateCheckResult.Current
        "unavailable" -> AppUpdateCheckResult.Unavailable
        "available" -> {
            val sourcesJson = json.getJSONArray("sources")
            val sources = buildList {
                for (index in 0 until sourcesJson.length()) {
                    val source = sourcesJson.getJSONObject(index)
                    add(
                        AppUpdateDownloadSource(
                            source = AppUpdateSource.valueOf(source.getString("source")),
                            url = source.getString("url"),
                            checksumUrl = source.getString("checksumUrl"),
                        ),
                    )
                }
            }
            AppUpdateCheckResult.Available(
                AppUpdateInfo(
                    version = json.getString("version"),
                    releasePageUrl = json.getString("releasePageUrl"),
                    fileName = json.getString("fileName"),
                    downloadSources = sources,
                ),
            )
        }
        else -> null
    }
}
