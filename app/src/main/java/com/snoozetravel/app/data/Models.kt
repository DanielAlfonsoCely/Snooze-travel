package com.snoozetravel.app.data

import java.util.Locale
import kotlin.math.roundToInt

/** Cómo se decide cuándo despertar: a cierta distancia o cierto tiempo antes de llegar. */
enum class TriggerMode { DISTANCE, TIME }

/** [value] está en km (DISTANCE) o minutos (TIME). */
data class Trigger(val mode: TriggerMode, val value: Double) {
    val summary: String
        get() = when (mode) {
            TriggerMode.DISTANCE -> "A ${formatKm(value)}"
            TriggerMode.TIME -> "${value.roundToInt()} min antes"
        }

    companion object {
        val DEFAULT = Trigger(TriggerMode.TIME, 10.0)
        val DISTANCE_RANGE = 0.5f..20f
        val TIME_RANGE = 1f..30f
    }
}

data class Destination(
    val id: String,
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val trigger: Trigger,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Patrones de vibración. Amplitud 255 = máxima intensidad del motor. */
enum class VibePattern(val label: String, val timings: LongArray, val amplitudes: IntArray) {
    CONTINUOUS(
        "Continua intensa",
        longArrayOf(0, 2000, 150),
        intArrayOf(0, 255, 0),
    ),
    PULSES(
        "Pulsos rápidos",
        longArrayOf(0, 250, 120),
        intArrayOf(0, 255, 0),
    ),
    HEARTBEAT(
        "Latido",
        longArrayOf(0, 150, 100, 450, 600),
        intArrayOf(0, 255, 0, 255, 0),
    ),
    HAMMER(
        "Martillo",
        longArrayOf(0, 600, 120, 600, 120, 1400, 350),
        intArrayOf(0, 255, 0, 255, 0, 255, 0),
    ),
    SOS(
        "SOS",
        longArrayOf(0, 150, 120, 150, 120, 150, 300, 500, 150, 500, 150, 500, 300, 150, 120, 150, 120, 150, 900),
        intArrayOf(0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0),
    ),
}

data class AlarmSettings(
    val pattern: VibePattern = VibePattern.CONTINUOUS,
    val soundEnabled: Boolean = true,
    /** Segundos solo vibrando antes de empezar a sonar (alarma escalonada). */
    val soundDelaySec: Int = 20,
    val ringtoneUri: String? = null,
    val ringtoneTitle: String? = null,
    val rampVolume: Boolean = true,
    val maxVolume: Boolean = true,
    val preAlertEnabled: Boolean = false,
    val preOffsetKm: Double = 3.0,
    val preOffsetMin: Double = 5.0,
)

fun formatKm(km: Double): String = when {
    km < 1.0 -> "${(km * 1000).roundToInt()} m"
    km % 1.0 == 0.0 -> "${km.toInt()} km"
    else -> String.format(Locale.US, "%.1f km", km)
}

fun formatDistance(meters: Double): String = when {
    meters < 1000 -> "${(meters / 10).roundToInt() * 10} m"
    meters < 10_000 -> String.format(Locale.US, "%.1f km", meters / 1000)
    else -> "${(meters / 1000).roundToInt()} km"
}

fun formatEta(seconds: Double): String {
    val min = (seconds / 60).roundToInt().coerceAtLeast(1)
    return if (min < 60) "~$min min" else "~${min / 60} h ${min % 60} min"
}
