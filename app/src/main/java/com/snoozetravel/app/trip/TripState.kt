package com.snoozetravel.app.trip

import com.snoozetravel.app.data.Trigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface TripStatus {
    data object Idle : TripStatus

    data class Active(
        val destinationId: String,
        val destinationName: String,
        val trigger: Trigger,
        val distanceM: Double? = null,
        val etaSec: Double? = null,
        val progress: Float = 0f,
    ) : TripStatus

    data class Alarming(val destinationId: String, val destinationName: String) : TripStatus
}

/** Estado en memoria compartido por servicio, UI y widget. */
object TripState {
    private val _status = MutableStateFlow<TripStatus>(TripStatus.Idle)
    val status: StateFlow<TripStatus> = _status.asStateFlow()

    fun set(s: TripStatus) {
        _status.value = s
    }
}
