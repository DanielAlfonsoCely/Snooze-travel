package com.snoozetravel.app.trip

import com.snoozetravel.app.data.Trigger
import com.snoozetravel.app.data.TriggerMode
import com.snoozetravel.app.util.haversine
import kotlin.math.max
import kotlin.math.min

/**
 * Lógica pura del viaje (sin dependencias de Android).
 *
 * - Calcula distancia al destino y un ETA basado en la velocidad real a la que el bus
 *   se acerca al destino (aprendida durante el viaje; funciona sin internet).
 * - Decide si disparar el aviso previo / la alarma.
 * - Decide cada cuánto pedir la siguiente ubicación: lejos = poco frecuente y con
 *   baja precisión (casi no gasta batería); cerca = frecuente y con GPS.
 *
 * [preOffset] está en la misma unidad que el trigger (km o min); null = sin aviso previo.
 */
class TripEngine(
    private val destLat: Double,
    private val destLon: Double,
    val trigger: Trigger,
    private val preOffset: Double?,
) {
    data class Step(
        val distanceM: Double,
        val etaSec: Double?,
        val progress: Float,
        val firePre: Boolean,
        val fireMain: Boolean,
        val nextIntervalMs: Long,
        val highAccuracy: Boolean,
    )

    private data class Sample(val tMs: Long, val distanceM: Double)

    private val samples = ArrayDeque<Sample>()
    private var startDistance: Double? = null
    private var lastFixMs = Long.MIN_VALUE

    /** Velocidad de acercamiento suavizada (m/s). Se mantiene durante paradas del bus. */
    var approachSpeed: Double? = null
        private set
    var preDone = preOffset == null
        private set
    var mainDone = false
        private set

    fun update(lat: Double, lon: Double, accuracyM: Float, nowMs: Long): Step? {
        if (nowMs <= lastFixMs) return null // fix viejo o repetido
        lastFixMs = nowMs

        val d = haversine(lat, lon, destLat, destLon)
        // Restamos parte del error del GPS: preferimos avisar un poco antes que tarde.
        val eff = max(0.0, d - min(accuracyM.toDouble(), MAX_ACCURACY_MARGIN_M))
        updateSpeed(d, nowMs)
        if (startDistance == null) startDistance = d

        var fireMain = false
        var firePre = false
        if (!mainDone && reached(eff, trigger.value)) {
            fireMain = true
            mainDone = true
            preDone = true
        } else if (!preDone && reached(eff, trigger.value + preOffset!!)) {
            firePre = true
            preDone = true
        }

        // Brecha (m) hasta el umbral pendiente más cercano.
        val pending = buildList {
            if (!mainDone) add(trigger.value)
            if (!preDone) add(trigger.value + preOffset!!)
        }
        val gap = pending.minOfOrNull { eff - thresholdMeters(it) } ?: 0.0
        // Pedimos la siguiente ubicación antes de que, incluso a velocidad máxima,
        // podamos haber cruzado la mitad de la brecha.
        val rawMs = gap / MAX_BUS_SPEED_MS * 0.5 * 1000
        val interval = INTERVAL_BUCKETS_MS.lastOrNull { it <= rawMs } ?: INTERVAL_BUCKETS_MS.first()

        val target = thresholdMeters(trigger.value)
        val start = startDistance ?: d
        val progress = if (start <= target) 1f else ((start - d) / (start - target)).toFloat().coerceIn(0f, 1f)

        return Step(
            distanceM = d,
            etaSec = approachSpeed?.let { d / it },
            progress = progress,
            firePre = firePre,
            fireMain = fireMain,
            nextIntervalMs = interval,
            highAccuracy = gap < HIGH_ACCURACY_GAP_M,
        )
    }

    private fun reached(effDistance: Double, value: Double): Boolean = when (trigger.mode) {
        TriggerMode.DISTANCE -> effDistance <= value * 1000
        TriggerMode.TIME -> effDistance <= TIME_MODE_SAFETY_M ||
            (approachSpeed?.let { effDistance / it <= value * 60 } ?: false)
    }

    /** Umbral equivalente en metros (para planificar con margen, en modo tiempo usa velocidad alta). */
    private fun thresholdMeters(value: Double): Double = when (trigger.mode) {
        TriggerMode.DISTANCE -> value * 1000
        // Con velocidad aprendida se planifica con +40% de margen (por si el bus acelera).
        TriggerMode.TIME -> max(
            TIME_MODE_SAFETY_M,
            (approachSpeed?.let { max(it * 1.4, 12.0) } ?: PLAN_SPEED_MS) * value * 60,
        )
    }

    private fun updateSpeed(d: Double, nowMs: Long) {
        samples.addLast(Sample(nowMs, d))
        while (samples.size > 2 && nowMs - samples.first().tMs > SPEED_WINDOW_MS) samples.removeFirst()
        if (samples.size > MAX_SAMPLES) samples.removeFirst()
        val ref = samples.firstOrNull { nowMs - it.tMs in MIN_SPAN_MS..SPEED_WINDOW_MS } ?: return
        val v = (ref.distanceM - d) / ((nowMs - ref.tMs) / 1000.0)
        if (v in MIN_MOVING_SPEED_MS..MAX_PLAUSIBLE_SPEED_MS) {
            approachSpeed = approachSpeed?.let { 0.6 * it + 0.4 * v } ?: v
        }
    }

    companion object {
        const val MAX_ACCURACY_MARGIN_M = 100.0
        const val TIME_MODE_SAFETY_M = 400.0
        const val MAX_BUS_SPEED_MS = 30.0 // 108 km/h
        const val PLAN_SPEED_MS = 25.0 // 90 km/h para planificar antes de conocer la velocidad
        const val HIGH_ACCURACY_GAP_M = 5_000.0
        const val SPEED_WINDOW_MS = 5 * 60_000L
        const val MIN_SPAN_MS = 45_000L
        const val MAX_SAMPLES = 40
        const val MIN_MOVING_SPEED_MS = 1.5
        const val MAX_PLAUSIBLE_SPEED_MS = 45.0
        val INTERVAL_BUCKETS_MS = longArrayOf(5_000, 10_000, 15_000, 30_000, 60_000, 90_000, 120_000, 180_000)
    }
}

/**
 * Vigilancia después de apagar la alarma: detecta si el bus pasó el destino y se aleja
 * (te volviste a dormir) o si ya te bajaste, para terminar y apagar el GPS.
 *
 * - PASSED: la distancia creció [PASS_MARGIN_M] (+ error GPS) sobre la mínima alcanzada.
 * - ARRIVED: llevas [ARRIVED_HOLD_MS] a menos de [ARRIVED_RADIUS_M] del destino.
 * - STILL: [STILL_HOLD_MS] sin moverte (te bajaste en otro lado).
 * - TIMEOUT: tope de seguridad para no dejar el GPS encendido.
 */
class PassGuard(
    private val destLat: Double,
    private val destLon: Double,
    private val startMs: Long,
) {
    enum class Verdict { KEEP, PASSED, ARRIVED, STILL, TIMEOUT }

    data class Step(val verdict: Verdict, val distanceM: Double, val nextIntervalMs: Long, val highAccuracy: Boolean)

    private data class Sample(val tMs: Long, val distanceM: Double)

    var minDistance = Double.MAX_VALUE
        private set
    private var nearSinceMs: Long? = null
    private val samples = ArrayDeque<Sample>()
    private var lastFixMs = Long.MIN_VALUE

    fun update(lat: Double, lon: Double, accuracyM: Float, nowMs: Long): Step? {
        if (nowMs <= lastFixMs) return null
        lastFixMs = nowMs
        val d = haversine(lat, lon, destLat, destLon)
        if (d < minDistance) minDistance = d

        samples.addLast(Sample(nowMs, d))
        while (samples.isNotEmpty() && nowMs - samples.first().tMs > STILL_HOLD_MS) samples.removeFirst()

        val verdict = when {
            nowMs - startMs > MAX_MS -> Verdict.TIMEOUT
            d > minDistance + PASS_MARGIN_M + min(accuracyM.toDouble(), 150.0) -> Verdict.PASSED
            arrived(d, nowMs) -> Verdict.ARRIVED
            still(nowMs) -> Verdict.STILL
            else -> Verdict.KEEP
        }
        // Cerca del destino se mira seguido (pasarse a 60 km/h toma ~1 min); lejos, menos.
        val close = d < CLOSE_M
        return Step(verdict, d, if (close) 15_000 else 45_000, close)
    }

    private fun arrived(d: Double, nowMs: Long): Boolean {
        if (d > ARRIVED_RADIUS_M) {
            nearSinceMs = null
            return false
        }
        val since = nearSinceMs ?: nowMs.also { nearSinceMs = it }
        return nowMs - since >= ARRIVED_HOLD_MS
    }

    private fun still(nowMs: Long): Boolean {
        val first = samples.firstOrNull() ?: return false
        if (nowMs - first.tMs < STILL_HOLD_MS - 60_000) return false // aún no hay ventana completa
        val spread = samples.maxOf { it.distanceM } - samples.minOf { it.distanceM }
        return spread < STILL_SPREAD_M
    }

    companion object {
        const val PASS_MARGIN_M = 1_000.0
        const val ARRIVED_RADIUS_M = 400.0
        const val ARRIVED_HOLD_MS = 4 * 60_000L
        const val STILL_HOLD_MS = 15 * 60_000L // largo, para no confundirlo con un trancón
        const val STILL_SPREAD_M = 200.0
        const val CLOSE_M = 6_000.0
        const val MAX_MS = 60 * 60_000L
    }
}
