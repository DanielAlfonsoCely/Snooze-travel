package com.snoozetravel.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.snoozetravel.app.data.Destination
import com.snoozetravel.app.data.Trigger
import com.snoozetravel.app.trip.TripService
import com.snoozetravel.app.util.Loc
import com.snoozetravel.app.util.Notifications

/**
 * Devuelve una función que activa el despertador pidiendo solo lo imprescindible:
 * permiso de ubicación (+ notificaciones) y que el GPS esté encendido.
 */
@Composable
fun rememberTripActivator(): (Destination, Trigger) -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<Pair<Destination, Trigger>?>(null) }
    var showFullScreenDialog by remember { mutableStateOf(false) }

    fun launch(p: Pair<Destination, Trigger>) {
        TripService.start(ctx, p.first, p.second)
        pending = null
        if (!Notifications.canUseFullScreen(ctx)) showFullScreenDialog = true
    }

    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val p = pending ?: return@rememberLauncherForActivityResult
        if (res.resultCode == Activity.RESULT_OK || Loc.isEnabled(ctx)) launch(p)
        else Toast.makeText(ctx, "Activa la ubicación para usar el despertador", Toast.LENGTH_LONG).show()
    }

    fun checkSettingsAndLaunch(p: Pair<Destination, Trigger>) {
        val request = LocationSettingsRequest.Builder()
            .addLocationRequest(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000).build())
            .build()
        LocationServices.getSettingsClient(ctx).checkLocationSettings(request)
            .addOnSuccessListener { launch(p) }
            .addOnFailureListener { e ->
                if (e is ResolvableApiException) {
                    settingsLauncher.launch(IntentSenderRequest.Builder(e.resolution).build())
                } else if (Loc.isEnabled(ctx)) {
                    launch(p)
                } else {
                    Toast.makeText(ctx, "Activa la ubicación para usar el despertador", Toast.LENGTH_LONG).show()
                }
            }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val p = pending ?: return@rememberLauncherForActivityResult
        if (Loc.hasPermission(ctx)) checkSettingsAndLaunch(p)
        else Toast.makeText(ctx, "Sin permiso de ubicación no puedo saber cuándo despertarte", Toast.LENGTH_LONG).show()
    }

    if (showFullScreenDialog) {
        AlertDialog(
            onDismissRequest = { showFullScreenDialog = false },
            title = { Text("Alarma en pantalla completa") },
            text = { Text("Para que la alarma aparezca sobre la pantalla de bloqueo, permite las notificaciones de pantalla completa para Snooze Travel.") },
            confirmButton = {
                TextButton(onClick = {
                    showFullScreenDialog = false
                    if (Build.VERSION.SDK_INT >= 34) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}"))
                        )
                    }
                }) { Text("Permitir") }
            },
            dismissButton = { TextButton(onClick = { showFullScreenDialog = false }) { Text("Ahora no") } },
        )
    }

    return { d, t ->
        val p = d to t
        pending = p
        val needs = buildList {
            if (!Loc.hasPermission(ctx)) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(ctx)) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needs.isEmpty()) checkSettingsAndLaunch(p) else permLauncher.launch(needs.toTypedArray())
    }
}
