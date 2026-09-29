package com.snoozetravel.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snoozetravel.app.data.Trigger
import com.snoozetravel.app.data.TriggerMode
import com.snoozetravel.app.data.formatKm
import kotlin.math.roundToInt

/** Selector de "cuándo despertarme": por distancia (km) o por tiempo (min antes). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TriggerSelector(trigger: Trigger, onChange: (Trigger) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val modes = listOf(TriggerMode.TIME to "Por tiempo", TriggerMode.DISTANCE to "Por distancia")
            modes.forEachIndexed { i, (mode, label) ->
                SegmentedButton(
                    selected = trigger.mode == mode,
                    onClick = {
                        if (trigger.mode != mode) {
                            // A ~60 km/h: 1 km ≈ 1 min, así la conversión se siente natural.
                            val range = if (mode == TriggerMode.TIME) Trigger.TIME_RANGE else Trigger.DISTANCE_RANGE
                            val v = trigger.value.toFloat().coerceIn(range).toDouble()
                            onChange(Trigger(mode, if (mode == TriggerMode.TIME) v.roundToInt().toDouble() else v))
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(16.dp))

        AnimatedContent(
            targetState = trigger.mode,
            transitionSpec = {
                (fadeIn() + slideInVertically { it / 6 }) togetherWith (fadeOut() + slideOutVertically { -it / 6 })
            },
            label = "trigger",
        ) { mode ->
            Column {
                val isTime = mode == TriggerMode.TIME
                val range = if (isTime) Trigger.TIME_RANGE else Trigger.DISTANCE_RANGE
                val steps = if (isTime) 28 else 38
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedCounter(
                        text = if (isTime) "${trigger.value.roundToInt()}" else formatKm(trigger.value).substringBefore(" "),
                    )
                    Spacer(Modifier.padding(start = 6.dp))
                    Text(
                        if (isTime) "min antes de llegar"
                        else if (trigger.value < 1) "m antes de llegar" else "km antes de llegar",
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Slider(
                    value = trigger.value.toFloat().coerceIn(range),
                    onValueChange = { v ->
                        val snapped = if (isTime) v.roundToInt().toDouble() else (v * 2).roundToInt() / 2.0
                        if (snapped != trigger.value) onChange(trigger.copy(value = snapped))
                    },
                    valueRange = range,
                    steps = steps,
                )
                val presets = if (isTime) listOf(5.0, 8.0, 10.0, 15.0, 20.0) else listOf(1.0, 2.0, 3.0, 5.0, 8.0)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { p ->
                        FilterChip(
                            selected = trigger.value == p,
                            onClick = { onChange(trigger.copy(value = p)) },
                            label = { Text(if (isTime) "${p.roundToInt()} min" else formatKm(p)) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isTime) {
                        "Recomendado: 8–10 min. La app aprende la velocidad real del bus durante el viaje, " +
                            "así el aviso se ajusta al tráfico."
                    } else {
                        val min = trigger.value.roundToInt().coerceAtLeast(1)
                        "≈ $min min antes a 60 km/h. Recomendado para bus intermunicipal: 5–8 km."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

/** Número grande que se desliza al cambiar. */
@Composable
fun AnimatedCounter(text: String, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeIn()) togetherWith
                (slideOutVertically { -it / 2 } + fadeOut())
        },
        label = "counter",
        modifier = modifier,
    ) { t ->
        Text(t, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Light)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}
