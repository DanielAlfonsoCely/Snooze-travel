package com.snoozetravel.app.trip

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.snoozetravel.app.R
import com.snoozetravel.app.alarm.AlarmActivity
import com.snoozetravel.app.alarm.AlarmPlayer
import com.snoozetravel.app.alarm.Vibes
import com.snoozetravel.app.data.Destination
import com.snoozetravel.app.data.Store
import com.snoozetravel.app.data.Trigger
import com.snoozetravel.app.data.TriggerMode
import com.snoozetravel.app.data.formatDistance
import com.snoozetravel.app.data.formatEta
import com.snoozetravel.app.data.formatKm
import com.snoozetravel.app.ui.MainActivity
import com.snoozetravel.app.util.Loc
import com.snoozetravel.app.util.Notifications
import com.snoozetravel.app.widget.TripWidgetProvider
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Servicio en primer plano que SOLO existe mientras el despertador está activado.
 * Al llegar (o al desactivarlo) se detiene por completo: cero consumo en segundo plano.
 */
class TripService : Service() {

    private val fused: FusedLocationProviderClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val handler = Handler(Looper.getMainLooper())

    private var engine: TripEngine? = null
    private var guard: PassGuard? = null
    private var alarmAgain = false
    private var destination: Destination? = null
    private var trigger: Trigger? = null
    private var player: AlarmPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var registered = false
    private var reqInterval = 0L
    private var reqHigh = false
    private var lastWidgetDistance = -1e9

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::onLocation)
        }
    }

    private val autoStop = Runnable { endTrip() }
    private val guardTimeout = Runnable { if (guard != null) endTrip() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val d = intent.readDestination()
                val t = intent.readTrigger()
                if (d == null || t == null || !beginTrip(d, t)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                return START_REDELIVER_INTENT
            }
            ACTION_STOP -> endTrip()
            ACTION_DISMISS -> onDismiss()
            else -> if (engine == null && guard == null && TripState.status.value !is TripStatus.Alarming) stopSelf()
        }
        return START_NOT_STICKY
    }

    // ------------------------------------------------------------------ viaje

    private fun beginTrip(d: Destination, t: Trigger): Boolean {
        if (!Loc.hasPermission(this)) return false
        stopAlarmOutputs()
        destination = d
        trigger = t
        val s = Store.settings.value
        val pre = if (!s.preAlertEnabled) null else if (t.mode == TriggerMode.DISTANCE) s.preOffsetKm else s.preOffsetMin
        engine = TripEngine(d.lat, d.lon, t, pre)
        guard = null
        alarmAgain = false
        handler.removeCallbacks(guardTimeout)
        TripState.set(TripStatus.Active(d.id, d.name, t, d.lat, d.lon))

        try {
            ServiceCompat.startForeground(
                this, Notifications.ID_TRIP, tripNotification(null),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        } catch (e: Exception) {
            engine = null
            TripState.set(TripStatus.Idle)
            return false
        }

        Store.lastDestinationId = d.id
        registered = false
        requestUpdates(10_000, true)
        requestImmediateFix()
        TripWidgetProvider.update(this)
        return true
    }

    @SuppressLint("MissingPermission")
    private fun requestImmediateFix() {
        try {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc -> loc?.let(::onLocation) }
        } catch (_: SecurityException) {
        }
    }

    private fun onLocation(loc: Location) {
        if (guard != null) return onGuardLocation(loc)
        val e = engine ?: return
        if (e.mainDone) return
        val d = destination ?: return
        val t = trigger ?: return
        val now = loc.elapsedRealtimeNanos / 1_000_000
        val step = e.update(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else 50f, now) ?: return

        TripState.set(
            TripStatus.Active(
                d.id, d.name, t, d.lat, d.lon, loc.latitude, loc.longitude,
                step.distanceM, step.etaSec, step.progress,
            )
        )

        if (step.fireMain) {
            fireAlarm()
            return
        }
        Notifications.notify(this, Notifications.ID_TRIP, tripNotification(step))
        if (abs(step.distanceM - lastWidgetDistance) > 250) {
            lastWidgetDistance = step.distanceM
            TripWidgetProvider.update(this)
        }
        if (step.firePre) preAlert(step)
        requestUpdates(step.nextIntervalMs, step.highAccuracy)
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates(intervalMs: Long, high: Boolean) {
        if (registered && intervalMs == reqInterval && high == reqHigh) return
        val priority = if (high) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val request = LocationRequest.Builder(priority, intervalMs)
            // Acepta ubicaciones "gratis" que otras apps ya pidieron.
            .setMinUpdateIntervalMillis(max(5_000L, intervalMs / 2))
            .setWaitForAccurateLocation(false)
            .build()
        try {
            if (registered) fused.removeLocationUpdates(callback)
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
            registered = true
            reqInterval = intervalMs
            reqHigh = high
        } catch (_: SecurityException) {
            endTrip()
        }
    }

    private fun stopUpdates() {
        if (registered) fused.removeLocationUpdates(callback)
        registered = false
    }

    // ------------------------------------------------------------------ alarmas

    private fun preAlert(step: TripEngine.Step) {
        val d = destination ?: return
        Vibes.pulse(this)
        val left = step.etaSec?.let { "${formatEta(it)} · " }.orEmpty() + formatDistance(step.distanceM)
        val n = NotificationCompat.Builder(this, Notifications.CH_PRE)
            .setSmallIcon(R.drawable.ic_stat_snooze)
            .setContentTitle("Prepárate: te acercas a ${d.name}")
            .setContentText("Faltan $left. La alarma sonará pronto.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setTimeoutAfter(10 * 60_000L)
            .build()
        Notifications.notify(this, Notifications.ID_PRE, n)
    }

    // ------------------------------------------------------------------ vigilancia anti-pasarse

    private fun onDismiss() {
        if (TripState.status.value !is TripStatus.Alarming) {
            if (engine == null && guard == null) endTrip()
            return
        }
        stopAlarmOutputs()
        if (!alarmAgain && Store.settings.value.guardEnabled) startGuard() else endTrip()
    }

    private fun startGuard() {
        val d = destination ?: return endTrip()
        guard = PassGuard(d.lat, d.lon, SystemClock.elapsedRealtime())
        TripState.set(TripStatus.Guarding(d.id, d.name, d.lat, d.lon))
        Notifications.notify(this, Notifications.ID_TRIP, guardNotification(null))
        requestUpdates(15_000, true)
        requestImmediateFix()
        handler.postDelayed(guardTimeout, PassGuard.MAX_MS + 60_000)
        TripWidgetProvider.update(this)
    }

    private fun onGuardLocation(loc: Location) {
        val g = guard ?: return
        val d = destination ?: return
        val now = loc.elapsedRealtimeNanos / 1_000_000
        val step = g.update(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else 50f, now) ?: return
        when (step.verdict) {
            PassGuard.Verdict.KEEP -> {
                TripState.set(TripStatus.Guarding(d.id, d.name, d.lat, d.lon, loc.latitude, loc.longitude, step.distanceM))
                Notifications.notify(this, Notifications.ID_TRIP, guardNotification(step.distanceM))
                if (abs(step.distanceM - lastWidgetDistance) > 250) {
                    lastWidgetDistance = step.distanceM
                    TripWidgetProvider.update(this)
                }
                requestUpdates(step.nextIntervalMs, step.highAccuracy)
            }
            PassGuard.Verdict.PASSED -> {
                guard = null
                handler.removeCallbacks(guardTimeout)
                fireAlarm(again = true)
            }
            else -> endTrip() // llegaste, te bajaste en otro lado o se cumplió el tope
        }
    }

    private fun guardNotification(distanceM: Double?): Notification {
        val name = destination?.name ?: "tu destino"
        val where = distanceM?.let { "A ${formatDistance(it)} de $name. " }.orEmpty()
        return NotificationCompat.Builder(this, Notifications.CH_TRIP)
            .setSmallIcon(R.drawable.ic_stat_snooze)
            .setContentTitle("Vigilando que te bajes")
            .setContentText("${where}Si el bus se pasa, la alarma vuelve a sonar.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${where}Si el bus se pasa, la alarma vuelve a sonar."))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(openAppIntent())
            .addAction(0, "Ya me bajé", serviceIntent(ACTION_STOP, 4))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun fireAlarm(again: Boolean = false) {
        val d = destination ?: return
        stopUpdates()
        alarmAgain = again
        TripState.set(TripStatus.Alarming(d.id, d.name, again))
        Notifications.cancel(this, Notifications.ID_PRE)

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "snooze:alarm")
            .apply { acquire(ALARM_MAX_MS + 5_000) }

        val fullScreen = PendingIntent.getActivity(
            this, 1,
            Intent(this, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(this, Notifications.CH_ALARM)
            .setSmallIcon(R.drawable.ic_stat_snooze)
            .setContentTitle(if (again) "¡Te estás pasando de ${d.name}!" else "¡Despierta! Llegando a ${d.name}")
            .setContentText("Toca para apagar la alarma")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setOngoing(true)
            .addAction(0, "Apagar", serviceIntent(ACTION_DISMISS, 2))
            .build()
        Notifications.notify(this, Notifications.ID_ALARM, n)
        Notifications.notify(this, Notifications.ID_TRIP, tripNotification(null, arrived = true))

        player = AlarmPlayer(this).also { it.start(Store.settings.value) }
        handler.postDelayed(autoStop, ALARM_MAX_MS)
        TripWidgetProvider.update(this)
    }

    private fun stopAlarmOutputs() {
        handler.removeCallbacks(autoStop)
        player?.stop()
        player = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        Notifications.cancel(this, Notifications.ID_ALARM)
    }

    private fun endTrip() {
        stopUpdates()
        stopAlarmOutputs()
        Notifications.cancel(this, Notifications.ID_PRE)
        handler.removeCallbacks(guardTimeout)
        engine = null
        guard = null
        alarmAgain = false
        destination = null
        trigger = null
        TripState.set(TripStatus.Idle)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        TripWidgetProvider.update(this)
    }

    override fun onDestroy() {
        stopUpdates()
        stopAlarmOutputs()
        if (TripState.status.value !is TripStatus.Idle) {
            TripState.set(TripStatus.Idle)
            TripWidgetProvider.update(this)
        }
        super.onDestroy()
    }

    // ------------------------------------------------------------------ notificación

    private fun tripNotification(step: TripEngine.Step?, arrived: Boolean = false): Notification {
        val d = destination
        val t = trigger
        val text = when {
            arrived -> "¡Llegando!"
            step == null -> "Buscando tu ubicación…"
            else -> buildString {
                append(formatDistance(step.distanceM))
                step.etaSec?.let { append(" · ").append(formatEta(it)) }
                if (t != null) append(" · Suena ").append(
                    if (t.mode == TriggerMode.DISTANCE) "a ${formatKm(t.value)}" else "${t.value.roundToInt()} min antes"
                )
            }
        }
        return NotificationCompat.Builder(this, Notifications.CH_TRIP)
            .setSmallIcon(R.drawable.ic_stat_snooze)
            .setContentTitle("Rumbo a ${d?.name ?: "tu destino"}")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setProgress(100, ((step?.progress ?: 0f) * 100).roundToInt(), step == null && !arrived)
            .setContentIntent(openAppIntent())
            .addAction(0, "Desactivar", serviceIntent(ACTION_STOP, 3))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun serviceIntent(action: String, code: Int): PendingIntent = PendingIntent.getService(
        this, code, Intent(this, TripService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val ACTION_START = "com.snoozetravel.START"
        const val ACTION_STOP = "com.snoozetravel.STOP"
        const val ACTION_DISMISS = "com.snoozetravel.DISMISS"
        private const val ALARM_MAX_MS = 15 * 60_000L

        fun start(ctx: Context, d: Destination, t: Trigger) {
            val i = Intent(ctx, TripService::class.java).setAction(ACTION_START)
                .putExtra("id", d.id).putExtra("name", d.name).putExtra("address", d.address)
                .putExtra("lat", d.lat).putExtra("lon", d.lon)
                .putExtra("mode", t.mode.name).putExtra("value", t.value)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) = send(ctx, ACTION_STOP)
        fun dismiss(ctx: Context) = send(ctx, ACTION_DISMISS)

        private fun send(ctx: Context, action: String) {
            if (TripState.status.value is TripStatus.Idle) return
            runCatching { ctx.startService(Intent(ctx, TripService::class.java).setAction(action)) }
        }

        private fun Intent.readDestination(): Destination? {
            val id = getStringExtra("id") ?: return null
            if (!hasExtra("lat") || !hasExtra("lon")) return null
            return Destination(
                id, getStringExtra("name") ?: "Destino", getStringExtra("address").orEmpty(),
                getDoubleExtra("lat", 0.0), getDoubleExtra("lon", 0.0), Trigger.DEFAULT,
            )
        }

        private fun Intent.readTrigger(): Trigger? {
            val mode = runCatching { TriggerMode.valueOf(getStringExtra("mode")!!) }.getOrNull() ?: return null
            return Trigger(mode, getDoubleExtra("value", Trigger.DEFAULT.value))
        }
    }
}
