package com.snoozetravel.app.alarm

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snoozetravel.app.R
import com.snoozetravel.app.data.Store
import com.snoozetravel.app.trip.TripService
import com.snoozetravel.app.trip.TripState
import com.snoozetravel.app.trip.TripStatus
import com.snoozetravel.app.ui.theme.SnoozeTheme
import com.snoozetravel.app.ui.theme.isDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pantalla completa sobre el bloqueo. Para apagar hay que MANTENER presionado
 * (evita apagarla dormido o por accidente en el bolsillo).
 */
class AlarmActivity : ComponentActivity() {
    private var testPlayer: AlarmPlayer? = null
    private val isTest by lazy { intent.getBooleanExtra(EXTRA_TEST, false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge()

        if (isTest) testPlayer = AlarmPlayer(this).also { it.start(Store.settings.value) }

        setContent {
            val theme by Store.theme.collectAsStateWithLifecycle()
            val status by TripState.status.collectAsStateWithLifecycle()
            val name = (status as? TripStatus.Alarming)?.destinationName ?: if (isTest) "Prueba" else "tu destino"
            val again = (status as? TripStatus.Alarming)?.again == true

            if (!isTest) LaunchedEffect(status) { if (status !is TripStatus.Alarming) finish() }
            if (isTest) LaunchedEffect(Unit) { delay(TEST_DURATION_MS); dismiss() }

            SnoozeTheme(isDark(theme)) {
                BackHandler { /* bloqueado: hay que mantener presionado */ }
                AlarmScreen(name = name, test = isTest, again = again, onDismiss = ::dismiss)
            }
        }
    }

    private fun dismiss() {
        if (isTest) {
            testPlayer?.stop()
            testPlayer = null
        } else {
            TripService.dismiss(this)
        }
        finish()
    }

    override fun onDestroy() {
        testPlayer?.stop()
        super.onDestroy()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        private const val EXTRA_TEST = "test"
        private const val TEST_DURATION_MS = 12_000L

        fun test(ctx: Context) {
            ctx.startActivity(Intent(ctx, AlarmActivity::class.java).putExtra(EXTRA_TEST, true))
        }
    }
}

@Composable
private fun AlarmScreen(name: String, test: Boolean, again: Boolean, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(
        0.85f, 1.15f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "ring",
    )
    val wave by pulse.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "wave",
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(cs.background)
            .systemBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Text(
                when {
                    test -> "Prueba de alarma"
                    again -> "¡Te estás pasando de"
                    else -> "Estás llegando"
                },
                style = MaterialTheme.typography.titleMedium,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                name,
                style = MaterialTheme.typography.displaySmall,
                color = cs.onBackground,
                textAlign = TextAlign.Center,
            )
        }

        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                drawCircle(cs.primary.copy(alpha = (1f - wave) * 0.35f), radius = r * (0.45f + 0.55f * wave))
            }
            Box(
                Modifier
                    .size(140.dp)
                    .scale(ring)
                    .clip(CircleShape)
                    .background(cs.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_moon), null,
                    tint = cs.onPrimaryContainer, modifier = Modifier.size(56.dp),
                )
            }
        }

        HoldToDismiss(onDismiss)
    }
}

@Composable
private fun HoldToDismiss(onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(112.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        val job = scope.launch {
                            progress.animateTo(1f, tween(1200, easing = LinearEasing))
                            onDismiss()
                        }
                        tryAwaitRelease()
                        if (progress.value < 1f) {
                            job.cancel()
                            scope.launch { progress.animateTo(0f, tween(250)) }
                        }
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(cs.outlineVariant, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(
                    cs.primary, -90f, 360f * progress.value, false, Offset(inset, inset), arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Box(
                Modifier
                    .size(84.dp)
                    .scale(1f - 0.08f * progress.value)
                    .clip(CircleShape)
                    .background(cs.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text("Apagar", color = cs.onPrimary, style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Mantén presionado para apagar",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}
