package com.snoozetravel.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snoozetravel.app.R
import com.snoozetravel.app.data.Destination
import com.snoozetravel.app.data.Store
import com.snoozetravel.app.data.Trigger
import com.snoozetravel.app.data.formatDistance
import com.snoozetravel.app.data.formatEta
import com.snoozetravel.app.data.TriggerMode
import com.snoozetravel.app.data.radiusMeters
import com.snoozetravel.app.search.Place
import androidx.compose.foundation.layout.fillMaxSize
import com.snoozetravel.app.trip.TripService
import com.snoozetravel.app.trip.TripState
import com.snoozetravel.app.trip.TripStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    dark: Boolean,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewDestination: () -> Unit,
    onEditDestination: (Destination) -> Unit,
    onActivate: (Destination, Trigger) -> Unit,
) {
    val ctx = LocalContext.current
    val destinations by Store.destinations.collectAsStateWithLifecycle()
    val status by TripState.status.collectAsStateWithLifecycle()
    var sheetFor by remember { mutableStateOf<Destination?>(null) }
    val activeId = when (val s = status) {
        is TripStatus.Active -> s.destinationId
        is TripStatus.Alarming -> s.destinationId
        is TripStatus.Guarding -> s.destinationId
        TripStatus.Idle -> null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Snooze Travel") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    ThemeToggle(dark, onToggleTheme)
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Ajustes") }
                },
            )
        },
        floatingActionButton = {
            AnimatedVisibility(status is TripStatus.Idle, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                ExtendedFloatingActionButton(
                    onClick = onNewDestination,
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Destino") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp, top = pad.calculateTopPadding() + 4.dp,
                bottom = pad.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "status") {
                AnimatedContent(
                    targetState = status,
                    contentKey = { it::class }, // anima solo al cambiar de estado, no con cada ubicación
                    transitionSpec = { (fadeIn(tween(300)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(200)) },
                    label = "status",
                ) { current ->
                    when (val s = current) {
                        TripStatus.Idle -> IdleHeader(hasDestinations = destinations.isNotEmpty())
                        is TripStatus.Active -> ActiveTripCard(s, dark, onStop = { TripService.stop(ctx) })
                        is TripStatus.Alarming -> AlarmingCard(s, onDismiss = { TripService.dismiss(ctx) })
                        is TripStatus.Guarding -> GuardingCard(s, dark, onDone = { TripService.stop(ctx) })
                    }
                }
            }
            if (destinations.isNotEmpty()) {
                item(key = "title") { SectionTitle("Mis destinos", Modifier.animateItem()) }
            }
            items(destinations, key = { it.id }) { d ->
                DestinationRow(
                    d = d,
                    active = d.id == activeId,
                    enabled = status is TripStatus.Idle,
                    onClick = { sheetFor = d },
                    onEdit = { onEditDestination(d) },
                    modifier = Modifier.animateItem(),
                )
            }
            if (destinations.isEmpty()) {
                item(key = "empty") { EmptyState(onNewDestination, Modifier.animateItem()) }
            }
        }
    }

    sheetFor?.let { d ->
        ActivateSheet(d, dark, onDismiss = { sheetFor = null }, onActivate = { t, remember ->
            if (remember) Store.saveDestination(d.copy(trigger = t))
            sheetFor = null
            onActivate(d, t)
        })
    }
}

@Composable
private fun ThemeToggle(dark: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (dark) 360f else 0f, tween(500), label = "rot")
    IconButton(onClick = onToggle) {
        AnimatedContent(
            targetState = dark,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.6f)) },
            label = "themeIcon",
        ) { isDark ->
            Icon(
                painterResource(if (isDark) R.drawable.ic_sun else R.drawable.ic_moon),
                contentDescription = if (isDark) "Tema claro" else "Tema oscuro",
                modifier = Modifier.rotate(rotation),
            )
        }
    }
}

@Composable
private fun IdleHeader(hasDestinations: Boolean) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(cs.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_moon), null, tint = cs.onSecondaryContainer, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Despertador inactivo", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (hasDestinations) "Toca un destino para activarlo en este viaje."
                    else "Agrega tu primer destino para empezar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActiveTripCard(s: TripStatus.Active, dark: Boolean, onStop: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val pulse = rememberInfiniteTransition(label = "pulse")
    val dot by pulse.animateFloat(0.6f, 1.25f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "dot")
    val progress by animateFloatAsState(s.progress, tween(800), label = "progress")

    Surface(color = cs.primaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).scale(dot).clip(CircleShape).background(cs.primary))
                Spacer(Modifier.width(10.dp))
                Text("Despertador activo", style = MaterialTheme.typography.labelLarge, color = cs.onPrimaryContainer)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Rumbo a ${s.destinationName}",
                style = MaterialTheme.typography.titleLarge,
                color = cs.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                AnimatedCounter(s.distanceM?.let(::formatDistance) ?: "…")
                Spacer(Modifier.width(10.dp))
                s.etaSec?.let {
                    Text(
                        formatEta(it),
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = cs.primary,
                trackColor = cs.onPrimaryContainer.copy(alpha = 0.12f),
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (s.distanceM == null) "Buscando tu ubicación…" else "Sonará ${s.trigger.summary.lowercase()}. Puedes dormir tranquilo.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onPrimaryContainer.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(14.dp))
            // Mapa en vivo: tú, el destino y el círculo donde sonará (punteado = estimado por tiempo).
            val timeMode = s.trigger.mode == TriggerMode.TIME
            LiveMap(
                dest = Place(s.destinationName, "", s.destLat, s.destLon),
                radiusM = s.trigger.radiusMeters(s.speedMs),
                dashed = timeMode,
                me = if (s.myLat != null && s.myLon != null) s.myLat to s.myLon else null,
                dark = dark,
                caption = if (timeMode) {
                    if (s.speedMs != null) "Círculo estimado con la velocidad actual del bus" else "Círculo estimado a 60 km/h"
                } else null,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Desactivar") }
        }
    }
}

@Composable
private fun GuardingCard(s: TripStatus.Guarding, dark: Boolean, onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val pulse = rememberInfiniteTransition(label = "guard")
    val dot by pulse.animateFloat(0.6f, 1.25f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "dot")

    Surface(color = cs.secondaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).scale(dot).clip(CircleShape).background(cs.secondary))
                Spacer(Modifier.width(10.dp))
                Text("Vigilando que te bajes", style = MaterialTheme.typography.labelLarge, color = cs.onSecondaryContainer)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                s.distanceM?.let { "A ${formatDistance(it)} de ${s.destinationName}" } ?: s.destinationName,
                style = MaterialTheme.typography.titleLarge,
                color = cs.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Si el bus pasa tu destino y se aleja, la alarma vuelve a sonar.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSecondaryContainer.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(14.dp))
            LiveMap(
                dest = Place(s.destinationName, "", s.destLat, s.destLon),
                radiusM = null,
                dashed = false,
                me = if (s.myLat != null && s.myLon != null) s.myLat to s.myLon else null,
                dark = dark,
                caption = null,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Ya me bajé") }
        }
    }
}

@Composable
private fun LiveMap(dest: Place, radiusM: Double?, dashed: Boolean, me: Pair<Double, Double>?, dark: Boolean, caption: String?) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.medium)) {
        OsmMap(
            point = dest,
            radiusM = radiusM,
            dark = dark,
            dashed = dashed,
            me = me,
            fit = MapFit.TRIP,
            modifier = Modifier.fillMaxSize(),
        )
        if (caption != null) {
            Surface(
                color = cs.surface.copy(alpha = 0.88f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            ) {
                Text(
                    caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AlarmingCard(s: TripStatus.Alarming, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.tertiaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp)) {
            Text(if (s.again) "¡Te estás pasando!" else "¡Estás llegando!", style = MaterialTheme.typography.headlineMedium, color = cs.onTertiaryContainer)
            Text(s.destinationName, style = MaterialTheme.typography.titleMedium, color = cs.onTertiaryContainer.copy(alpha = 0.8f))
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = cs.tertiary, contentColor = cs.onTertiary),
            ) { Text("Apagar alarma") }
        }
    }
}

@Composable
private fun DestinationRow(
    d: Destination,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val scale by animateFloatAsState(if (active) 1.02f else 1f, label = "scale")
    Surface(
        color = if (active) cs.secondaryContainer else cs.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().scale(scale),
    ) {
        Row(
            Modifier.clickable(enabled = enabled, onClick = onClick).padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(cs.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.LocationOn, null, tint = cs.primary, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (d.address.isNotBlank()) {
                    Text(
                        d.address, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    if (active) "Activo · ${d.trigger.summary}" else d.trigger.summary,
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            IconButton(onClick = onEdit, enabled = enabled) { Icon(Icons.Default.Edit, "Editar", tint = cs.onSurfaceVariant) }
        }
    }
}

@Composable
private fun EmptyState(onNew: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(cs.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.LocationOn, null, tint = cs.primary, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Sin destinos todavía", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Guarda tu casa u otros lugares y actívalos cuando creas que te vas a dormir.",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = onNew) { Text("Agregar destino") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivateSheet(d: Destination, dark: Boolean, onDismiss: () -> Unit, onActivate: (Trigger, Boolean) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var trigger by remember(d.id) { mutableStateOf(d.trigger) }
    var rememberIt by remember(d.id) { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = cs.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(d.name, style = MaterialTheme.typography.titleLarge)
            if (d.address.isNotBlank()) {
                Text(d.address, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2)
            }
            Spacer(Modifier.height(12.dp))
            RadiusPreviewMap(Place(d.name, d.address, d.lat, d.lon), trigger, dark, Modifier.height(170.dp))
            SectionTitle("¿Cuándo despertarte en este viaje?")
            TriggerSelector(trigger, { trigger = it })
            AnimatedVisibility(trigger != d.trigger) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Guardar como predeterminado", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = rememberIt, onCheckedChange = { rememberIt = it })
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { scope.launch { state.hide() }.invokeOnCompletion { onActivate(trigger, rememberIt) } },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Activar despertador", style = MaterialTheme.typography.titleMedium) }
        }
    }
}
