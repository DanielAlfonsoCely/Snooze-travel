package com.snoozetravel.app.search

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.snoozetravel.app.util.haversine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume

data class Place(val name: String, val address: String, val lat: Double, val lon: Double)

/**
 * Búsqueda de lugares 100% gratis y sin API key:
 *  - Geocoder de Android (en Samsung usa el backend de Google): direcciones y muchos sitios.
 *  - Photon (OpenStreetMap): negocios, paraderos, barrios, con prioridad a lo cercano.
 */
object PlaceSearch {
    private const val UA = "SnoozeTravel/1.0 (Android; personal use)"

    suspend fun search(ctx: Context, query: String, biasLat: Double?, biasLon: Double?): List<Place> = coroutineScope {
        val google = async(Dispatchers.IO) { runCatching { geocode(ctx, query) }.getOrDefault(emptyList()) }
        val osm = async(Dispatchers.IO) { runCatching { photon(query, biasLat, biasLon) }.getOrDefault(emptyList()) }
        val out = mutableListOf<Place>()
        for (p in google.await() + osm.await()) {
            if (out.none { haversine(it.lat, it.lon, p.lat, p.lon) < 60 }) out += p
        }
        out.take(10)
    }

    /** Dirección legible para un punto (cuando se ajusta el pin o se usa "Mi ubicación"). */
    suspend fun reverse(ctx: Context, lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        val gc = Geocoder(ctx, Locale.getDefault())
        val list: List<Address> = withTimeoutOrNull(8_000) {
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { c ->
                    gc.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = c.resume(addresses)
                        override fun onError(errorMessage: String?) = c.resume(emptyList())
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                runCatching { gc.getFromLocation(lat, lon, 1) }.getOrNull().orEmpty()
            }
        }.orEmpty()
        list.firstOrNull()?.getAddressLine(0)
    }

    private suspend fun geocode(ctx: Context, q: String): List<Place> {
        if (!Geocoder.isPresent()) return emptyList()
        val gc = Geocoder(ctx, Locale.getDefault())
        val list: List<Address> = withTimeoutOrNull(8_000) {
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { c ->
                    gc.getFromLocationName(q, 5, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = c.resume(addresses)
                        override fun onError(errorMessage: String?) = c.resume(emptyList())
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                gc.getFromLocationName(q, 5).orEmpty()
            }
        }.orEmpty()
        return list.filter { it.hasLatitude() && it.hasLongitude() }.map { a ->
            val line = a.getAddressLine(0).orEmpty()
            val feature = a.featureName?.takeIf { f -> f.isNotBlank() && !f.all { it.isDigit() } && f != line }
            Place(feature ?: line.substringBefore(","), line, a.latitude, a.longitude)
        }
    }

    private fun photon(q: String, lat: Double?, lon: Double?): List<Place> {
        val url = buildString {
            append("https://photon.komoot.io/api/?limit=8&q=")
            append(URLEncoder.encode(q, "UTF-8"))
            if (lat != null && lon != null) append("&lat=$lat&lon=$lon")
        }
        val features = JSONObject(httpGet(url)).optJSONArray("features") ?: return emptyList()
        return (0 until features.length()).mapNotNull { i ->
            val f = features.getJSONObject(i)
            val c = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
            val p = f.optJSONObject("properties") ?: JSONObject()
            fun s(k: String) = p.optString(k).takeIf { it.isNotBlank() }
            val street = listOfNotNull(s("street"), s("housenumber")).joinToString(" ").ifBlank { null }
            val name = s("name") ?: street ?: return@mapNotNull null
            val address = listOfNotNull(
                street.takeIf { it != name }, s("district"), s("city"), s("state"), s("country"),
            ).distinct().joinToString(", ")
            Place(name, address, c.getDouble(1), c.getDouble(0))
        }
    }

    internal fun httpGet(url: String, userAgent: String = UA): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        conn.setRequestProperty("User-Agent", userAgent)
        conn.setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag())
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
