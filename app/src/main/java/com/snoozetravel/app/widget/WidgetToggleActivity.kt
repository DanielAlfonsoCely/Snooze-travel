package com.snoozetravel.app.widget

import android.content.Intent
import androidx.activity.ComponentActivity
import com.snoozetravel.app.data.Store
import com.snoozetravel.app.trip.TripService
import com.snoozetravel.app.trip.TripState
import com.snoozetravel.app.trip.TripStatus
import com.snoozetravel.app.ui.MainActivity
import com.snoozetravel.app.util.Loc

/**
 * Actividad invisible que recibe el toque del widget. Se ejecuta en onResume
 * (app en primer plano) para que Android permita iniciar el servicio de ubicación.
 */
class WidgetToggleActivity : ComponentActivity() {
    private var handled = false

    override fun onResume() {
        super.onResume()
        if (handled) return
        handled = true
        when (TripState.status.value) {
            is TripStatus.Active, is TripStatus.Guarding -> TripService.stop(this)
            is TripStatus.Alarming -> TripService.dismiss(this)
            TripStatus.Idle -> {
                val d = Store.lastDestination()
                if (d != null && Loc.hasPermission(this) && Loc.isEnabled(this)) {
                    TripService.start(this, d, d.trigger)
                } else {
                    // Falta destino, permiso o GPS: abrir la app para resolverlo.
                    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
