package com.milesxue.pixeldone

import com.milesxue.pixeldone.data.sync.shouldRetryBackgroundSync
import com.milesxue.pixeldone.domain.sync.SyncCoordinatorStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncWorkPolicyTest {
    @Test
    fun signedOutSessionStopsBackgroundRetry() {
        assertFalse(SyncCoordinatorStatus.SIGNED_OUT.shouldRetryBackgroundSync())
    }

    @Test
    fun transientFailuresStillRetry() {
        assertTrue(SyncCoordinatorStatus.NETWORK_ERROR.shouldRetryBackgroundSync())
        assertTrue(SyncCoordinatorStatus.ERROR.shouldRetryBackgroundSync())
    }
}
