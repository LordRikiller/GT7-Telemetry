package com.gt7telemetry

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Receiver status shown in the top bar. */
data class Status(
    val listening: Boolean = false,
    val packetsPerSec: Int = 0,
    val everReceived: Boolean = false,
    val lastPacketAgeMs: Long = Long.MAX_VALUE,
) {
    /** True once packets have arrived and are still flowing. */
    val live: Boolean get() = everReceived && lastPacketAgeMs < 2000
}

/**
 * Process-wide singleton bridging the UDP service (writer) and the Compose
 * UI (reader). Kept dead simple: two StateFlows, no DI framework.
 */
object TelemetryRepository {
    private val _frame = MutableStateFlow<Frame?>(null)
    val frame: StateFlow<Frame?> = _frame.asStateFlow()

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    fun publish(frame: Frame) { _frame.value = frame }
    fun updateStatus(update: (Status) -> Status) { _status.value = update(_status.value) }
    fun setStatus(status: Status) { _status.value = status }
}
