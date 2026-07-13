package com.milesxue.pixeldone.data.local

import com.milesxue.pixeldone.data.sync.SyncMetadataLocalStore
import com.milesxue.pixeldone.data.sync.TodoSyncDomainStore
import com.milesxue.pixeldone.data.sync.TodoSyncLocalStore

internal class CompositeTodoSyncLocalStore(
    domainStore: TodoSyncDomainStore,
    metadataStore: SyncMetadataLocalStore,
) : TodoSyncLocalStore,
    TodoSyncDomainStore by domainStore,
    SyncMetadataLocalStore by metadataStore
