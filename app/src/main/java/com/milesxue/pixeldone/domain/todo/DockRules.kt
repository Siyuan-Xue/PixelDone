package com.milesxue.pixeldone.domain.todo

enum class DockPlusPlacement {
    CENTER,
    LEFT_EDGE,
    RIGHT_EDGE,
}

enum class DockAction {
    SORT,
    DEADLINE,
    HIDE_DONE,
    DELETE_DONE,
}

data class DockConfig(
    val plusPlacement: DockPlusPlacement = DockPlusPlacement.CENTER,
    val actions: List<DockAction> = DefaultDockActions,
) {
    fun normalized(): DockConfig = copy(actions = normalizeDockActions(actions))
}

data class CenterDockActionSides(
    val left: List<DockAction>,
    val right: List<DockAction>,
)

sealed interface DockItem {
    data object Add : DockItem
    data class Action(val action: DockAction) : DockItem
}

val DefaultDockActions: List<DockAction> = listOf(
    DockAction.SORT,
    DockAction.DEADLINE,
)

val AllDockActions: List<DockAction> = listOf(
    DockAction.SORT,
    DockAction.DEADLINE,
    DockAction.HIDE_DONE,
    DockAction.DELETE_DONE,
)

fun normalizeDockActions(actions: List<DockAction>): List<DockAction> {
    val seen = mutableSetOf<DockAction>()
    return actions.filter { action ->
        action in AllDockActions && seen.add(action)
    }
}

fun centerDockActionSides(actions: List<DockAction>): CenterDockActionSides {
    val normalizedActions = normalizeDockActions(actions)
    val leftCount = (normalizedActions.size + 1) / 2
    return CenterDockActionSides(
        left = normalizedActions.take(leftCount),
        right = normalizedActions.drop(leftCount),
    )
}

fun orderedDockItems(config: DockConfig): List<DockItem> {
    val normalizedConfig = config.normalized()
    val actions = normalizedConfig.actions.map(DockItem::Action)
    return when (normalizedConfig.plusPlacement) {
        DockPlusPlacement.CENTER -> {
            val sides = centerDockActionSides(normalizedConfig.actions)
            sides.left.map(DockItem::Action) + DockItem.Add + sides.right.map(DockItem::Action)
        }
        DockPlusPlacement.LEFT_EDGE -> listOf(DockItem.Add) + actions
        DockPlusPlacement.RIGHT_EDGE -> actions + DockItem.Add
    }
}
