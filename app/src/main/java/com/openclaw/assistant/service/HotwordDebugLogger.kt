package com.openclaw.assistant.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Singleton that collects wake word debug log entries for display on the home screen.
 * Only active when wakeWordDebugEnabled is true.
 */
object HotwordDebugLogger {

    private const val MAX_ENTRIES = 60

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun log(message: String) {
        val entry = "[${timeFormat.format(Date())}] $message"
        val current = _logs.value
        _logs.value = (current + entry).takeLast(MAX_ENTRIES)
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
