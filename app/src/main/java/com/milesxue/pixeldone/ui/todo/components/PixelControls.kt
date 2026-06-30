package com.milesxue.pixeldone.ui.todo.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milesxue.pixeldone.domain.todo.ReminderRepeat
import com.milesxue.pixeldone.domain.todo.TodoPriority
import com.milesxue.pixeldone.ui.theme.ClaudeCactus
import com.milesxue.pixeldone.ui.theme.ClaudeClay
import com.milesxue.pixeldone.ui.theme.ClaudeClayInteractive
import com.milesxue.pixeldone.ui.theme.ClaudeCoral
import com.milesxue.pixeldone.ui.theme.ClaudeGray100
import com.milesxue.pixeldone.ui.theme.ClaudeGray200
import com.milesxue.pixeldone.ui.theme.ClaudeGray300
import com.milesxue.pixeldone.ui.theme.ClaudeGray600
import com.milesxue.pixeldone.ui.theme.ClaudeIvory
import com.milesxue.pixeldone.ui.theme.ClaudeIvoryMedium
import com.milesxue.pixeldone.ui.theme.ClaudeOat
import com.milesxue.pixeldone.ui.theme.ClaudeSlateDark
import com.milesxue.pixeldone.ui.theme.ClaudeSlateLight
import com.milesxue.pixeldone.ui.theme.GoogleBlue
import com.milesxue.pixeldone.ui.theme.GoogleGreen
import com.milesxue.pixeldone.ui.theme.GoogleRed
import com.milesxue.pixeldone.ui.theme.GoogleYellow
import com.milesxue.pixeldone.ui.theme.PixelError
import kotlin.math.roundToInt

/**
 * PixelDone 的基础 UI 组件。
 *
 * 教学说明：这些组件只处理“如何画出来、如何响应点击态”，不读写数据、不触发提醒、不访问网络。
 * 这样屏幕层可以专注组合业务流程，组件层则保持可复用、可预览、容易替换。
 */

@Composable
internal fun PixelBatchDeleteDoneButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .border(1.dp, if (enabled) PixelError else ClaudeGray300, RectangleShape)
            .background(if (enabled) ClaudeCoral else ClaudeGray200)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PixelTrashIcon(color = if (enabled) ClaudeSlateDark else ClaudeGray600)
        PixelDoneBadge(
            enabled = enabled,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp),
        )
    }
}

@Composable
internal fun PixelSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .border(1.dp, ClaudeGray300, RectangleShape)
            .background(ClaudeIvoryMedium)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "EDIT LIST" },
        contentAlignment = Alignment.Center,
    ) {
        PixelSettingsIcon(color = ClaudeSlateDark)
    }
}

@Composable
internal fun PixelItemDeleteButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .border(1.dp, ClaudeGray300, RectangleShape)
            .background(ClaudeIvoryMedium)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PixelTrashIcon(color = PixelError)
    }
}

@Composable
internal fun PixelItemImageButton(
    hasImage: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (hasImage) ClaudeClayInteractive else ClaudeGray300
    val backgroundColor = if (hasImage) ClaudeOat else ClaudeIvoryMedium
    val iconColor = if (hasImage) ClaudeClayInteractive else ClaudeSlateLight

    Box(
        modifier = modifier
            .size(36.dp)
            .border(1.dp, borderColor, RectangleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (hasImage) "VIEW TASK IMAGE" else "ADD TASK IMAGE"
            },
        contentAlignment = Alignment.Center,
    ) {
        PixelImageIcon(color = iconColor)
    }
}

@Composable
internal fun PixelRestoreButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .border(1.dp, ClaudeClayInteractive, RectangleShape)
            .background(ClaudeOat)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "RESTORE TASK" },
        contentAlignment = Alignment.Center,
    ) {
        PixelRestoreIcon(color = ClaudeClayInteractive)
    }
}

@Composable
private fun PixelSettingsIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val strokeWidth = 1.8.dp.toPx()
        drawLine(
            color = color,
            start = Offset(3.dp.toPx(), 5.dp.toPx()),
            end = Offset(15.dp.toPx(), 5.dp.toPx()),
            strokeWidth = strokeWidth,
        )
        drawRect(
            color = color,
            topLeft = Offset(6.dp.toPx(), 3.dp.toPx()),
            size = Size(3.dp.toPx(), 4.dp.toPx()),
            style = Stroke(width = strokeWidth),
        )
        drawLine(
            color = color,
            start = Offset(3.dp.toPx(), 13.dp.toPx()),
            end = Offset(15.dp.toPx(), 13.dp.toPx()),
            strokeWidth = strokeWidth,
        )
        drawRect(
            color = color,
            topLeft = Offset(10.dp.toPx(), 11.dp.toPx()),
            size = Size(3.dp.toPx(), 4.dp.toPx()),
            style = Stroke(width = strokeWidth),
        )
    }
}

@Composable
internal fun PixelAlarmIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(14.dp)) {
        val strokeWidth = 1.5.dp.toPx()
        drawCircle(
            color = color,
            radius = 4.3.dp.toPx(),
            center = Offset(7.dp.toPx(), 7.5.dp.toPx()),
            style = Stroke(width = strokeWidth),
        )
        drawLine(
            color = color,
            start = Offset(2.8.dp.toPx(), 3.2.dp.toPx()),
            end = Offset(5.dp.toPx(), 1.4.dp.toPx()),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(9.dp.toPx(), 1.4.dp.toPx()),
            end = Offset(11.2.dp.toPx(), 3.2.dp.toPx()),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(7.dp.toPx(), 7.5.dp.toPx()),
            end = Offset(7.dp.toPx(), 4.9.dp.toPx()),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(7.dp.toPx(), 7.5.dp.toPx()),
            end = Offset(9.4.dp.toPx(), 8.8.dp.toPx()),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(4.6.dp.toPx(), 12.3.dp.toPx()),
            end = Offset(3.5.dp.toPx(), 13.2.dp.toPx()),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(9.4.dp.toPx(), 12.3.dp.toPx()),
            end = Offset(10.5.dp.toPx(), 13.2.dp.toPx()),
            strokeWidth = strokeWidth,
        )
    }
}

@Composable
private fun PixelImageIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val strokeWidth = 1.7.dp.toPx()
        drawRect(
            color = color,
            topLeft = Offset(3.dp.toPx(), 4.dp.toPx()),
            size = Size(12.dp.toPx(), 10.dp.toPx()),
            style = Stroke(width = strokeWidth),
        )
        drawCircle(
            color = color,
            radius = 1.4.dp.toPx(),
            center = Offset(11.5.dp.toPx(), 7.dp.toPx()),
        )
        drawLine(
            color = color,
            start = Offset(4.5.dp.toPx(), 13.dp.toPx()),
            end = Offset(8.dp.toPx(), 9.dp.toPx()),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(8.dp.toPx(), 9.dp.toPx()),
            end = Offset(10.dp.toPx(), 11.dp.toPx()),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(10.dp.toPx(), 11.dp.toPx()),
            end = Offset(13.8.dp.toPx(), 7.5.dp.toPx()),
            strokeWidth = strokeWidth,
        )
    }
}

@Composable
private fun PixelRestoreIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val strokeWidth = 1.8.dp.toPx()
        drawLine(
            color = color,
            start = Offset(4.dp.toPx(), 9.dp.toPx()),
            end = Offset(14.dp.toPx(), 9.dp.toPx()),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(4.dp.toPx(), 9.dp.toPx()),
            end = Offset(8.dp.toPx(), 5.dp.toPx()),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(4.dp.toPx(), 9.dp.toPx()),
            end = Offset(8.dp.toPx(), 13.dp.toPx()),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(14.dp.toPx(), 6.dp.toPx()),
            end = Offset(14.dp.toPx(), 12.dp.toPx()),
            strokeWidth = strokeWidth,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FloatingNewTaskButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .size(56.dp)
            .background(if (pressed) ClaudeClayInteractive else ClaudeClay, RectangleShape)
            .border(2.dp, ClaudeSlateDark, RectangleShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .semantics { contentDescription = "NEW TASK OR LIST" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+",
            color = ClaudeSlateDark,
            fontFamily = FontFamily.Monospace,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun PixelDoneBadge(enabled: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(14.dp)
            .border(1.dp, if (enabled) ClaudeSlateDark else ClaudeGray300, RectangleShape)
            .background(if (enabled) ClaudeCactus else ClaudeGray200),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(8.dp)) {
            val strokeWidth = 1.5.dp.toPx()
            drawLine(
                color = if (enabled) ClaudeSlateDark else ClaudeGray600,
                start = Offset(1.dp.toPx(), 4.dp.toPx()),
                end = Offset(3.dp.toPx(), 6.dp.toPx()),
                strokeWidth = strokeWidth,
            )
            drawLine(
                color = if (enabled) ClaudeSlateDark else ClaudeGray600,
                start = Offset(3.dp.toPx(), 6.dp.toPx()),
                end = Offset(7.dp.toPx(), 1.dp.toPx()),
                strokeWidth = strokeWidth,
            )
        }
    }
}

@Composable
private fun PixelTrashIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val strokeWidth = 2.dp.toPx()
        val thinStrokeWidth = 1.5.dp.toPx()
        drawLine(
            color = color,
            start = Offset(4.dp.toPx(), 5.dp.toPx()),
            end = Offset(14.dp.toPx(), 5.dp.toPx()),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(7.dp.toPx(), 3.dp.toPx()),
            end = Offset(11.dp.toPx(), 3.dp.toPx()),
            strokeWidth = strokeWidth,
        )
        drawRect(
            color = color,
            topLeft = Offset(5.dp.toPx(), 7.dp.toPx()),
            size = Size(8.dp.toPx(), 9.dp.toPx()),
            style = Stroke(width = strokeWidth),
        )
        drawLine(
            color = color,
            start = Offset(8.dp.toPx(), 9.dp.toPx()),
            end = Offset(8.dp.toPx(), 14.dp.toPx()),
            strokeWidth = thinStrokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(11.dp.toPx(), 9.dp.toPx()),
            end = Offset(11.dp.toPx(), 14.dp.toPx()),
            strokeWidth = thinStrokeWidth,
        )
    }
}

@Composable
internal fun PixelPanel(
    modifier: Modifier = Modifier,
    color: Color = ClaudeGray100,
    borderColor: Color = ClaudeGray600,
    borderWidth: Dp = 2.dp,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = color,
        shape = RectangleShape,
        border = BorderStroke(borderWidth, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

@Composable
internal fun <T> PixelSegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Button(
                onClick = { onSelected(option) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp),
                shape = RectangleShape,
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) ClaudeClayInteractive else ClaudeGray300,
                ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) ClaudeOat else ClaudeIvoryMedium,
                    contentColor = ClaudeSlateDark,
                ),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val PrioritySliderValues = listOf(
    TodoPriority.LOW,
    TodoPriority.MEDIUM,
    TodoPriority.HIGH,
    TodoPriority.XHIGH,
)

@Composable
internal fun PrioritySlider(
    selected: TodoPriority,
    onSelected: (TodoPriority) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = PrioritySliderValues.indexOf(selected)
        .takeIf { it >= 0 }
        ?: PrioritySliderValues.indexOf(TodoPriority.MEDIUM)
    val selectedColor = selected.priorityColor()

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = selectedIndex.toFloat(),
            onValueChange = { value ->
                val priority = PrioritySliderValues[
                    value.roundToInt().coerceIn(0, PrioritySliderValues.lastIndex)
                ]
                if (priority != selected) {
                    onSelected(priority)
                }
            },
            valueRange = 0f..PrioritySliderValues.lastIndex.toFloat(),
            steps = PrioritySliderValues.size - 2,
            colors = SliderDefaults.colors(
                thumbColor = selectedColor,
                activeTrackColor = selectedColor,
                inactiveTrackColor = ClaudeGray300,
                activeTickColor = ClaudeIvory,
                inactiveTickColor = selectedColor,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PrioritySliderValues.forEach { priority ->
                Text(
                    text = priority.uiLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (priority == selected) ClaudeSlateDark else ClaudeSlateLight,
                    fontWeight = if (priority == selected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    destructive: Boolean = false,
    selected: Boolean = false,
) {
    val containerColor = when {
        destructive -> ClaudeCoral
        primary -> ClaudeClay
        selected -> ClaudeOat
        else -> ClaudeIvoryMedium
    }
    val borderColor = when {
        destructive -> PixelError
        primary -> ClaudeClayInteractive
        selected -> ClaudeClayInteractive
        else -> ClaudeGray300
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RectangleShape,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (enabled) borderColor else ClaudeGray300),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = ClaudeSlateDark,
            disabledContainerColor = ClaudeGray200,
            disabledContentColor = ClaudeGray600,
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun TodoPriority.uiLabel(): String {
    return when (this) {
        TodoPriority.XHIGH -> "XHIGH"
        TodoPriority.HIGH -> "HIGH"
        TodoPriority.MEDIUM -> "MID"
        TodoPriority.LOW -> "LOW"
    }
}

internal fun TodoPriority.priorityColor(): Color {
    return when (this) {
        TodoPriority.XHIGH -> GoogleRed
        TodoPriority.HIGH -> GoogleYellow
        TodoPriority.MEDIUM -> GoogleBlue
        TodoPriority.LOW -> GoogleGreen
    }
}

internal fun ReminderRepeat.uiLabel(): String {
    return when (this) {
        ReminderRepeat.NONE -> "NONE"
        ReminderRepeat.DAILY -> "DAILY"
        ReminderRepeat.WEEKLY -> "WEEKLY"
    }
}
