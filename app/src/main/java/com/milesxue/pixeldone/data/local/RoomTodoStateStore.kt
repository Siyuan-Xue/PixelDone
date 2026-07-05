package com.milesxue.pixeldone.data.local

import android.content.Context
import androidx.room.Room
import com.milesxue.pixeldone.data.todo.TodoPreferences
import com.milesxue.pixeldone.data.todo.TodoStateStore
import com.milesxue.pixeldone.domain.todo.TodoChecklistState
import com.milesxue.pixeldone.domain.todo.createInitialChecklistState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class RoomTodoStateStore private constructor(
    private val database: PixelDoneDatabase,
    private val legacyPreferences: TodoPreferences,
) : TodoStateStore {
    private val dao = database.todoDao()
    private val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun loadTodoState(nowMillis: Long): TodoChecklistState = runBlocking(Dispatchers.IO) {
        ensureMigrated(nowMillis)
        loadStateFromRoom(nowMillis) ?: createInitialChecklistState(emptyList(), nowMillis).also { state ->
            dao.replaceState(state.toTodoEntitySet(nowMillis))
        }
    }

    override fun saveTodoState(state: TodoChecklistState) {
        val nowMillis = System.currentTimeMillis()
        runBlocking(Dispatchers.IO) {
            ensureMigrated(nowMillis)
            val previous = dao.getEntitySet()
            dao.replaceState(state.toTodoEntitySet(nowMillis, previousEntitySet = previous))
        }
    }

    override fun observeTodoState(onChange: () -> Unit): () -> Unit {
        runBlocking(Dispatchers.IO) { ensureMigrated(System.currentTimeMillis()) }
        val job = observerScope.launch {
            combine(
                dao.observeChecklists(),
                dao.observeItems(),
                dao.observeMetadata(),
            ) { _, _, _ -> Unit }
                .drop(1)
                .collect { onChange() }
        }
        return { job.cancel() }
    }

    suspend fun loadEntitySetForSync(nowMillis: Long): TodoEntitySet {
        ensureMigrated(nowMillis)
        return dao.getEntitySet() ?: createInitialChecklistState(emptyList(), nowMillis)
            .toTodoEntitySet(nowMillis)
            .also { dao.replaceState(it) }
    }

    suspend fun replaceEntitySetFromSync(entitySet: TodoEntitySet) {
        dao.replaceState(entitySet)
    }

    private suspend fun ensureMigrated(nowMillis: Long) {
        if (dao.metadataCount() > 0) return
        val legacyState = legacyPreferences.loadTodoState(nowMillis)
        dao.replaceState(legacyState.toTodoEntitySet(nowMillis))
    }

    private suspend fun loadStateFromRoom(nowMillis: Long): TodoChecklistState? {
        return todoEntitiesToState(
            metadata = dao.getMetadata(),
            checklists = dao.getChecklists(),
            items = dao.getItems(),
            fallbackCreatedAtMillis = nowMillis,
        )
    }

    companion object {
        private const val DatabaseName = "pixel_done_local.db"

        fun create(context: Context, legacyPreferences: TodoPreferences): RoomTodoStateStore {
            val database = Room.databaseBuilder(
                context.applicationContext,
                PixelDoneDatabase::class.java,
                DatabaseName,
            ).build()
            return RoomTodoStateStore(database, legacyPreferences)
        }
    }
}