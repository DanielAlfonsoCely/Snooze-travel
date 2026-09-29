package com.snoozetravel.app.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.snoozetravel.app.data.AlarmSettings
import com.snoozetravel.app.data.VibePattern
import kotlin.math.min

/**
 * Alarma escalonada: vibra fuerte de inmediato y, si está configurado, después de
 * [AlarmSettings.soundDelaySec] empieza a sonar (con volumen que sube gradualmente).
 * Usa el canal de ALARMA para funcionar con el celular en silencio / no molestar.
 */
class AlarmPlayer(private val ctx: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var savedVolume: Int? = null

    fun start(s: AlarmSettings) {
        Vibes.vibrate(ctx, s.pattern, repeat = true)
        if (s.soundEnabled) handler.postDelayed({ startSound(s) }, s.soundDelaySec * 1000L)
    }

    private fun startSound(s: AlarmSettings) {
        if (s.maxVolume) runCatching {
            savedVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        }
        val candidates = listOfNotNull(
            s.ringtoneUri?.let(Uri::parse),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val mp = candidates.firstNotNullOfOrNull { uri ->
            runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    setDataSource(ctx, uri)
                    isLooping = true
                    prepare()
                }
            }.getOrNull()
        } ?: return
        player = mp

        if (s.rampVolume) {
            var volume = 0.1f
            mp.setVolume(volume, volume)
            mp.start()
            handler.postDelayed(object : Runnable {
                override fun run() {
                    val p = player ?: return
                    volume = min(1f, volume + 0.05f)
                    p.setVolume(volume, volume)
                    if (volume < 1f) handler.postDelayed(this, 1000)
                }
            }, 1000)
        } else {
            mp.setVolume(1f, 1f)
            mp.start()
        }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        Vibes.cancel(ctx)
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        savedVolume?.let { runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, it, 0) } }
        savedVolume = null
    }
}

object Vibes {
    private fun vibrator(ctx: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)

    fun vibrate(ctx: Context, pattern: VibePattern, repeat: Boolean) =
        play(ctx, pattern.timings, pattern.amplitudes, if (repeat) 0 else -1)

    /** Vibración corta del aviso previo. */
    fun pulse(ctx: Context) = play(
        ctx, longArrayOf(0, 350, 150, 350, 150, 350), intArrayOf(0, 255, 0, 255, 0, 255), -1,
    )

    fun cancel(ctx: Context) = vibrator(ctx).cancel()

    private fun play(ctx: Context, timings: LongArray, amplitudes: IntArray, repeat: Int) {
        val v = vibrator(ctx)
        if (!v.hasVibrator()) return
        val effect = if (v.hasAmplitudeControl()) VibrationEffect.createWaveform(timings, amplitudes, repeat)
        else VibrationEffect.createWaveform(timings, repeat)
        if (Build.VERSION.SDK_INT >= 33) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }
}
