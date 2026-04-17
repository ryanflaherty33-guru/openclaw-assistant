package com.openclaw.assistant.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Singleton ring-buffer logger that captures gateway connection events
 * for display in the Diagnostics screen. Follows the same pattern as
 * [com.openclaw.assistant.service.HotwordDebugLogger].
 */
object ConnectionDebugLogger {

    private const val MAX_ENTRIES = 200

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun log(tag: String, message: String) {
        val entry = "[${timeFormat.format(Date())}] [$tag] $message"
        val current = _logs.value
        _logs.value = (current + entry).takeLast(MAX_ENTRIES)
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
