package com.snoozetravel.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.snoozetravel.app.R
import com.snoozetravel.app.search.Place
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.File
import kotlin.math.max

private object MapConfig {
    @Volatile private var done = false

    fun ensure(ctx: Context) {
        if (done) return
        Configuration.getInstance().apply {
            load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = ctx.packageName
            osmdroidBasePath = File(ctx.filesDir, "osmdroid")
            osmdroidTileCache = File(ctx.cacheDir, "osmdroid-tiles")
            tileFileSystemCacheMaxBytes = 60L * 1024 * 1024
            tileFileSystemCacheTrimBytes = 45L * 1024 * 1024
        }
        done = true
    }

    /** Mapa oscuro: invierte y apaga los colores. */
    val darkFilter = ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            )
        ).apply {
            postConcat(ColorMatrix().apply { setSaturation(0.25f) })
            postConcat(ColorMatrix().apply { setScale(0.85f, 0.88f, 0.9f, 1f) })
        }
    )

    /** Mapa claro: colores suavizados para mantener el estilo. */
    val lightFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0.55f) })

    val dash = DashPathEffect(floatArrayOf(22f, 14f), 0f)
}

/** Cómo encuadra el mapa: el círculo de alarma completo, o tu posición + destino (viaje en vivo). */
enum class MapFit { CIRCLE, TRIP }

/**
 * Mapa OpenStreetMap (osmdroid): sin API key, liviano, con caché de teselas.
 *
 * @param radiusM radio del círculo de alarma alrededor de [point] (se anima al cambiar).
 * @param dashed borde punteado = radio aproximado (modo por tiempo).
 * @param me tu posición actual (punto azul).
 * @param onLongPress si no es null, mantener presionado ajusta el punto.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun OsmMap(
    point: Place?,
    radiusM: Double?,
    dark: Boolean,
    modifier: Modifier = Modifier,
    dashed: Boolean = false,
    me: Pair<Double, Double>? = null,
    center: Pair<Double, Double>? = null,
    interactive: Boolean = true,
    fit: MapFit = MapFit.CIRCLE,
    onLongPress: ((Double, Double) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary.toArgb()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val onLongPressState = rememberUpdatedState(onLongPress)
    val lastTouch = remember { longArrayOf(0L) }
    val animRadius by animateFloatAsState((radiusM ?: 0.0).toFloat(), tween(450), label = "radius")

    val map = remember {
        MapConfig.ensure(ctx)
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(interactive)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
            controller.setZoom(5.5)
            controller.setCenter(GeoPoint(4.6, -74.1))
            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?) = false
                override fun longPressHelper(p: GeoPoint?): Boolean {
                    val cb = onLongPressState.value ?: return false
                    p?.let { cb(it.latitude, it.longitude) }
                    return true
                }
            }))
            overlays.add(CopyrightOverlay(ctx))
            setOnTouchListener { v, _ ->
                if (!interactive) return@setOnTouchListener true // solo visual
                lastTouch[0] = SystemClock.uptimeMillis()
                // Evita que el scroll de la pantalla robe los gestos del mapa.
                v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
    }
    val circle = remember {
        Polygon(map).apply {
            outlinePaint.strokeWidth = 4f
            setOnClickListener { _, _, _ -> true }
        }
    }
    val marker = remember {
        Marker(map).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setInfoWindow(null)
            setOnMarkerClickListener { _, _ -> true }
        }
    }
    val meMarker = remember {
        Marker(map).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setInfoWindow(null)
            setOnMarkerClickListener { _, _ -> true }
            icon = ContextCompat.getDrawable(ctx, R.drawable.ic_me)
        }
    }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        map.onResume()
        onDispose {
            lifecycle.removeObserver(obs)
            map.onPause()
            map.onDetach()
        }
    }

    // Encuadre del círculo: se reajusta cuando cambia el punto o el radio (con pequeña espera
    // para no saltar en cada paso del slider).
    if (fit == MapFit.CIRCLE) {
        LaunchedEffect(point?.lat, point?.lon, radiusM) {
            val p = point ?: return@LaunchedEffect
            delay(220)
            val gp = GeoPoint(p.lat, p.lon)
            if (radiusM != null && radiusM > 0) fitTo(map, Polygon.pointsAsCircle(gp, radiusM))
            else map.controller.animateTo(gp, max(map.zoomLevelDouble, 15.0), 700L)
        }
        LaunchedEffect(center) {
            if (point == null && center != null) map.controller.animateTo(GeoPoint(center.first, center.second), 12.0, 700L)
        }
    } else {
        // Viaje en vivo: tú + destino. Si tocaste el mapa, no te lo muevo por 20 s.
        LaunchedEffect(me, point?.lat, point?.lon) {
            if (SystemClock.uptimeMillis() - lastTouch[0] < 20_000) return@LaunchedEffect
            val pts = listOfNotNull(point?.let { GeoPoint(it.lat, it.lon) }, me?.let { GeoPoint(it.first, it.second) })
            fitTo(map, pts)
        }
    }

    AndroidView(factory = { map }, modifier = modifier, update = { mv ->
        mv.overlayManager.tilesOverlay.setColorFilter(if (dark) MapConfig.darkFilter else MapConfig.lightFilter)
        mv.overlays.remove(circle)
        mv.overlays.remove(marker)
        mv.overlays.remove(meMarker)
        if (point != null) {
            val gp = GeoPoint(point.lat, point.lon)
            if (animRadius > 1f) {
                circle.setPoints(Polygon.pointsAsCircle(gp, animRadius.toDouble()))
                circle.fillPaint.color = ColorUtils.setAlphaComponent(primary, 40)
                circle.outlinePaint.color = ColorUtils.setAlphaComponent(primary, 190)
                circle.outlinePaint.pathEffect = if (dashed) MapConfig.dash else null
                mv.overlays.add(circle)
            }
            marker.icon = ContextCompat.getDrawable(mv.context, R.drawable.ic_pin)?.mutate()?.apply { setTint(primary) }
            marker.position = gp
            mv.overlays.add(marker)
        }
        if (me != null) {
            meMarker.position = GeoPoint(me.first, me.second)
            mv.overlays.add(meMarker)
        }
        mv.invalidate()
    })
}

/** Encuadra todos los puntos (espera al primer layout si el mapa aún no tiene tamaño). */
private fun fitTo(map: MapView, pts: List<GeoPoint>) {
    if (pts.isEmpty()) return
    val run = {
        if (pts.size == 1) {
            map.controller.animateTo(pts[0], max(map.zoomLevelDouble, 14.0), 700L)
        } else {
            val bb = BoundingBox.fromGeoPoints(pts)
            val border = (28 * map.resources.displayMetrics.density).toInt()
            map.zoomToBoundingBox(bb, true, border)
        }
    }
    if (map.width == 0 || map.height == 0) map.addOnFirstLayoutListener { _, _, _, _, _ -> run() } else run()
}
