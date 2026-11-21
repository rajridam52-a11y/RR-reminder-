package com.example.stopwatch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

data class LapItem(
    val lapNumber: Int,
    val lapTimeMillis: Long,
    val totalTimeMillis: Long,
    val formattedLapTime: String,
    val formattedTotalTime: String
)

enum class StopwatchStatus {
    IDLE,
    RUNNING,
    PAUSED
}

object StopwatchManager {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var job: Job? = null

    private var startTimeMillis: Long = 0L
    private var accumulatedMillis: Long = 0L
    private var lastLapTotalMillis: Long = 0L

    private val _status = MutableStateFlow(StopwatchStatus.IDLE)
    val status: StateFlow<StopwatchStatus> = _status.asStateFlow()

    private val _elapsedMillis = MutableStateFlow(0L)
    val elapsedMillis: StateFlow<Long> = _elapsedMillis.asStateFlow()

    private val _laps = MutableStateFlow<List<LapItem>>(emptyList())
    val laps: StateFlow<List<LapItem>> = _laps.asStateFlow()

    fun start() {
        if (_status.value == StopwatchStatus.RUNNING) return

        startTimeMillis = System.currentTimeMillis()
        _status.value = StopwatchStatus.RUNNING

        job = scope.launch {
            while (isActive && _status.value == StopwatchStatus.RUNNING) {
                val current = System.currentTimeMillis()
                val total = accumulatedMillis + (current - startTimeMillis)
                _elapsedMillis.value = total
                delay(16) // ~60fps smooth updates
            }
        }
    }

    fun pause() {
        if (_status.value != StopwatchStatus.RUNNING) return

        val current = System.currentTimeMillis()
        accumulatedMillis += (current - startTimeMillis)
        _elapsedMillis.value = accumulatedMillis
        _status.value = StopwatchStatus.PAUSED
        job?.cancel()
        job = null
    }

    fun reset() {
        job?.cancel()
        job = null
        startTimeMillis = 0L
        accumulatedMillis = 0L
        lastLapTotalMillis = 0L
        _elapsedMillis.value = 0L
        _laps.value = emptyList()
        _status.value = StopwatchStatus.IDLE
    }

    fun recordLap(): LapItem? {
        val total = _elapsedMillis.value
        if (total <= 0) return null

        val lapMillis = total - lastLapTotalMillis
        lastLapTotalMillis = total

        val lapNumber = _laps.value.size + 1
        val item = LapItem(
            lapNumber = lapNumber,
            lapTimeMillis = lapMillis,
            totalTimeMillis = total,
            formattedLapTime = formatTime(lapMillis),
            formattedTotalTime = formatTime(total)
        )

        _laps.value = listOf(item) + _laps.value
        return item
    }

    fun formatTime(millis: Long): String {
        val hundredths = (millis % 1000) / 10
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = millis / (1000 * 60 * 60)

        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d.%02d", hours, minutes, seconds, hundredths)
        } else {
            String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, hundredths)
        }
    }
}
