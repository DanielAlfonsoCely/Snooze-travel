package com.snoozetravel.app.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snoozetravel.app.alarm.AlarmActivity
import com.snoozetravel.app.alarm.Vibes
import com.snoozetravel.app.data.Store
import com.snoozetravel.app.data.ThemeMode
import com.snoozetravel.app.data.VibePattern
import com.snoozetravel.app.data.formatKm
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s by Store.settings.collectAsStateWithLifecycle()
    val theme by Store.theme.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme

    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = res.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
        val title = uri?.let { runCatching { RingtoneManager.getRingtone(ctx, it)?.getTitle(ctx) }.getOrNull() }
        Store.updateSettings { it.copy(ringtoneUri = uri?.toString(), ringtoneTitle = title) }
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // --- Apariencia
            SectionTitle("Apariencia")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val options = listOf(ThemeMode.SYSTEM to "Sistema", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Oscuro")
                options.forEachIndexed { i, (mode, label) ->
                    SegmentedButton(
                        selected = theme == mode,
                        onClick = { Store.setTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    ) { Text(label) }
                }
            }

            // --- Vibración
            SectionTitle("Vibración")
            Card {
                VibePattern.entries.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                Store.updateSettings { it.copy(pattern = p) }
                                Vibes.vibrate(ctx, p, repeat = false) // vista previa
                            }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = s.pattern == p, onClick = null, modifier = Modifier.padding(12.dp))
                        Text(p.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Hint("Toca un patrón para sentirlo. Siempre vibra a la máxima intensidad del celular.")

            // --- Sonido
            SectionTitle("Sonido")
            Card {
                ToggleRow("Sonar además de vibrar", s.soundEnabled) { v -> Store.updateSettings { it.copy(soundEnabled = v) } }
                AnimatedVisibility(s.soundEnabled, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column {
                        Text(
                            "Empezar a sonar",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, top = 8.dp),
                        )
                        FlowRow(
                            Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(0 to "De inmediato", 15 to "A los 15 s", 30 to "A los 30 s", 60 to "Al minuto").forEach { (sec, label) ->
                                FilterChip(
                                    selected = s.soundDelaySec == sec,
                                    onClick = { Store.updateSettings { it.copy(soundDelaySec = sec) } },
                                    label = { Text(label) },
                                )
                            }
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ringtonePicker.launch(
                                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Tono de la alarma")
                                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                            .putExtra(
                                                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                                s.ringtoneUri?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                                            )
                                    )
                                }
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                        ) {
                            Text("Tono", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text(s.ringtoneTitle ?: "Alarma predeterminada", color = cs.primary, style = MaterialTheme.typography.bodyMedium)
                        }
                        ToggleRow("Subir el volumen poco a poco", s.rampVolume) { v -> Store.updateSettings { it.copy(rampVolume = v) } }
                        ToggleRow("Usar volumen máximo de alarma", s.maxVolume) { v -> Store.updateSettings { it.copy(maxVolume = v) } }
                    }
                }
            }
            Hint("Primero vibra y, si no la apagas, empieza a sonar. Suena aunque el celular esté en silencio.")

            // --- Aviso previo
            SectionTitle("Aviso previo")
            Card {
                ToggleRow("Avisarme antes de la alarma", s.preAlertEnabled) { v -> Store.updateSettings { it.copy(preAlertEnabled = v) } }
                AnimatedVisibility(s.preAlertEnabled, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text("Si despiertas por tiempo: ${s.preOffsetMin.roundToInt()} min antes de la alarma", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = s.preOffsetMin.toFloat(),
                            onValueChange = { v -> Store.updateSettings { it.copy(preOffsetMin = v.roundToInt().toDouble()) } },
                            valueRange = 2f..15f,
                            steps = 12,
                        )
                        Text("Si despiertas por distancia: ${formatKm(s.preOffsetKm)} antes de la alarma", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = s.preOffsetKm.toFloat(),
                            onValueChange = { v -> Store.updateSettings { it.copy(preOffsetKm = v.roundToInt().toDouble()) } },
                            valueRange = 1f..10f,
                            steps = 8,
                        )
                    }
                }
            }
            Hint("Una vibración corta y una notificación para que te vayas preparando.")

            Spacer(Modifier.height(24.dp))
            FilledTonalButton(onClick = { AlarmActivity.test(ctx) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Probar alarma")
            }
            Text(
                "Mapas © colaboradores de OpenStreetMap · Búsqueda: Google Geocoder y Photon (Komoot)",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 6.dp)) { content() }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}
