package com.milesxue.pixeldone.ui.todo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milesxue.pixeldone.R
import com.milesxue.pixeldone.domain.todo.DockAction
import com.milesxue.pixeldone.domain.todo.DockConfig
import com.milesxue.pixeldone.domain.todo.DockItem
import com.milesxue.pixeldone.domain.todo.DockPlusPlacement
import com.milesxue.pixeldone.domain.todo.SortMode
import com.milesxue.pixeldone.domain.todo.centerDockActionSides
import com.milesxue.pixeldone.domain.todo.orderedDockItems
import com.milesxue.pixeldone.ui.theme.PixelDoneColors
import com.milesxue.pixeldone.ui.todo.components.FloatingNewTaskButton
import kotlin.math.min

private val DockHeight = 64.dp
private val AddButtonWidth = 56.dp

internal data class DockActionUiState(
    val active: Boolean,
    val enabled: Boolean = true,
)

internal fun dockActionState(
    sortMode: SortMode,
    hideCompleted: Boolean,
    showDeadlineCountdown: Boolean,
    completedCount: Int,
    totalCount: Int,
    batchDeleteActive: Boolean,
): Map<DockAction, DockActionUiState> = mapOf(
    DockAction.SORT to DockActionUiState(active = sortMode == SortMode.TIME),
    DockAction.DEADLINE to DockActionUiState(active = showDeadlineCountdown),
    DockAction.HIDE_DONE to DockActionUiState(active = hideCompleted),
    DockAction.DELETE_DONE to DockActionUiState(
        active = false,
        enabled = completedCount > 0,
    ),
    DockAction.BATCH_DELETE to DockActionUiState(
        active = batchDeleteActive,
        enabled = totalCount > 0,
    ),
    DockAction.EXPORT_MARKDOWN to DockActionUiState(active = false),
)

internal fun previewDockActionState(): Map<DockAction, DockActionUiState> = mapOf(
    DockAction.SORT to DockActionUiState(active = false),
    DockAction.DEADLINE to DockActionUiState(active = false),
    DockAction.HIDE_DONE to DockActionUiState(active = false),
    DockAction.DELETE_DONE to DockActionUiState(active = false),
    DockAction.BATCH_DELETE to DockActionUiState(active = false),
    DockAction.EXPORT_MARKDOWN to DockActionUiState(active = false),
)

@Composable
internal fun BottomActionDock(
    config: DockConfig,
    actionState: Map<DockAction, DockActionUiState>,
    onActionClick: (DockAction) -> Unit,
    onAddClick: () -> Unit,
    onAddLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalizedConfig = config.normalized()
    if (
        normalizedConfig.plusPlacement == DockPlusPlacement.CENTER &&
        normalizedConfig.actions.size <= 4
    ) {
        val sides = centerDockActionSides(normalizedConfig.actions)
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(DockHeight),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.BottomEnd,
            ) {
                DockActionGroup(
                    actions = sides.left,
                    actionState = actionState,
                    onActionClick = onActionClick,
                    modifier = Modifier.padding(end = 18.dp),
                )
            }
            FloatingNewTaskButton(
                onClick = onAddClick,
                onLongClick = onAddLongClick,
                modifier = Modifier.width(AddButtonWidth),
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.BottomStart,
            ) {
                DockActionGroup(
                    actions = sides.right,
                    actionState = actionState,
                    onActionClick = onActionClick,
                    modifier = Modifier.padding(start = 18.dp),
                )
            }
        }
        return
    }

    val items = orderedDockItems(normalizedConfig)
    val spacing = if (normalizedConfig.actions.size >= 5) 5.dp else 18.dp
    val alignment = when (normalizedConfig.plusPlacement) {
        DockPlusPlacement.CENTER -> Alignment.CenterHorizontally
        DockPlusPlacement.LEFT_EDGE -> Alignment.Start
        DockPlusPlacement.RIGHT_EDGE -> Alignment.End
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(DockHeight),
        horizontalArrangement = Arrangement.spacedBy(spacing, alignment),
        verticalAlignment = Alignment.Bottom,
    ) {
        items.forEach { item ->
            when (item) {
                DockItem.Add -> {
                    FloatingNewTaskButton(
                        onClick = onAddClick,
                        onLongClick = onAddLongClick,
                    )
                }
                is DockItem.Action -> {
                    val state = actionState[item.action] ?: DockActionUiState(active = false)
                    DockIconButton(
                        action = item.action,
                        active = state.active,
                        enabled = state.enabled,
                        onClick = { onActionClick(item.action) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DockActionGroup(
    actions: List<DockAction>,
    actionState: Map<DockAction, DockActionUiState>,
    onActionClick: (DockAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        actions.forEach { action ->
            val state = actionState[action] ?: DockActionUiState(active = false)
            DockIconButton(
                action = action,
                active = state.active,
                enabled = state.enabled,
                onClick = { onActionClick(action) },
            )
        }
    }
}

@Composable
private fun DockIconButton(
    action: DockAction,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PixelDoneColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val iconColor = when {
        !enabled -> colors.disabledText
        active -> colors.primary
        else -> colors.textPrimary
    }
    val borderColor = when {
        !enabled -> colors.borderWeak
        active -> colors.primary
        else -> colors.textPrimary
    }
    val backgroundColor = when {
        !enabled -> colors.disabledSurface
        pressed -> colors.surfaceRaised
        else -> colors.selectedSurface
    }
    val actionDescription = action.contentDescription()
    val sortStateDescription = if (active) stringResource(R.string.time) else stringResource(R.string.field_priority)

    Box(
        modifier = modifier
            .size(44.dp)
            .background(backgroundColor, RectangleShape)
            .border(2.dp, borderColor, RectangleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics {
                contentDescription = actionDescription
                if (action == DockAction.SORT) {
                    stateDescription = sortStateDescription
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        DockActionIcon(action = action, color = iconColor, active = active)
    }
}

@Composable
internal fun DockActionIcon(
    action: DockAction,
    color: Color,
    modifier: Modifier = Modifier.size(22.dp),
    active: Boolean = false,
) {
    if (action == DockAction.SORT) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (active) "T" else "P",
                color = color,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    Canvas(modifier = modifier) {
        val iconSize = min(size.width, size.height)
        val left = (size.width - iconSize) / 2f
        val top = (size.height - iconSize) / 2f
        fun x(value: Float): Float = left + iconSize * value / 22f
        fun y(value: Float): Float = top + iconSize * value / 22f
        fun offset(rawX: Float, rawY: Float): Offset = Offset(x(rawX), y(rawY))
        val strokeWidth = iconSize * 2.2f / 22f
        val thinStrokeWidth = iconSize * 1.6f / 22f
        val heavyStrokeWidth = iconSize * 2.8f / 22f
        val iconStroke = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Square,
            join = StrokeJoin.Miter,
        )
        fun rect(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            width: Float = strokeWidth,
        ) {
            drawRect(
                color = color,
                topLeft = offset(left, top),
                size = Size(x(right) - x(left), y(bottom) - y(top)),
                style = Stroke(
                    width = width,
                    cap = StrokeCap.Square,
                    join = StrokeJoin.Miter,
                ),
            )
        }
        fun line(
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            width: Float = strokeWidth,
        ) {
            drawLine(
                color = color,
                start = offset(startX, startY),
                end = offset(endX, endY),
                strokeWidth = width,
                cap = StrokeCap.Square,
            )
        }
        when (action) {
            DockAction.SORT -> Unit
            DockAction.DEADLINE -> {
                rect(4f, 5.5f, 18f, 18f, strokeWidth)
                line(4f, 9f, 18f, 9f, thinStrokeWidth)
                line(7f, 3.5f, 7f, 7.5f, heavyStrokeWidth)
                line(15f, 3.5f, 15f, 7.5f, heavyStrokeWidth)
                drawCircle(
                    color = color,
                    radius = iconSize * 3f / 22f,
                    center = offset(11f, 13.6f),
                    style = Stroke(
                        width = thinStrokeWidth,
                        cap = StrokeCap.Square,
                        join = StrokeJoin.Miter,
                    ),
                )
                line(11f, 13.6f, 11f, 11.7f, thinStrokeWidth)
                line(11f, 13.6f, 13.2f, 14.8f, thinStrokeWidth)
            }
            DockAction.HIDE_DONE -> {
                val eyePath = Path().apply {
                    moveTo(x(3f), y(11f))
                    lineTo(x(7.5f), y(6.5f))
                    lineTo(x(14.5f), y(6.5f))
                    lineTo(x(19f), y(11f))
                    lineTo(x(14.5f), y(15.5f))
                    lineTo(x(7.5f), y(15.5f))
                    close()
                }
                drawPath(color = color, path = eyePath, style = iconStroke)
                rect(9f, 9f, 13f, 13f, thinStrokeWidth)
                line(5f, 4.5f, 17f, 17.5f, heavyStrokeWidth)
            }
            DockAction.DELETE_DONE -> {
                line(5f, 6f, 17f, 6f, heavyStrokeWidth)
                line(8.5f, 3.5f, 13.5f, 3.5f, heavyStrokeWidth)
                rect(6f, 8f, 16f, 18f, strokeWidth)
                line(8.3f, 13.4f, 10.1f, 15.2f, thinStrokeWidth)
                line(10.1f, 15.2f, 14f, 10.8f, thinStrokeWidth)
            }
            DockAction.BATCH_DELETE -> {
                line(3.2f, 5.5f, 11.2f, 5.5f, thinStrokeWidth)
                line(3.2f, 11f, 11.2f, 11f, thinStrokeWidth)
                line(3.2f, 16.5f, 11.2f, 16.5f, thinStrokeWidth)
                line(13.6f, 6.5f, 20.2f, 6.5f, strokeWidth)
                line(15.5f, 4.3f, 18.3f, 4.3f, strokeWidth)
                rect(14.5f, 8.5f, 19.3f, 17.8f, thinStrokeWidth)
                line(16f, 10.2f, 16f, 16.1f, thinStrokeWidth)
                line(17.8f, 10.2f, 17.8f, 16.1f, thinStrokeWidth)
            }
            DockAction.EXPORT_MARKDOWN -> {
                rect(4f, 3.5f, 14.5f, 18.5f, strokeWidth)
                line(7f, 8f, 11.5f, 8f, thinStrokeWidth)
                line(7f, 11.5f, 11.5f, 11.5f, thinStrokeWidth)
                line(7f, 15f, 10f, 15f, thinStrokeWidth)
                line(12.5f, 13f, 19f, 6.5f, strokeWidth)
                line(14.5f, 6.5f, 19f, 6.5f, strokeWidth)
                line(19f, 6.5f, 19f, 11f, strokeWidth)
            }
        }
    }
}

@Composable
internal fun DockAction.settingsTitle(): String = when (this) {
    DockAction.SORT -> stringResource(R.string.sort)
    DockAction.DEADLINE -> stringResource(R.string.deadline_short)
    DockAction.HIDE_DONE -> stringResource(R.string.hide_done)
    DockAction.DELETE_DONE -> stringResource(R.string.clean_done)
    DockAction.BATCH_DELETE -> stringResource(R.string.quick_delete)
    DockAction.EXPORT_MARKDOWN -> stringResource(R.string.export_markdown)
}

@Composable
internal fun DockAction.settingsValue(): String = when (this) {
    DockAction.SORT -> stringResource(R.string.sort_detail)
    DockAction.DEADLINE -> stringResource(R.string.deadline_detail)
    DockAction.HIDE_DONE -> stringResource(R.string.hide_done_detail)
    DockAction.DELETE_DONE -> stringResource(R.string.clean_done_detail)
    DockAction.BATCH_DELETE -> stringResource(R.string.quick_delete_detail)
    DockAction.EXPORT_MARKDOWN -> stringResource(R.string.export_markdown_detail)
}

@Composable
private fun DockAction.contentDescription(): String = when (this) {
    DockAction.SORT -> stringResource(R.string.toggle_sort)
    DockAction.DEADLINE -> stringResource(R.string.toggle_deadline)
    DockAction.HIDE_DONE -> stringResource(R.string.toggle_done_visibility)
    DockAction.DELETE_DONE -> stringResource(R.string.clean_completed_tasks)
    DockAction.BATCH_DELETE -> stringResource(R.string.toggle_quick_delete)
    DockAction.EXPORT_MARKDOWN -> stringResource(R.string.copy_export_markdown)
}
