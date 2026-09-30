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
        val destLat: Double,
        val destLon: Double,
        val myLat: Double? = null,
        val myLon: Double? = null,
        val distanceM: Double? = null,
        val etaSec: Double? = null,
        val progress: Float = 0f,
    ) : TripStatus {
        /** Velocidad real de acercamiento (m/s), si ya se conoce. */
        val speedMs: Double? get() = if (distanceM != null && etaSec != null && etaSec > 0) distanceM / etaSec else null
    }

    /** [again] = la alarma volvió a sonar porque el bus pasó el destino. */
    data class Alarming(
        val destinationId: String,
        val destinationName: String,
        val again: Boolean = false,
    ) : TripStatus

    /** Alarma apagada; se vigila que realmente te bajes. */
    data class Guarding(
        val destinationId: String,
        val destinationName: String,
        val destLat: Double,
        val destLon: Double,
        val myLat: Double? = null,
        val myLon: Double? = null,
        val distanceM: Double? = null,
    ) : TripStatus
}

/** Estado en memoria compartido por servicio, UI y widget. */
object TripState {
    private val _status = MutableStateFlow<TripStatus>(TripStatus.Idle)
    val status: StateFlow<TripStatus> = _status.asStateFlow()

    fun set(s: TripStatus) {
        _status.value = s
    }
}
