package com.snoozetravel.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Loc {
    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isEnabled(ctx: Context): Boolean =
        LocationManagerCompat.isLocationEnabled(ctx.getSystemService(LocationManager::class.java))

    /** Última ubicación conocida (gratis: no enciende el GPS). */
    @SuppressLint("MissingPermission")
    suspend fun last(ctx: Context): Location? {
        if (!hasPermission(ctx)) return null
        return suspendCancellableCoroutine { c ->
            LocationServices.getFusedLocationProviderClient(ctx).lastLocation
                .addOnSuccessListener { c.resume(it) }
                .addOnFailureListener { c.resume(null) }
        }
    }

    /** Ubicación precisa puntual (enciende el GPS solo unos segundos). */
    @SuppressLint("MissingPermission")
    suspend fun current(ctx: Context): Location? {
        if (!hasPermission(ctx)) return null
        return withTimeoutOrNull(20_000) {
            suspendCancellableCoroutine { c ->
                LocationServices.getFusedLocationProviderClient(ctx)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { c.resume(it) }
                    .addOnFailureListener { c.resume(null) }
            }
        }
    }
}

/** Distancia en metros sobre la superficie terrestre (haversine). */
fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_008.8
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * atan2(sqrt(a), sqrt(1 - a))
}
