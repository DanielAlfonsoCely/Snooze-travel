package com.snoozetravel.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
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

    /** Mapa claro: colores suavizados para mantener el estilo sobrio. */
    val lightFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0.55f) })
}

/**
 * Mapa OpenStreetMap (osmdroid): sin API key, liviano, con caché de teselas.
 * Mantener presionado ajusta el punto.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun OsmMap(
    point: Place?,
    radiusM: Double?,
    dark: Boolean,
    center: Pair<Double, Double>?,
    onLongPress: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary.toArgb()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val onLongPressState = rememberUpdatedState(onLongPress)

    val map = remember {
        MapConfig.ensure(ctx)
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
            controller.setZoom(5.5)
            controller.setCenter(GeoPoint(4.6, -74.1))
            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?) = false
                override fun longPressHelper(p: GeoPoint?): Boolean {
                    p?.let { onLongPressState.value(it.latitude, it.longitude) }
                    return true
                }
            }))
            overlays.add(CopyrightOverlay(ctx))
            // Evita que el scroll de la pantalla robe los gestos del mapa.
            setOnTouchListener { v, _ ->
                v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
    }
    val circle = remember {
        Polygon(map).apply {
            outlinePaint.strokeWidth = 3f
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

    LaunchedEffect(point?.lat, point?.lon) {
        point?.let { map.controller.animateTo(GeoPoint(it.lat, it.lon), max(map.zoomLevelDouble, 15.0), 700L) }
    }
    LaunchedEffect(center) {
        if (point == null && center != null) {
            map.controller.animateTo(GeoPoint(center.first, center.second), 12.0, 700L)
        }
    }

    AndroidView(factory = { map }, modifier = modifier, update = { mv ->
        mv.overlayManager.tilesOverlay.setColorFilter(if (dark) MapConfig.darkFilter else MapConfig.lightFilter)
        mv.overlays.remove(circle)
        mv.overlays.remove(marker)
        if (point != null) {
            val gp = GeoPoint(point.lat, point.lon)
            if (radiusM != null && radiusM > 0) {
                circle.setPoints(Polygon.pointsAsCircle(gp, radiusM))
                circle.fillPaint.color = ColorUtils.setAlphaComponent(primary, 36)
                circle.outlinePaint.color = ColorUtils.setAlphaComponent(primary, 160)
                mv.overlays.add(circle)
            }
            marker.icon = ContextCompat.getDrawable(mv.context, R.drawable.ic_pin)?.mutate()?.apply { setTint(primary) }
            marker.position = gp
            mv.overlays.add(marker)
        }
        mv.invalidate()
    })
}
