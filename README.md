# Snooze Travel

Despertador por ubicación para cuando te quedas dormido en el bus: activas el viaje, eliges tu destino y el celular vibra fuerte (y opcionalmente suena) al acercarte.

## Instalar

1. Descarga `SnoozeTravel.apk` de la última [Release](../../releases/latest).
2. En el celular, ábrelo y permite "Instalar apps desconocidas" para tu navegador o gestor de archivos.
3. Al activar el primer viaje, la app pide permiso de ubicación y de notificaciones.

## Funciones

- **Se activa solo cuando lo necesitas.** Enciendes el despertador para un viaje y se apaga solo al llegar o si lo desactivas.
- **Varios destinos guardados** (casa, trabajo…). Los buscas por nombre o dirección, o los compartes desde Google Maps (Compartir → Snooze Travel).
- **Dos formas de elegir cuándo despertarte:**
  - **Por distancia:** de 0,5 a 20 km antes.
  - **Por tiempo:** de 1 a 30 min antes. El ETA se calcula con la velocidad real del bus, aprendida durante el viaje; funciona sin internet y se adapta al tráfico.
- **Alarma escalonada:** vibra a máxima intensidad (5 patrones) y, si no la apagas, empieza a sonar con volumen creciente. Usa el canal de alarma, así que suena aunque el celular esté en silencio.
- **Pantalla completa sobre el bloqueo.** Se apaga **manteniendo presionado** el botón, para no apagarla dormido.
- **Aviso previo opcional:** una vibración corta unos minutos o km antes de la alarma.
- **Widget** de un toque para activar o desactivar con el último destino.
- **Tema claro/oscuro**, con diseño minimalista y animado.

## Consumo de batería

- **Inactiva, la app no consume nada.** No hay servicios, alarmas ni trabajos en segundo plano.
- **Activa, la frecuencia se adapta a la distancia:** lejos del destino, una ubicación cada 1–3 min con WiFi y antenas (sin GPS); cerca, GPS cada 5–15 s. La siguiente lectura siempre se pide antes de que el bus pueda cruzar el umbral, aun a 108 km/h.
- **Reutiliza gratis** las ubicaciones que ya hayan pedido otras apps.
- **Al sonar la alarma** se apaga el GPS, y todo se detiene al apagarla.

## Servicios externos (gratis, sin API key)

- Mapa: [OpenStreetMap](https://www.openstreetmap.org/copyright) vía osmdroid.
- Búsqueda: Geocoder de Android (usa Google en equipos con Play Services) y [Photon](https://photon.komoot.io).

## Compilar

Requiere JDK 17 y Android SDK 35.

```bash
./gradlew assembleRelease
```

Sin `keystore.properties`, el APK se firma con la llave de debug. El CI firma con los secrets `SNOOZE_KEYSTORE_B64`, `SNOOZE_KEYSTORE_PASSWORD`, `SNOOZE_KEY_ALIAS` y `SNOOZE_KEY_PASSWORD`. Si publicas un tag `v*`, también crea una Release con el APK.
