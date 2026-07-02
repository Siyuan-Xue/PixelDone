package com.milesxue.pixeldone

import com.milesxue.pixeldone.domain.todo.DefaultChecklistId
import com.milesxue.pixeldone.domain.todo.SettingsChecklistId
import com.milesxue.pixeldone.domain.todo.TrashChecklistId
import com.milesxue.pixeldone.ui.todo.nextChecklistBackNavigation
import com.milesxue.pixeldone.ui.todo.pushChecklistBackStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChecklistBackStackTest {
    @Test
    fun checklistBackStackRecordsNormalSettingsAndTrashHistory() {
        val validIds = setOf(DefaultChecklistId, "work", TrashChecklistId, SettingsChecklistId)
        var stack = emptyList<String>()

        stack = pushChecklistBackStack(
            stack = stack,
            currentId = DefaultChecklistId,
            targetId = SettingsChecklistId,
            validIds = validIds,
        )
        stack = pushChecklistBackStack(
            stack = stack,
            currentId = SettingsChecklistId,
            targetId = TrashChecklistId,
            validIds = validIds,
        )

        val firstBack = nextChecklistBackNavigation(stack, validIds, TrashChecklistId)
        assertEquals(SettingsChecklistId, firstBack?.targetId)
        assertEquals(listOf(DefaultChecklistId), firstBack?.remainingStack)

        val secondBack = nextChecklistBackNavigation(firstBack!!.remainingStack, validIds, SettingsChecklistId)
        assertEquals(DefaultChecklistId, secondBack?.targetId)
        assertEquals(emptyList<String>(), secondBack?.remainingStack)
    }

    @Test
    fun checklistBackStackDoesNotRecordCurrentChecklistSelection() {
        val validIds = setOf(DefaultChecklistId, TrashChecklistId, SettingsChecklistId)

        val stack = pushChecklistBackStack(
            stack = emptyList(),
            currentId = DefaultChecklistId,
            targetId = DefaultChecklistId,
            validIds = validIds,
        )

        assertEquals(emptyList<String>(), stack)
        assertNull(nextChecklistBackNavigation(stack, validIds, DefaultChecklistId))
    }

    @Test
    fun checklistBackStackFallsThroughWhenNoValidPreviousChecklistExists() {
        val validIds = setOf(DefaultChecklistId, TrashChecklistId, SettingsChecklistId)

        assertNull(nextChecklistBackNavigation(emptyList(), validIds, DefaultChecklistId))
        assertNull(nextChecklistBackNavigation(listOf(DefaultChecklistId), validIds, DefaultChecklistId))
        assertNull(nextChecklistBackNavigation(listOf("deleted"), validIds, DefaultChecklistId))
    }

    @Test
    fun checklistBackStackSkipsDeletedChecklistIds() {
        val validIds = setOf(DefaultChecklistId, TrashChecklistId, SettingsChecklistId)
        val stack = listOf(DefaultChecklistId, "deleted", SettingsChecklistId)

        val navigation = nextChecklistBackNavigation(stack, validIds, TrashChecklistId)

        assertEquals(SettingsChecklistId, navigation?.targetId)
        assertEquals(listOf(DefaultChecklistId), navigation?.remainingStack)
    }

    @Test
    fun checklistBackStackKeepsRealHistoryForLoopNavigation() {
        val validIds = setOf(DefaultChecklistId, "work", TrashChecklistId, SettingsChecklistId)
        var stack = emptyList<String>()
        stack = pushChecklistBackStack(stack, DefaultChecklistId, "work", validIds)
        stack = pushChecklistBackStack(stack, "work", DefaultChecklistId, validIds)

        val firstBack = nextChecklistBackNavigation(stack, validIds, DefaultChecklistId)
        assertEquals("work", firstBack?.targetId)
        assertEquals(listOf(DefaultChecklistId), firstBack?.remainingStack)

        val secondBack = nextChecklistBackNavigation(firstBack!!.remainingStack, validIds, "work")
        assertEquals(DefaultChecklistId, secondBack?.targetId)
        assertEquals(emptyList<String>(), secondBack?.remainingStack)
    }
}
