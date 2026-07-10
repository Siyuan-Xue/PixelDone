package com.milesxue.pixeldone

import com.milesxue.pixeldone.data.sync.LocalOnlyAuthSessionRepository
import com.milesxue.pixeldone.data.sync.LocalOnlySyncCoordinator
import com.milesxue.pixeldone.domain.sync.SyncCoordinatorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LocalOnlySyncTest {
    @Test
    fun localOnlyAuthSessionIsSignedOut() {
        val repository = LocalOnlyAuthSessionRepository()
        val session = repository.session.value

        assertFalse(session.signedIn)
        assertNull(session.userId)
        assertEquals("LOCAL ONLY", session.displayLabel)
    }

    @Test
    fun localOnlySyncCoordinatorDoesNotStartNetworkSync() {
        val coordinator = LocalOnlySyncCoordinator()

        assertEquals(SyncCoordinatorStatus.LOCAL_ONLY, coordinator.status.value)
    }

}
