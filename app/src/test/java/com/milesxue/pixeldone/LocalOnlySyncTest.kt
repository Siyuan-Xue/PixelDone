package com.milesxue.pixeldone

import com.milesxue.pixeldone.data.sync.LocalOnlyAuthSessionRepository
import com.milesxue.pixeldone.data.sync.LocalOnlySyncCoordinator
import com.milesxue.pixeldone.domain.sync.ConflictResolutionSource
import com.milesxue.pixeldone.domain.sync.ConflictResolver
import com.milesxue.pixeldone.domain.sync.SyncCoordinatorStatus
import com.milesxue.pixeldone.domain.sync.SyncMergeCandidate
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

    @Test
    fun conflictResolverUsesLastWriteWinsAndKeepsLocalOnTie() {
        val local = SyncMergeCandidate(value = "local", updatedAtMillis = 20L)
        val remote = SyncMergeCandidate(value = "remote", updatedAtMillis = 10L)
        val localResult = ConflictResolver.resolveLastWriteWins(local, remote)
        val tieResult = ConflictResolver.resolveLastWriteWins(
            local = local,
            remote = remote.copy(value = "remote-tie", updatedAtMillis = 20L),
        )

        assertEquals(ConflictResolutionSource.LOCAL, localResult.source)
        assertEquals("local", localResult.value)
        assertEquals(ConflictResolutionSource.LOCAL, tieResult.source)
        assertEquals("local", tieResult.value)
    }

    @Test
    fun conflictResolverTreatsDeleteTimestampAsRecordClock() {
        val local = SyncMergeCandidate(value = "local-active", updatedAtMillis = 30L)
        val remote = SyncMergeCandidate(value = "remote-deleted", updatedAtMillis = 10L, deletedAtMillis = 40L)

        val result = ConflictResolver.resolveLastWriteWins(local, remote)

        assertEquals(ConflictResolutionSource.REMOTE, result.source)
        assertEquals("remote-deleted", result.value)
        assertEquals(40L, result.deletedAtMillis)
    }
}
