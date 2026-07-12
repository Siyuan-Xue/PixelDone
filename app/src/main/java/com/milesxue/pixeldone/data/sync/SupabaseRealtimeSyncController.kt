package com.milesxue.pixeldone.data.sync

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Realtime is an invalidation signal only. Every event is followed by the same
 * transactional cursor pull used by manual and event-triggered WorkManager sync.
 */
@OptIn(FlowPreview::class)
internal class SupabaseRealtimeSyncController(
    private val config: SupabaseConfig,
    private val authRepository: AuthSessionRepository,
    private val coordinator: SyncCoordinator,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : DefaultLifecycleObserver {
    private val foreground = MutableStateFlow(false)

    init {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        foreground.value = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        lifecycle.addObserver(this)
        scope.launch {
            combine(authRepository.session, foreground) { session, active -> session to active }
                .collect { (session, active) ->
                    subscriptionJob?.cancel()
                    subscriptionJob = null
                    if (active && session.signedIn && session.accessToken != null && session.userId != null) {
                        subscriptionJob = scope.launch { subscribe(session) }
                    }
                }
        }
    }

    private var subscriptionJob: kotlinx.coroutines.Job? = null

    override fun onStart(owner: LifecycleOwner) {
        foreground.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        foreground.value = false
    }

    private suspend fun subscribe(session: com.milesxue.pixeldone.domain.sync.AuthSession) {
        val userId = requireNotNull(session.userId)
        val token = requireNotNull(session.accessToken)
        val client = createSupabaseClient(config.normalizedBaseUrl, config.publishableKey) {
            install(Realtime) {
                disconnectOnSessionLoss = false
                requireValidSession = false
                disconnectOnNoSubscriptions = true
            }
        }
        client.realtime.setAuth(token)
        val channel = client.channel("pixeldone-$userId")
        val tables = listOf("todo_checklists", "todo_items", "user_settings", "sync_tombstones")
        val invalidations = tables.flatMap { table ->
            listOf(
                channel.postgresChangeFlow<PostgresAction.Insert>("public") {
                    this.table = table
                    filter("owner_user_id", FilterOperator.EQ, userId)
                },
                channel.postgresChangeFlow<PostgresAction.Update>("public") {
                    this.table = table
                    filter("owner_user_id", FilterOperator.EQ, userId)
                },
            )
        }
        try {
            coroutineScope {
                launch {
                    merge(*invalidations.toTypedArray())
                        .debounce(RealtimeDebounceMillis)
                        .collect { coordinator.requestSync() }
                }
                launch {
                    client.realtime.status.collect { status ->
                        if (status.name == "CONNECTED") coordinator.requestSync()
                    }
                }
                coordinator.requestSync()
                channel.subscribe(blockUntilSubscribed = true)
                awaitCancellation()
            }
        } finally {
            runCatching { client.realtime.removeChannel(channel) }
            client.realtime.disconnect()
        }
    }

    companion object {
        private const val RealtimeDebounceMillis = 400L
    }
}
