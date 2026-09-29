package com.snoozetravel.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snoozetravel.app.data.TriggerMode
import com.snoozetravel.app.data.formatDistance
import com.snoozetravel.app.util.Loc
import com.snoozetravel.app.util.haversine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel, dark: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    val cs = MaterialTheme.colorScheme
    var confirmDelete by remember { mutableStateOf(false) }

    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (Loc.hasPermission(ctx)) vm.useCurrentLocation()
    }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
            vm.message = null
        }
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(if (vm.editingId == null) "Nuevo destino" else "Editar destino") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                actions = {
                    if (vm.editingId != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Eliminar") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .consumeWindowInsets(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // --- Búsqueda
            OutlinedTextField(
                value = vm.query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Busca un lugar o dirección") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    when {
                        vm.searching -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        vm.query.isNotEmpty() -> IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Default.Clear, "Borrar") }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = cs.outlineVariant,
                    focusedContainerColor = cs.surfaceContainerLow,
                    unfocusedContainerColor = cs.surfaceContainerLow,
                ),
            )

            AnimatedVisibility(vm.results.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Surface(
                    color = cs.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Column {
                        vm.results.forEachIndexed { i, p ->
                            if (i > 0) HorizontalDivider(color = cs.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { focus.clearFocus(); vm.select(p) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Place, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (p.address.isNotBlank()) {
                                        Text(
                                            p.address, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                vm.bias?.let { (la, lo) ->
                                    Text(
                                        formatDistance(haversine(la, lo, p.lat, p.lon)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = cs.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {
                        if (Loc.hasPermission(ctx)) vm.useCurrentLocation()
                        else locPerm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    },
                    label = { Text("Mi ubicación") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp)) },
                )
                AssistChip(
                    onClick = { openGoogleMaps(ctx, vm.query) },
                    label = { Text("Buscar en Google Maps") },
                    leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                )
            }

            // --- Mapa
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(MaterialTheme.shapes.large),
            ) {
                OsmMap(
                    point = vm.selected,
                    radiusM = vm.trigger.takeIf { it.mode == TriggerMode.DISTANCE }?.value?.times(1000),
                    dark = dark,
                    center = vm.bias,
                    onLongPress = vm::adjust,
                    modifier = Modifier.fillMaxSize(),
                )
                Surface(
                    color = cs.surface.copy(alpha = 0.88f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.align(Alignment.TopCenter).padding(10.dp),
                ) {
                    Text(
                        if (vm.selected == null) "Busca un lugar arriba" else "Mantén presionado para ajustar el punto",
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            // --- Detalles (aparecen al elegir un lugar)
            AnimatedVisibility(vm.selected != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.animateContentSize()) {
                    vm.selected?.address?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = vm.name,
                        onValueChange = vm::onNameChange,
                        label = { Text("Nombre (ej. Casa)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = cs.outlineVariant),
                    )
                    SectionTitle("¿Cuándo despertarte?")
                    TriggerSelector(vm.trigger, { vm.trigger = it })
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { if (vm.save()) onDone() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text("Guardar destino", style = MaterialTheme.typography.titleMedium) }
                }
            }

            Text(
                "Tip: en Google Maps busca el lugar, toca Compartir y elige Snooze Travel para guardarlo aquí.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 20.dp),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar destino?") },
            text = { Text("Se borrará \"${vm.name}\" de tus destinos.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(); onDone() }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

private fun openGoogleMaps(ctx: android.content.Context, query: String) {
    val uri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(query.ifBlank { " " }))
    val maps = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
    runCatching { ctx.startActivity(maps) }.onFailure {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }
    Toast.makeText(ctx, "Cuando lo encuentres: Compartir → Snooze Travel", Toast.LENGTH_LONG).show()
}
