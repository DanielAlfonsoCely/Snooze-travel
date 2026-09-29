package com.snoozetravel.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.snoozetravel.app.data.Destination
import com.snoozetravel.app.data.Store
import com.snoozetravel.app.data.Trigger
import com.snoozetravel.app.search.MapsLinkResolver
import com.snoozetravel.app.search.Place
import com.snoozetravel.app.search.PlaceSearch
import com.snoozetravel.app.util.Loc
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class EditorViewModel(app: Application) : AndroidViewModel(app) {
    var editingId by mutableStateOf<String?>(null); private set
    var query by mutableStateOf("")
    var results by mutableStateOf<List<Place>>(emptyList()); private set
    var searching by mutableStateOf(false); private set
    var selected by mutableStateOf<Place?>(null); private set
    var name by mutableStateOf("")
    var trigger by mutableStateOf(Trigger.DEFAULT)
    var message by mutableStateOf<String?>(null)
    var bias by mutableStateOf<Pair<Double, Double>?>(null); private set

    private var nameTouched = false
    private var searchJob: Job? = null
    private var reverseJob: Job? = null

    fun load(d: Destination?) {
        searchJob?.cancel()
        editingId = d?.id
        query = ""
        results = emptyList()
        searching = false
        selected = d?.let { Place(it.name, it.address, it.lat, it.lon) }
        name = d?.name.orEmpty()
        nameTouched = d != null
        trigger = d?.trigger ?: Trigger.DEFAULT
        message = null
        viewModelScope.launch {
            // Ubicación aproximada sin encender el GPS, para centrar el mapa y priorizar resultados cercanos.
            Loc.last(getApplication())?.let { bias = it.latitude to it.longitude }
        }
    }

    fun onQueryChange(q: String) {
        query = q
        searchJob?.cancel()
        if (q.trim().length < 3) {
            results = emptyList()
            searching = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(450) // debounce: no buscar en cada tecla
            searching = true
            results = PlaceSearch.search(getApplication(), q.trim(), bias?.first, bias?.second)
            searching = false
            if (results.isEmpty()) message = "Sin resultados. Prueba con otra dirección o nombre."
        }
    }

    fun select(p: Place) {
        searchJob?.cancel()
        searching = false
        selected = p
        results = emptyList()
        query = ""
        if (!nameTouched) name = p.name
    }

    fun onNameChange(n: String) {
        name = n
        nameTouched = true
    }

    /** Ajuste fino manteniendo presionado el mapa. */
    fun adjust(lat: Double, lon: Double) {
        val prev = selected
        selected = Place(prev?.name ?: "Punto elegido", prev?.address.orEmpty(), lat, lon)
        if (!nameTouched && prev == null) name = "Punto elegido"
        reverseJob?.cancel()
        reverseJob = viewModelScope.launch {
            val addr = PlaceSearch.reverse(getApplication(), lat, lon) ?: return@launch
            selected = selected?.copy(address = addr)
        }
    }

    fun useCurrentLocation() {
        viewModelScope.launch {
            searching = true
            val loc = Loc.current(getApplication())
            searching = false
            if (loc == null) {
                message = "No pude obtener tu ubicación. Revisa el permiso y el GPS."
                return@launch
            }
            select(Place("Mi ubicación", "", loc.latitude, loc.longitude))
            adjust(loc.latitude, loc.longitude)
        }
    }

    fun handleShared(text: String) {
        viewModelScope.launch {
            searching = true
            val p = runCatching { MapsLinkResolver.resolve(getApplication(), text) }.getOrNull()
            searching = false
            if (p != null) select(p) else message = "No pude leer ese lugar. Intenta buscarlo aquí."
        }
    }

    fun save(): Boolean {
        val p = selected ?: return false
        Store.saveDestination(
            Destination(
                id = editingId ?: UUID.randomUUID().toString(),
                name = name.trim().ifBlank { p.name },
                address = p.address,
                lat = p.lat,
                lon = p.lon,
                trigger = trigger,
            )
        )
        return true
    }

    fun delete() {
        editingId?.let(Store::deleteDestination)
    }
}
