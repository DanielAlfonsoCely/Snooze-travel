package com.snoozetravel.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.snoozetravel.app.R
import com.snoozetravel.app.data.Store
import com.snoozetravel.app.data.formatDistance
import com.snoozetravel.app.data.formatEta
import com.snoozetravel.app.trip.TripState
import com.snoozetravel.app.trip.TripStatus

/** Widget de un toque: activa/desactiva el despertador para el último destino usado. */
class TripWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, views(context))
    }

    companion object {
        fun update(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, TripWidgetProvider::class.java))
            if (ids.isNotEmpty()) mgr.updateAppWidget(ids, views(ctx))
        }

        private fun views(ctx: Context): RemoteViews {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_trip)
            when (val s = TripState.status.value) {
                TripStatus.Idle -> {
                    val d = Store.lastDestination()
                    rv.setTextViewText(R.id.widget_title, d?.name ?: "Snooze Travel")
                    rv.setTextViewText(R.id.widget_subtitle, d?.let { "Inactivo · ${it.trigger.summary}" } ?: "Agrega un destino")
                    rv.setTextViewText(R.id.widget_button, if (d != null) "Activar" else "Abrir")
                    styleButton(ctx, rv, active = false)
                }
                is TripStatus.Active -> {
                    rv.setTextViewText(R.id.widget_title, s.destinationName)
                    val info = s.distanceM?.let { m ->
                        formatDistance(m) + (s.etaSec?.let { " · ${formatEta(it)}" } ?: "")
                    } ?: "Buscando ubicación…"
                    rv.setTextViewText(R.id.widget_subtitle, "Activo · $info")
                    rv.setTextViewText(R.id.widget_button, "Desactivar")
                    styleButton(ctx, rv, active = true)
                }
                is TripStatus.Alarming -> {
                    rv.setTextViewText(R.id.widget_title, "¡Llegando a ${s.destinationName}!")
                    rv.setTextViewText(R.id.widget_subtitle, "Toca para apagar")
                    rv.setTextViewText(R.id.widget_button, "Apagar")
                    styleButton(ctx, rv, active = true)
                }
            }
            val pi = PendingIntent.getActivity(
                ctx, 10,
                Intent(ctx, WidgetToggleActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            rv.setOnClickPendingIntent(R.id.widget_root, pi)
            rv.setOnClickPendingIntent(R.id.widget_button, pi)
            return rv
        }

        private fun styleButton(ctx: Context, rv: RemoteViews, active: Boolean) {
            rv.setInt(R.id.widget_button, "setBackgroundResource", if (active) R.drawable.widget_btn_active else R.drawable.widget_btn)
            rv.setTextColor(
                R.id.widget_button,
                ctx.getColor(if (active) R.color.widget_on_active else R.color.widget_on_accent),
            )
        }
    }
}
