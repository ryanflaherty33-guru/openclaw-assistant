package com.openclaw.assistant.ui.chat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechPlaybackTransitionsTest {
    @Test
    fun requestTrackerInvalidatesSupersededRequests() {
        val tracker = SpeechRequestTracker()

        val first = tracker.nextRequest()
        val second = tracker.nextRequest()
        val stopped = tracker.nextRequest()

        assertFalse(tracker.isCurrent(first))
        assertFalse(tracker.isCurrent(second))
        assertTrue(tracker.isCurrent(stopped))
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun cancelAndAwaitSpeechReplacementWaitsForPreviousCleanup() = runTest {
        val cleanupFinished = CompletableDeferred<Unit>()
        val previousJob = launch {
            try {
                awaitCancellation()
            } finally {
                cleanupFinished.complete(Unit)
            }
        }
        var stopped = false
        runCurrent()

        cancelAndAwaitSpeechReplacement(
            previousJob = previousJob,
            stopPlayback = { stopped = true },
            waitTimeoutMs = 1_000L,
        )

        assertTrue(stopped)
        assertTrue(previousJob.isCancelled)
        assertTrue(cleanupFinished.isCompleted)
    }
}
