package com.milesxue.pixeldone.widget

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
internal data class WidgetCompletionFeedback(
    @SerialName("checklist_id")
    val checklistId: String,
    @SerialName("todo_id")
    val todoId: String,
    val token: String,
    @SerialName("expires_at_millis")
    val expiresAtMillis: Long,
)

internal data class PixelDoneWidgetStateSnapshot(
    val selectedChecklistId: String?,
    val completionFeedbackJson: String?,
)

internal object PixelDoneWidgetStateStore {
    val definition: GlanceStateDefinition<Preferences> = PreferencesGlanceStateDefinition

    private val SelectedChecklistIdKey =
        PixelDonePreferenceKeys.stringKey("selected_checklist_id")
    private val CompletionFeedbackJsonKey =
        PixelDonePreferenceKeys.stringKey("completion_feedback_json")

    suspend fun readPreferences(
        context: Context,
        glanceId: GlanceId,
    ): Preferences = getAppWidgetState(
        context = context.applicationContext,
        definition = definition,
        glanceId = glanceId,
    )

    fun selectedChecklistId(
        context: Context,
        appWidgetId: Int,
        preferences: Preferences,
    ): String? = selectedChecklistId(preferences)
        ?: PixelDoneWidgetConfigStore.legacyChecklistId(context, appWidgetId)

    fun selectedChecklistId(preferences: Preferences): String? =
        preferences[SelectedChecklistIdKey]?.trim()?.takeIf(String::isNotEmpty)

    fun completionFeedback(
        preferences: Preferences,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<WidgetCompletionFeedback> = decodeWidgetCompletionFeedback(
        raw = preferences[CompletionFeedbackJsonKey],
        nowMillis = nowMillis,
    )

    suspend fun replaceConfiguration(
        context: Context,
        glanceId: GlanceId,
        checklistId: String,
    ): PixelDoneWidgetStateSnapshot {
        require(checklistId.isNotBlank()) { "checklistId must not be blank" }
        var previousState: PixelDoneWidgetStateSnapshot? = null
        updateAppWidgetState(context.applicationContext, glanceId) { preferences ->
            previousState = preferences.snapshot()
            preferences[SelectedChecklistIdKey] = checklistId
            preferences.remove(CompletionFeedbackJsonKey)
        }
        return checkNotNull(previousState)
    }

    suspend fun restore(
        context: Context,
        glanceId: GlanceId,
        snapshot: PixelDoneWidgetStateSnapshot,
    ) {
        updateAppWidgetState(context.applicationContext, glanceId) { preferences ->
            preferences.restore(snapshot)
        }
    }

    suspend fun addCompletionFeedback(
        context: Context,
        glanceId: GlanceId,
        feedback: WidgetCompletionFeedback,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!feedback.isValid() || feedback.expiresAtMillis <= nowMillis) return false
        var added = false
        updateAppWidgetState(context.applicationContext, glanceId) { preferences ->
            val current = decodeWidgetCompletionFeedback(
                raw = preferences[CompletionFeedbackJsonKey],
                nowMillis = nowMillis,
            )
            val updated = mergeWidgetCompletionFeedback(
                current = current,
                feedback = feedback,
                nowMillis = nowMillis,
            )
            added = updated.size > current.size
            if (added) {
                preferences.writeCompletionFeedback(updated)
            } else if (preferences[CompletionFeedbackJsonKey] != encodeWidgetCompletionFeedback(current)) {
                preferences.writeCompletionFeedback(current)
            }
        }
        return added
    }

    suspend fun removeCompletionFeedback(
        context: Context,
        glanceId: GlanceId,
        checklistId: String,
        todoId: String,
        token: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        var removed = false
        updateAppWidgetState(context.applicationContext, glanceId) { preferences ->
            val raw = preferences[CompletionFeedbackJsonKey]
            val decoded = decodeValidWidgetCompletionFeedback(
                raw,
            )
            val active = normalizeWidgetCompletionFeedback(decoded, nowMillis)
            val remaining = active.filterNot { candidate ->
                candidate.checklistId == checklistId &&
                    candidate.todoId == todoId &&
                    candidate.token == token
            }
            removed = remaining.size != active.size
            if (raw != encodeWidgetCompletionFeedback(remaining)) {
                preferences.writeCompletionFeedback(remaining)
            }
        }
        return removed
    }

    suspend fun updateCompletionFeedbackExpiry(
        context: Context,
        glanceId: GlanceId,
        feedback: WidgetCompletionFeedback,
        expiresAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): WidgetCompletionFeedback? {
        if (expiresAtMillis <= nowMillis) return null
        var updatedFeedback: WidgetCompletionFeedback? = null
        updateAppWidgetState(context.applicationContext, glanceId) { preferences ->
            val raw = preferences[CompletionFeedbackJsonKey]
            val active = normalizeWidgetCompletionFeedback(
                feedback = decodeValidWidgetCompletionFeedback(raw),
                nowMillis = nowMillis,
            )
            val updated = active.map { candidate ->
                if (
                    candidate.checklistId == feedback.checklistId &&
                    candidate.todoId == feedback.todoId &&
                    candidate.token == feedback.token
                ) {
                    candidate.copy(expiresAtMillis = expiresAtMillis).also {
                        updatedFeedback = it
                    }
                } else {
                    candidate
                }
            }
            if (updatedFeedback != null && raw != encodeWidgetCompletionFeedback(updated)) {
                preferences.writeCompletionFeedback(updated)
            }
        }
        return updatedFeedback
    }

    suspend fun removeExpiredCompletionFeedback(
        context: Context,
        glanceId: GlanceId,
        feedback: WidgetCompletionFeedback,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        var removed = false
        updateAppWidgetState(context.applicationContext, glanceId) { preferences ->
            val decoded = decodeValidWidgetCompletionFeedback(
                preferences[CompletionFeedbackJsonKey],
            )
            val remaining = decoded.filterNot { candidate ->
                candidate.checklistId == feedback.checklistId &&
                    candidate.todoId == feedback.todoId &&
                    candidate.token == feedback.token &&
                    candidate.expiresAtMillis <= nowMillis
            }
            removed = remaining.size != decoded.size
            if (removed) {
                preferences.writeCompletionFeedback(remaining)
            }
        }
        return removed
    }

    suspend fun clear(
        context: Context,
        glanceId: GlanceId,
    ) {
        updateAppWidgetState(context.applicationContext, glanceId) { preferences ->
            preferences.remove(SelectedChecklistIdKey)
            preferences.remove(CompletionFeedbackJsonKey)
        }
    }

    private fun Preferences.snapshot() = PixelDoneWidgetStateSnapshot(
        selectedChecklistId = this[SelectedChecklistIdKey],
        completionFeedbackJson = this[CompletionFeedbackJsonKey],
    )

    private fun MutablePreferences.restore(snapshot: PixelDoneWidgetStateSnapshot) {
        snapshot.selectedChecklistId?.let { selectedChecklistId ->
            this[SelectedChecklistIdKey] = selectedChecklistId
        } ?: remove(SelectedChecklistIdKey)
        snapshot.completionFeedbackJson?.let { feedbackJson ->
            this[CompletionFeedbackJsonKey] = feedbackJson
        } ?: remove(CompletionFeedbackJsonKey)
    }

    private fun MutablePreferences.writeCompletionFeedback(
        feedback: List<WidgetCompletionFeedback>,
    ) {
        encodeWidgetCompletionFeedback(feedback)?.let { encoded ->
            this[CompletionFeedbackJsonKey] = encoded
        } ?: remove(CompletionFeedbackJsonKey)
    }
}

private val WidgetFeedbackJson = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}
private val WidgetFeedbackListSerializer = ListSerializer(WidgetCompletionFeedback.serializer())

internal fun decodeWidgetCompletionFeedback(
    raw: String?,
    nowMillis: Long,
): List<WidgetCompletionFeedback> = normalizeWidgetCompletionFeedback(
    feedback = decodeValidWidgetCompletionFeedback(raw),
    nowMillis = nowMillis,
)

internal fun mergeWidgetCompletionFeedback(
    current: List<WidgetCompletionFeedback>,
    feedback: WidgetCompletionFeedback,
    nowMillis: Long,
): List<WidgetCompletionFeedback> {
    val active = normalizeWidgetCompletionFeedback(current, nowMillis)
    if (!feedback.isValid() || feedback.expiresAtMillis <= nowMillis) return active
    if (active.any { it.sameTodoAs(feedback) }) return active
    return active + feedback
}

internal fun encodeWidgetCompletionFeedback(
    feedback: List<WidgetCompletionFeedback>,
): String? = if (feedback.isEmpty()) {
    null
} else {
    WidgetFeedbackJson.encodeToString(WidgetFeedbackListSerializer, feedback)
}

private fun decodeValidWidgetCompletionFeedback(
    raw: String?,
): List<WidgetCompletionFeedback> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        WidgetFeedbackJson.decodeFromString(WidgetFeedbackListSerializer, raw)
    }.getOrDefault(emptyList())
        .filter(WidgetCompletionFeedback::isValid)
}

private fun normalizeWidgetCompletionFeedback(
    feedback: List<WidgetCompletionFeedback>,
    nowMillis: Long,
): List<WidgetCompletionFeedback> {
    val seenTodos = mutableSetOf<Pair<String, String>>()
    return feedback.filter { candidate ->
        candidate.isValid() &&
            candidate.expiresAtMillis > nowMillis &&
            seenTodos.add(candidate.checklistId to candidate.todoId)
    }
}

private fun WidgetCompletionFeedback.isValid(): Boolean =
    checklistId.isNotBlank() &&
        todoId.isNotBlank() &&
        token.isNotBlank() &&
        expiresAtMillis > 0L

private fun WidgetCompletionFeedback.sameTodoAs(other: WidgetCompletionFeedback): Boolean =
    checklistId == other.checklistId && todoId == other.todoId
