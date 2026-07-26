package com.milesxue.pixeldone.widget

import android.app.Application
import android.os.Build
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.testing.unit.assertIsChecked
import androidx.glance.appwidget.testing.unit.assertIsNotChecked
import androidx.glance.appwidget.testing.unit.isChecked
import androidx.glance.appwidget.testing.unit.isNotChecked
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.assertHasText
import androidx.glance.testing.unit.MappedNode
import androidx.glance.text.Text
import com.milesxue.pixeldone.domain.todo.ReminderRepeat
import com.milesxue.pixeldone.domain.todo.TodoChecklist
import com.milesxue.pixeldone.domain.todo.TodoChecklistState
import com.milesxue.pixeldone.domain.todo.TodoItem
import com.milesxue.pixeldone.domain.todo.TodoPriority
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(
    application = Application::class,
    sdk = [Build.VERSION_CODES.BAKLAVA],
)
class PixelDoneWidgetGlanceTest {
    @Test
    fun todoRowIsUncheckedCheckboxWithExactCompletionAction() =
        runGlanceAppWidgetUnitTest {
            val checklist = checklist("paper", listOf(todo("task")))
            val item = checklist.items.single()
            provideComposable {
                WidgetTodoRow(checklist = checklist, item = item)
            }

            val parameters = widgetCompletionParameters(
                checklistId = "paper",
                todoId = "task",
            )
            onNode(isNotChecked())
                .assertIsNotChecked()
                .assertHasText("task")
                .assert(hasCompoundCompletionAction(parameters))
        }

    @Test
    fun completingTodoRowIsCheckedStruckThroughAndNotInteractive() =
        runGlanceAppWidgetUnitTest {
            val checklist = checklist("paper", listOf(todo("task")))
            provideComposable {
                WidgetTodoRow(
                    checklist = checklist,
                    item = checklist.items.single(),
                    completing = true,
                )
            }

            onNode(isChecked())
                .assertIsChecked()
                .assertHasText("task")
                .assert(hasNoActionOrBackground())
                .assert(hasLineThroughText())
        }

    @Test
    fun lazyListAcceptsTodoIdsThatPreviouslyFellInsideGlanceReservedRange() =
        runGlanceAppWidgetUnitTest {
            val checklist = checklist("main", listOf(todo("todo-a")))
            provideComposable {
                WidgetTodoList(
                    checklist = checklist,
                    todos = checklist.items,
                )
            }

            awaitIdle()
            onNode(isNotChecked())
                .assertIsNotChecked()
                .assertHasText("todo-a")
        }

    @Test
    fun repositoryAndConfigurationFlowsRecomposeCurrentChecklist() =
        runGlanceAppWidgetUnitTest {
            val paper = checklist("paper", listOf(todo("paper-task")))
            val main = checklist("main", listOf(todo("main-task")))
            val todoState = MutableStateFlow(
                TodoChecklistState(
                    lists = listOf(paper, main),
                    selectedListId = paper.id,
                ),
            )
            val checklistId = mutableStateOf<String?>("paper")
            val observedSelection = AtomicReference<ObservedWidgetChecklist>()

            provideComposable {
                val selection = observeWidgetChecklist(
                    todoStateFlow = todoState,
                    configuredChecklistId = checklistId.value,
                )
                SideEffect {
                    observedSelection.set(selection)
                }
                Text(selection.todoTitles())
            }
            awaitIdle()
            assertEquals("paper", observedSelection.get().checklist?.id)
            assertEquals("paper-task", observedSelection.get().todoTitles())

            todoState.value = todoState.value.copy(
                lists = listOf(
                    paper.copy(
                        items = listOf(paper.items.single().copy(completed = true)),
                    ),
                    main,
                ),
            )
            awaitIdle()
            assertEquals("", observedSelection.get().todoTitles())

            checklistId.value = "main"
            awaitIdle()
            assertEquals("main", observedSelection.get().checklist?.id)
            assertEquals("main-task", observedSelection.get().todoTitles())
        }

    private fun ObservedWidgetChecklist.todoTitles(): String =
        checklist
            ?.let(::widgetTodos)
            ?.joinToString { it.title }
            .orEmpty()

    /**
     * Glance 1.1.1's built-in run-callback matcher does not unwrap the
     * CompoundButtonAction emitted by CheckBox, so this matcher verifies the same public test tree
     * through the action's generated accessors.
     */
    private fun hasCompoundCompletionAction(
        expectedParameters: ActionParameters,
    ): GlanceNodeMatcher<MappedNode> = GlanceNodeMatcher(
        "has compound completion callback with exact parameters",
    ) { node ->
        runCatching {
            var outerAction: Any? = null
            node.value.emittable.modifier.foldIn(Unit) { _, element ->
                if (element.javaClass.name == "androidx.glance.action.ActionModifier") {
                    outerAction = element.javaClass
                        .getMethod("getAction")
                        .invoke(element)
                }
            }
            val compoundAction = outerAction
                ?.takeIf {
                    it.javaClass.name ==
                        "androidx.glance.appwidget.action.CompoundButtonAction"
                }
                ?: return@runCatching false
            val innerAction = compoundAction.javaClass
                .getMethod("getInnerAction")
                .invoke(compoundAction)
            val callbackClass = innerAction.javaClass
                .getMethod("getCallbackClass")
                .invoke(innerAction)
            val parameters = innerAction.javaClass
                .getMethod("getParameters")
                .invoke(innerAction) as? ActionParameters

            callbackClass == CompleteTodoFromWidgetAction::class.java &&
                parameters?.asMap() == expectedParameters.asMap()
        }.getOrDefault(false)
    }

    private fun hasNoActionOrBackground(): GlanceNodeMatcher<MappedNode> = GlanceNodeMatcher(
        "has no completion action or nested background",
    ) { node ->
        var hasAction = false
        var hasBackground = false
        node.value.emittable.modifier.foldIn(Unit) { _, element ->
            hasAction = hasAction ||
                element.javaClass.name == "androidx.glance.action.ActionModifier"
            hasBackground = hasBackground ||
                element.javaClass.name.startsWith("androidx.glance.BackgroundModifier")
        }
        !hasAction && !hasBackground
    }

    private fun hasLineThroughText(): GlanceNodeMatcher<MappedNode> = GlanceNodeMatcher(
        "has line-through text decoration",
    ) { node ->
        runCatching {
            node.value.emittable.javaClass
                .getMethod("getStyle")
                .invoke(node.value.emittable)
                ?.toString()
                ?.contains("LineThrough") == true
        }.getOrDefault(false)
    }

    private fun checklist(id: String, items: List<TodoItem>) = TodoChecklist(
        id = id,
        name = id,
        items = items,
        createdAtMillis = 0L,
    )

    private fun todo(id: String) = TodoItem(
        id = id,
        title = id,
        priority = TodoPriority.MEDIUM,
        dueAtMillis = 1L,
        completed = false,
        createdAtMillis = 0L,
        reminderRepeat = ReminderRepeat.NONE,
    )
}
