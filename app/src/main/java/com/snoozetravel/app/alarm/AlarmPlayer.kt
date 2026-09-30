package com.snoozetravel.app.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Alarma escalonada: vibra fuerte de inmediato y, si está configurado, después de
 * [AlarmSettings.soundDelaySec] empieza a sonar (con volumen que sube gradualmente).
 *
 * - Sin audífonos: canal de ALARMA por el parlante (suena en silencio / no molestar).
 * - Con audífonos y [AlarmSettings.headphonesOnly]: canal multimedia enrutado solo a los
 *   audífonos, a volumen moderado. Si se desconectan, pasa al parlante; si se conectan, a ellos.
 */
class AlarmPlayer(private val ctx: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var settings: AlarmSettings? = null
    private var soundStarted = false
    private var onHeadphones = false
    private val savedVolumes = mutableMapOf<Int, Int>()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
            val s = settings ?: return
            if (soundStarted && !onHeadphones && s.headphonesOnly && added.any { it.isHeadphone() }) restartSound(0.3f)
        }

        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            // Se quitaron los audífonos: seguir por el parlante, ya con volumen alto.
            if (soundStarted && onHeadphones && headphone() == null) restartSound(0.6f)
        }
    }

    fun start(s: AlarmSettings) {
        settings = s
        Vibes.vibrate(ctx, s.pattern, repeat = true)
        if (s.soundEnabled) {
            audio.registerAudioDeviceCallback(deviceCallback, handler)
            handler.postDelayed({ soundStarted = true; startSound(0.1f) }, s.soundDelaySec * 1000L)
        }
    }

    private fun restartSound(startVolume: Float) {
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
        restoreVolumes()
        startSound(startVolume)
    }

    private fun startSound(startVolume: Float) {
        val s = settings ?: return
        val hp = if (s.headphonesOnly) headphone() else null
        onHeadphones = hp != null
        val stream = if (hp != null) AudioManager.STREAM_MUSIC else AudioManager.STREAM_ALARM

        runCatching {
            val maxIdx = audio.getStreamMaxVolume(stream)
            val target = when {
                hp != null -> (maxIdx * HEADPHONE_LEVEL).roundToInt() // protege los oídos
                s.maxVolume -> maxIdx
                else -> null
            }
            if (target != null) {
                val current = audio.getStreamVolume(stream)
                savedVolumes.putIfAbsent(stream, current)
                // En audífonos no bajamos si ya estaba más alto; tampoco subimos a tope.
                audio.setStreamVolume(stream, if (hp != null) max(current, target) else target, 0)
            }
        }

        val attrs = AudioAttributes.Builder()
            .setUsage(if (hp != null) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_ALARM)
            .setContentType(if (hp != null) AudioAttributes.CONTENT_TYPE_MUSIC else AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val candidates = listOfNotNull(
            s.ringtoneUri?.let(Uri::parse),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
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
        if (hp != null && Build.VERSION.SDK_INT >= 28) mp.setPreferredDevice(hp)
        player = mp

        if (s.rampVolume) {
            var volume = startVolume
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

    private fun headphone(): AudioDeviceInfo? =
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.isHeadphone() }

    private fun AudioDeviceInfo.isHeadphone(): Boolean = isSink && type in HEADPHONE_TYPES

    private fun releasePlayer() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    private fun restoreVolumes() {
        savedVolumes.forEach { (stream, v) -> runCatching { audio.setStreamVolume(stream, v, 0) } }
        savedVolumes.clear()
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        runCatching { audio.unregisterAudioDeviceCallback(deviceCallback) }
        Vibes.cancel(ctx)
        releasePlayer()
        restoreVolumes()
        soundStarted = false
        settings = null
    }

    companion object {
        private const val HEADPHONE_LEVEL = 0.6f

        private val HEADPHONE_TYPES = buildSet {
            add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_USB_HEADSET)
            if (Build.VERSION.SDK_INT >= 31) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
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
