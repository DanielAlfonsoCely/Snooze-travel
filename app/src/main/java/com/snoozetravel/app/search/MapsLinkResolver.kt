package com.snoozetravel.app.search

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/**
 * Convierte lo que comparte Google Maps ("Nombre\nDirección\nhttps://maps.app.goo.gl/…")
 * o un enlace geo: en un [Place]. Si el enlace no trae coordenadas, busca por el nombre.
 */
object MapsLinkResolver {
    private val URL_REGEX = Regex("""https?://\S+""")
    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private val COORD_PATTERNS = listOf(
        Regex("""!3d(-?\d{1,2}\.\d+)!4d(-?\d{1,3}\.\d+)"""), // coordenada exacta del lugar
        Regex("""[?&](?:q|query|ll|destination|daddr|center)=(-?\d{1,2}\.\d+),\s*\+?(-?\d{1,3}\.\d+)"""),
        Regex("""@(-?\d{1,2}\.\d+),(-?\d{1,3}\.\d+)"""), // centro del mapa
        Regex("""geo:(-?\d{1,2}\.\d+),(-?\d{1,3}\.\d+)"""),
    )

    suspend fun resolve(ctx: Context, text: String): Place? = withContext(Dispatchers.IO) {
        val raw = text.trim()
        val url = URL_REGEX.find(raw)?.value
        val labels = raw.replace(url ?: "\u0000", "").lines().map { it.trim() }.filter { it.isNotBlank() }
        var name = labels.firstOrNull()
        val address = labels.drop(1).joinToString(", ")

        if (raw.startsWith("geo:")) {
            val label = Regex("""\(([^)]+)\)""").find(decode(raw))?.groupValues?.get(1)
            coords(raw)?.let { (la, lo) -> return@withContext Place(label ?: "Lugar compartido", "", la, lo) }
            val q = Regex("""[?&]q=([^&]+)""").find(decode(raw))?.groupValues?.get(1)
            return@withContext q?.let { searchFirst(ctx, it, it) }
        }

        if (url != null) {
            val finalUrl = runCatching { expand(url) }.getOrDefault(url)
            val urlName = Regex("""/maps/place/([^/@?]+)""").find(decode(finalUrl))?.groupValues?.get(1)
                ?.replace('+', ' ')
            if (name == null) name = urlName
            coords(finalUrl)?.let { (la, lo) ->
                return@withContext Place(name ?: "Lugar compartido", address, la, lo)
            }
            val q = listOfNotNull(name, address.ifBlank { null }).joinToString(", ").ifBlank {
                Regex("""[?&]q=([^&]+)""").find(decode(finalUrl))?.groupValues?.get(1)?.replace('+', ' ')
            }
            return@withContext q?.let { searchFirst(ctx, it, name ?: it) }
        }

        // Texto sin enlace: se busca tal cual.
        if (raw.isNotBlank()) searchFirst(ctx, raw.lines().joinToString(" "), name ?: raw) else null
    }

    private suspend fun searchFirst(ctx: Context, query: String, name: String): Place? =
        PlaceSearch.search(ctx, query, null, null).firstOrNull()?.copy(name = name.take(60))

    /** Sigue redirecciones de enlaces cortos (maps.app.goo.gl) sin descargar la página. */
    private fun expand(start: String): String {
        var current = start
        repeat(6) {
            if (coords(current) != null) return current
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("User-Agent", BROWSER_UA)
            val code = conn.responseCode
            val location = conn.getHeaderField("Location")
            conn.disconnect()
            if (code !in 300..399 || location == null) return current
            current = URL(URL(current), location).toString()
        }
        return current
    }

    private fun coords(u: String): Pair<Double, Double>? {
        val s = decode(u)
        for (p in COORD_PATTERNS) {
            val m = p.find(s) ?: continue
            val lat = m.groupValues[1].toDoubleOrNull() ?: continue
            val lon = m.groupValues[2].toDoubleOrNull() ?: continue
            if (lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0)) return lat to lon
        }
        return null
    }

    private fun decode(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
}
