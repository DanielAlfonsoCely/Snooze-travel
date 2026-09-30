package com.snoozetravel.app.data

import android.content.Context
import android.content.SharedPreferences
import com.snoozetravel.app.widget.TripWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistencia ligera con SharedPreferences + JSON (sin base de datos: son pocos datos).
 * Expone StateFlows para que la UI reaccione a cambios.
 */
object Store {
    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences

    private val _destinations = MutableStateFlow<List<Destination>>(emptyList())
    val destinations: StateFlow<List<Destination>> = _destinations.asStateFlow()

    private val _settings = MutableStateFlow(AlarmSettings())
    val settings: StateFlow<AlarmSettings> = _settings.asStateFlow()

    private val _theme = MutableStateFlow(ThemeMode.SYSTEM)
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("snooze", Context.MODE_PRIVATE)
        _destinations.value = readDestinations()
        _settings.value = readSettings()
        _theme.value = enumOr(prefs.getString("theme", null), ThemeMode.SYSTEM)
    }

    // ---------- Destinos ----------

    fun destination(id: String): Destination? = _destinations.value.firstOrNull { it.id == id }

    fun saveDestination(d: Destination) {
        val list = _destinations.value.toMutableList()
        val i = list.indexOfFirst { it.id == d.id }
        if (i >= 0) list[i] = d else list.add(d)
        _destinations.value = list
        writeDestinations(list)
    }

    fun deleteDestination(id: String) {
        val list = _destinations.value.filterNot { it.id == id }
        _destinations.value = list
        writeDestinations(list)
        if (lastDestinationId == id) lastDestinationId = null
    }

    var lastDestinationId: String?
        get() = prefs.getString("last_dest", null)
        set(value) {
            prefs.edit().putString("last_dest", value).apply()
            TripWidgetProvider.update(appContext)
        }

    /** Destino que usa el widget: el último activado, o el primero de la lista. */
    fun lastDestination(): Destination? =
        lastDestinationId?.let(::destination) ?: _destinations.value.firstOrNull()

    private fun writeDestinations(list: List<Destination>) {
        val arr = JSONArray()
        list.forEach { d ->
            arr.put(
                JSONObject()
                    .put("id", d.id).put("name", d.name).put("address", d.address)
                    .put("lat", d.lat).put("lon", d.lon)
                    .put("mode", d.trigger.mode.name).put("value", d.trigger.value)
            )
        }
        prefs.edit().putString("destinations", arr.toString()).apply()
        TripWidgetProvider.update(appContext)
    }

    private fun readDestinations(): List<Destination> {
        val raw = prefs.getString("destinations", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Destination(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    address = o.optString("address"),
                    lat = o.getDouble("lat"),
                    lon = o.getDouble("lon"),
                    trigger = Trigger(
                        enumOr(o.optString("mode"), TriggerMode.TIME),
                        o.optDouble("value", Trigger.DEFAULT.value),
                    ),
                )
            }
        }.getOrDefault(emptyList())
    }

    // ---------- Ajustes ----------

    fun updateSettings(transform: (AlarmSettings) -> AlarmSettings) {
        val s = transform(_settings.value)
        _settings.value = s
        prefs.edit()
            .putString("pattern", s.pattern.name)
            .putBoolean("sound", s.soundEnabled)
            .putInt("sound_delay", s.soundDelaySec)
            .putString("ringtone_uri", s.ringtoneUri)
            .putString("ringtone_title", s.ringtoneTitle)
            .putBoolean("ramp", s.rampVolume)
            .putBoolean("max_volume", s.maxVolume)
            .putBoolean("pre_enabled", s.preAlertEnabled)
            .putFloat("pre_km", s.preOffsetKm.toFloat())
            .putFloat("pre_min", s.preOffsetMin.toFloat())
            .putBoolean("headphones_only", s.headphonesOnly)
            .putBoolean("guard", s.guardEnabled)
            .apply()
    }

    private fun readSettings(): AlarmSettings {
        val d = AlarmSettings()
        return AlarmSettings(
            pattern = enumOr(prefs.getString("pattern", null), d.pattern),
            soundEnabled = prefs.getBoolean("sound", d.soundEnabled),
            soundDelaySec = prefs.getInt("sound_delay", d.soundDelaySec),
            ringtoneUri = prefs.getString("ringtone_uri", null),
            ringtoneTitle = prefs.getString("ringtone_title", null),
            rampVolume = prefs.getBoolean("ramp", d.rampVolume),
            maxVolume = prefs.getBoolean("max_volume", d.maxVolume),
            preAlertEnabled = prefs.getBoolean("pre_enabled", d.preAlertEnabled),
            preOffsetKm = prefs.getFloat("pre_km", d.preOffsetKm.toFloat()).toDouble(),
            preOffsetMin = prefs.getFloat("pre_min", d.preOffsetMin.toFloat()).toDouble(),
            headphonesOnly = prefs.getBoolean("headphones_only", d.headphonesOnly),
            guardEnabled = prefs.getBoolean("guard", d.guardEnabled),
        )
    }

    // ---------- Tema ----------

    fun setTheme(mode: ThemeMode) {
        _theme.value = mode
        prefs.edit().putString("theme", mode.name).apply()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        runCatching { enumValueOf<E>(name!!) }.getOrDefault(default)
}
