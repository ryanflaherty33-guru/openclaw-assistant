package com.openclaw.assistant.ui.chat

import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull

internal class SpeechRequestTracker {
    private var currentRequestId = 0L

    fun nextRequest(): Long {
        currentRequestId += 1
        return currentRequestId
    }

    fun isCurrent(requestId: Long): Boolean = requestId == currentRequestId
}

internal suspend fun cancelAndAwaitSpeechReplacement(
    previousJob: Job?,
    stopPlayback: () -> Unit,
    waitTimeoutMs: Long = 500L,
) {
    stopPlayback()
    previousJob?.cancel()
    previousJob?.let { job ->
        withTimeoutOrNull(waitTimeoutMs) {
            job.join()
        }
    }
}
