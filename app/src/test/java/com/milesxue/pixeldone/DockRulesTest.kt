package com.milesxue.pixeldone

import com.milesxue.pixeldone.domain.todo.AllDockActions
import com.milesxue.pixeldone.domain.todo.DockAction
import com.milesxue.pixeldone.domain.todo.DockConfig
import com.milesxue.pixeldone.domain.todo.DockItem
import com.milesxue.pixeldone.domain.todo.DockPlusPlacement
import com.milesxue.pixeldone.domain.todo.centerDockActionSides
import com.milesxue.pixeldone.domain.todo.normalizeDockActions
import com.milesxue.pixeldone.domain.todo.orderedDockItems
import org.junit.Assert.assertEquals
import org.junit.Test

class DockRulesTest {
    @Test
    fun allDockActionsIncludesOptionalQuickDeleteMode() {
        assertEquals(
            listOf(
                DockAction.SORT,
                DockAction.DEADLINE,
                DockAction.HIDE_DONE,
                DockAction.DELETE_DONE,
                DockAction.BATCH_DELETE,
            ),
            AllDockActions,
        )
    }

    @Test
    fun defaultDockPlacesSortLeftAndDeadlineRightOfCenteredAdd() {
        val items = orderedDockItems(DockConfig())

        assertEquals(
            listOf(
                DockItem.Action(DockAction.SORT),
                DockItem.Add,
                DockItem.Action(DockAction.DEADLINE),
            ),
            items,
        )
    }

    @Test
    fun centeredDockGivesOddExtraActionToLeftSide() {
        val sides = centerDockActionSides(
            listOf(
                DockAction.SORT,
                DockAction.DEADLINE,
                DockAction.HIDE_DONE,
            ),
        )

        assertEquals(listOf(DockAction.SORT, DockAction.DEADLINE), sides.left)
        assertEquals(listOf(DockAction.HIDE_DONE), sides.right)
    }

    @Test
    fun edgeDockPlacesAddAtRequestedEdge() {
        val actions = listOf(DockAction.SORT, DockAction.DEADLINE)

        assertEquals(
            listOf(
                DockItem.Add,
                DockItem.Action(DockAction.SORT),
                DockItem.Action(DockAction.DEADLINE),
            ),
            orderedDockItems(DockConfig(DockPlusPlacement.LEFT_EDGE, actions)),
        )
        assertEquals(
            listOf(
                DockItem.Action(DockAction.SORT),
                DockItem.Action(DockAction.DEADLINE),
                DockItem.Add,
            ),
            orderedDockItems(DockConfig(DockPlusPlacement.RIGHT_EDGE, actions)),
        )
    }

    @Test
    fun duplicateDockActionsAreRemovedInFirstSeenOrder() {
        val actions = normalizeDockActions(
            listOf(
                DockAction.HIDE_DONE,
                DockAction.SORT,
                DockAction.HIDE_DONE,
                DockAction.DEADLINE,
                DockAction.SORT,
                DockAction.BATCH_DELETE,
            ),
        )

        assertEquals(
            listOf(
                DockAction.HIDE_DONE,
                DockAction.SORT,
                DockAction.DEADLINE,
                DockAction.BATCH_DELETE,
            ),
            actions,
        )
    }
}
