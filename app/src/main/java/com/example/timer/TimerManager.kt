package com.example.timer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TimerStatus {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED
}

object TimerManager {

    private val _status = MutableStateFlow(TimerStatus.IDLE)
    val status: StateFlow<TimerStatus> = _status.asStateFlow()

    private val _totalSeconds = MutableStateFlow(300L) // default 5 minutes
    val totalSeconds: StateFlow<Long> = _totalSeconds.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(300L)
    val remainingSeconds: StateFlow<Long> = _remainingSeconds.asStateFlow()

    fun updateStatus(newStatus: TimerStatus) {
        _status.value = newStatus
    }

    fun setDuration(hours: Int, minutes: Int, seconds: Int) {
        val total = (hours * 3600L) + (minutes * 60L) + seconds
        if (total > 0) {
            _totalSeconds.value = total
            _remainingSeconds.value = total
            _status.value = TimerStatus.IDLE
        }
    }

    fun updateRemaining(seconds: Long) {
        _remainingSeconds.value = seconds
    }

    fun getFormattedRemaining(): String {
        val s = _remainingSeconds.value
        val hours = s / 3600
        val minutes = (s % 3600) / 60
        val secs = s % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }

    fun getProgress(): Float {
        val total = _totalSeconds.value
        val rem = _remainingSeconds.value
        if (total <= 0) return 1f
        return (rem.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }
}
