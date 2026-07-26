package com.milesxue.pixeldone.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelDoneWidgetStateStoreTest {
    @Test
    fun feedbackJsonRoundTripsMultipleTodos() {
        val feedback = listOf(
            feedback(todoId = "todo-a", token = "token-a", expiresAtMillis = 3_000L),
            feedback(todoId = "todo-b", token = "token-b", expiresAtMillis = 4_000L),
        )

        val encoded = encodeWidgetCompletionFeedback(feedback)

        assertEquals(
            feedback,
            decodeWidgetCompletionFeedback(
                raw = encoded,
                nowMillis = 1_000L,
            ),
        )
    }

    @Test
    fun malformedAndStructurallyInvalidFeedbackIsIgnored() {
        assertTrue(
            decodeWidgetCompletionFeedback(
                raw = "{not-json",
                nowMillis = 1_000L,
            ).isEmpty(),
        )
        assertTrue(
            decodeWidgetCompletionFeedback(
                raw = """
                    [
                      {
                        "checklist_id":"",
                        "todo_id":"todo-a",
                        "token":"token-a",
                        "expires_at_millis":3000
                      }
                    ]
                """.trimIndent(),
                nowMillis = 1_000L,
            ).isEmpty(),
        )
        assertNull(encodeWidgetCompletionFeedback(emptyList()))
    }

    @Test
    fun completionRemainsVisibleUntilExactlyTwoSeconds() {
        val encoded = encodeWidgetCompletionFeedback(
            listOf(
                feedback(
                    todoId = "todo-a",
                    token = "token-a",
                    expiresAtMillis = 3_000L,
                ),
            ),
        )

        assertEquals(
            1,
            decodeWidgetCompletionFeedback(
                raw = encoded,
                nowMillis = 2_999L,
            ).size,
        )
        assertTrue(
            decodeWidgetCompletionFeedback(
                raw = encoded,
                nowMillis = 3_000L,
            ).isEmpty(),
        )
    }

    @Test
    fun duplicateTodoFeedbackDoesNotReplaceTokenOrExtendExpiry() {
        val original = feedback(
            todoId = "todo-a",
            token = "first-token",
            expiresAtMillis = 3_000L,
        )
        val duplicate = feedback(
            todoId = "todo-a",
            token = "second-token",
            expiresAtMillis = 5_000L,
        )

        val merged = mergeWidgetCompletionFeedback(
            current = listOf(original),
            feedback = duplicate,
            nowMillis = 1_500L,
        )

        assertEquals(listOf(original), merged)
    }

    @Test
    fun multipleTodosCanHoldIndependentFeedback() {
        val first = feedback(
            todoId = "todo-a",
            token = "token-a",
            expiresAtMillis = 3_000L,
        )
        val second = feedback(
            todoId = "todo-b",
            token = "token-b",
            expiresAtMillis = 3_500L,
        )

        val merged = mergeWidgetCompletionFeedback(
            current = listOf(first),
            feedback = second,
            nowMillis = 1_000L,
        )

        assertEquals(listOf(first, second), merged)
        assertFalse(merged[0].sameIdentityAs(merged[1]))
    }

    @Test
    fun expiredAndDuplicateStoredEntriesAreNormalizedDeterministically() {
        val first = feedback(
            todoId = "todo-a",
            token = "first-token",
            expiresAtMillis = 3_000L,
        )
        val duplicate = feedback(
            todoId = "todo-a",
            token = "duplicate-token",
            expiresAtMillis = 4_000L,
        )
        val expired = feedback(
            todoId = "todo-b",
            token = "expired-token",
            expiresAtMillis = 999L,
        )
        val encoded = encodeWidgetCompletionFeedback(listOf(first, duplicate, expired))

        assertEquals(
            listOf(first),
            decodeWidgetCompletionFeedback(
                raw = encoded,
                nowMillis = 1_000L,
            ),
        )
    }

    private fun feedback(
        todoId: String,
        token: String,
        expiresAtMillis: Long,
    ) = WidgetCompletionFeedback(
        checklistId = "main",
        todoId = todoId,
        token = token,
        expiresAtMillis = expiresAtMillis,
    )

    private fun WidgetCompletionFeedback.sameIdentityAs(
        other: WidgetCompletionFeedback,
    ): Boolean = checklistId == other.checklistId && todoId == other.todoId
}
