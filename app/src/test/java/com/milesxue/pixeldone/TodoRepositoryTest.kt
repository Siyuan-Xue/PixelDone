package com.milesxue.pixeldone

import com.milesxue.pixeldone.data.todo.TodoRepository
import com.milesxue.pixeldone.data.todo.TodoStateStore
import com.milesxue.pixeldone.domain.todo.DefaultChecklistId
import com.milesxue.pixeldone.domain.todo.ReminderCapability
import com.milesxue.pixeldone.domain.todo.TodoChecklist
import com.milesxue.pixeldone.domain.todo.TodoChecklistState
import com.milesxue.pixeldone.domain.todo.TrashChecklistId
import com.milesxue.pixeldone.domain.todo.createInitialChecklistState
import org.junit.Assert.assertEquals
import org.junit.Test

class TodoRepositoryTest {
    @Test
    fun saveTodoStateUpdatesStateFlow() {
        val initial = createInitialChecklistState(emptyList(), createdAtMillis = 1L)
        val updated = initial.copy(selectedListId = TrashChecklistId)
        val repository = TodoRepository(InMemoryTodoStateStore(initial))

        repository.saveTodoState(updated)

        assertEquals(updated, repository.state.value)
        assertEquals(updated, repository.loadTodoState())
    }

    @Test
    fun updateTodoStateReadsTransformsAndPersists() {
        val initial = createInitialChecklistState(emptyList(), createdAtMillis = 1L)
        val repository = TodoRepository(InMemoryTodoStateStore(initial))

        val updated = repository.updateTodoState { state ->
            state.copy(
                lists = state.lists + TodoChecklist("work", "WORK", emptyList(), 2L),
                selectedListId = "work",
            )
        }

        assertEquals("work", updated.selectedListId)
        assertEquals(updated, repository.state.value)
    }
}

internal class InMemoryTodoStateStore(
    initialState: TodoChecklistState,
) : TodoStateStore {
    private var state = initialState
    private val listeners = mutableListOf<() -> Unit>()

    override fun loadTodoState(nowMillis: Long): TodoChecklistState = state

    override fun saveTodoState(state: TodoChecklistState) {
        this.state = state
        listeners.forEach { it() }
    }

    override fun observeTodoState(onChange: () -> Unit): () -> Unit {
        listeners += onChange
        return { listeners -= onChange }
    }
}
