package com.milesxue.pixeldone.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

internal class WidgetCompletionFeedbackCleanupScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun schedule(
        appWidgetId: Int,
        feedback: WidgetCompletionFeedback,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val request = OneTimeWorkRequestBuilder<WidgetCompletionFeedbackCleanupWorker>()
            .setInputData(
                workDataOf(
                    WidgetCompletionFeedbackCleanupWorker.AppWidgetIdKey to appWidgetId,
                    WidgetCompletionFeedbackCleanupWorker.ChecklistIdKey to feedback.checklistId,
                    WidgetCompletionFeedbackCleanupWorker.TodoIdKey to feedback.todoId,
                    WidgetCompletionFeedbackCleanupWorker.TokenKey to feedback.token,
                    WidgetCompletionFeedbackCleanupWorker.ExpiresAtMillisKey to
                        feedback.expiresAtMillis,
                ),
            )
            .setInitialDelay(
                widgetCompletionFeedbackDelayMillis(
                    expiresAtMillis = feedback.expiresAtMillis,
                    nowMillis = nowMillis,
                ),
                TimeUnit.MILLISECONDS,
            )
            .build()
        workManager.enqueueUniqueWork(
            widgetCompletionFeedbackWorkName(appWidgetId, feedback.token),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}

internal class WidgetCompletionFeedbackCleanupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val appWidgetId = inputData.getInt(
            AppWidgetIdKey,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return Result.success()
        val feedback = WidgetCompletionFeedback(
            checklistId = inputData.getString(ChecklistIdKey).orEmpty(),
            todoId = inputData.getString(TodoIdKey).orEmpty(),
            token = inputData.getString(TokenKey).orEmpty(),
            expiresAtMillis = inputData.getLong(ExpiresAtMillisKey, 0L),
        )
        if (
            feedback.checklistId.isBlank() ||
            feedback.todoId.isBlank() ||
            feedback.token.isBlank() ||
            feedback.expiresAtMillis <= 0L
        ) {
            return Result.success()
        }

        val glanceId = try {
            GlanceAppWidgetManager(applicationContext).getGlanceIdBy(appWidgetId)
        } catch (error: IllegalArgumentException) {
            // The host has already removed this widget ID, so no cleanup work remains.
            return Result.success()
        }

        return try {
            runWidgetCompletionFeedbackCleanup(
                removeExpiredState = {
                    PixelDoneWidgetStateStore.removeExpiredCompletionFeedback(
                        context = applicationContext,
                        glanceId = glanceId,
                        feedback = feedback,
                    )
                },
                updateWidget = {
                    PixelDoneWidget().update(applicationContext, glanceId)
                },
            )
            Result.success()
        } catch (error: Exception) {
            Log.e(LogTag, "Unable to clean widget completion feedback", error)
            Result.retry()
        }
    }

    internal companion object {
        const val AppWidgetIdKey = "app_widget_id"
        const val ChecklistIdKey = "checklist_id"
        const val TodoIdKey = "todo_id"
        const val TokenKey = "token"
        const val ExpiresAtMillisKey = "expires_at_millis"
        private const val LogTag = "PixelDoneWidget"
    }
}

internal fun widgetCompletionFeedbackDelayMillis(
    expiresAtMillis: Long,
    nowMillis: Long,
): Long = (expiresAtMillis - nowMillis).coerceAtLeast(0L)

internal fun widgetCompletionFeedbackFinalExpiryMillis(
    visibleUntilMillis: Long,
    completionFinishedAtMillis: Long,
): Long = maxOf(
    visibleUntilMillis,
    completionFinishedAtMillis + 1L,
)

internal suspend fun runWidgetCompletionFeedbackCleanup(
    removeExpiredState: suspend () -> Unit,
    updateWidget: suspend () -> Unit,
) {
    removeExpiredState()
    // Always publish. A previous attempt may have removed the state and then failed while updating
    // RemoteViews; retries must still replace that stale checked row.
    updateWidget()
}

internal fun widgetCompletionFeedbackWorkName(
    appWidgetId: Int,
    token: String,
): String = "PixelDoneWidgetFeedback:$appWidgetId:$token"
