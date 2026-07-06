package com.milesxue.pixeldone.data.local

import android.content.Context
import androidx.room.Room
import com.milesxue.pixeldone.data.sync.TodoSyncLocalStore
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomTodoStateStore private constructor(
    private val database: PixelDoneDatabase,
    private val legacyPreferences: TodoPreferences,
) : TodoStateStore, TodoSyncLocalStore {
    private val dao = database.todoDao()
    private val writeMutex = Mutex()
    private val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun loadTodoState(nowMillis: Long): TodoChecklistState = runBlocking(Dispatchers.IO) {
        writeMutex.withLock {
            ensureMigrated(nowMillis)
            loadStateFromRoom(nowMillis) ?: createInitialChecklistState(emptyList(), nowMillis).also { state ->
                dao.replaceState(state.toTodoEntitySet(nowMillis))
            }
        }
    }

    override fun saveTodoState(state: TodoChecklistState) {
        val nowMillis = System.currentTimeMillis()
        runBlocking(Dispatchers.IO) {
            writeMutex.withLock {
                ensureMigrated(nowMillis)
                val previous = dao.getEntitySet()
                dao.replaceState(state.toTodoEntitySet(nowMillis, previousEntitySet = previous))
            }
        }
    }

    override fun observeTodoState(onChange: () -> Unit): () -> Unit {
        runBlocking(Dispatchers.IO) {
            writeMutex.withLock { ensureMigrated(System.currentTimeMillis()) }
        }
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

    override suspend fun loadEntitySetForSync(nowMillis: Long): TodoEntitySet = writeMutex.withLock {
        ensureMigrated(nowMillis)
        dao.getEntitySet() ?: createInitialChecklistState(emptyList(), nowMillis)
            .toTodoEntitySet(nowMillis)
            .also { dao.replaceState(it) }
    }

    override suspend fun replaceEntitySetFromSync(entitySet: TodoEntitySet) {
        writeMutex.withLock { dao.replaceState(entitySet) }
    }

    override suspend fun updateEntitySetFromSync(
        nowMillis: Long,
        transform: (TodoEntitySet) -> TodoEntitySet,
    ): TodoEntitySet = writeMutex.withLock {
        ensureMigrated(nowMillis)
        val current = dao.getEntitySet() ?: createInitialChecklistState(emptyList(), nowMillis)
            .toTodoEntitySet(nowMillis)
        val updated = transform(current)
        dao.replaceState(updated)
        updated
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
            ).addMigrations(PixelDoneMigrations.Migration1To2)
                .build()
            return RoomTodoStateStore(database, legacyPreferences)
        }
    }
}