package com.snoozetravel.app.util

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notifications {
    const val CH_TRIP = "trip"
    const val CH_ALARM = "alarm"
    const val CH_PRE = "pre_alert"

    const val ID_TRIP = 1
    const val ID_ALARM = 2
    const val ID_PRE = 3

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val trip = NotificationChannel(CH_TRIP, "Viaje activo", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Muestra la distancia mientras el despertador está activo"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        // La alarma maneja su propio sonido/vibración (para poder escalar y repetir).
        val alarm = NotificationChannel(CH_ALARM, "Alarma de llegada", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Te despierta al acercarte a tu destino"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        val pre = NotificationChannel(CH_PRE, "Aviso previo", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Aviso corto antes de la alarma principal"
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannels(listOf(trip, alarm, pre))
    }

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission")
    fun notify(ctx: Context, id: Int, n: Notification) {
        if (canPost(ctx)) NotificationManagerCompat.from(ctx).notify(id, n)
    }

    fun cancel(ctx: Context, id: Int) = NotificationManagerCompat.from(ctx).cancel(id)

    /** Android 14+: permiso para mostrar la alarma en pantalla completa sobre el bloqueo. */
    fun canUseFullScreen(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 34 ||
            ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
}
