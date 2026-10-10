# PobrePlay v0.1

App Android y Android TV con Kotlin, Jetpack Compose y WebView. Proyecto de prueba sin backend.

## Descargar la APK
1. Abrir https://github.com/nikoG4/PobrePlay/actions
2. Seleccionar **Compilar APK Android** y esperar una ejecucion verde.
3. Abrir la ejecucion y descargar el artefacto **PobrePlay-APK-debug**.
4. Descomprimir e instalar **app-debug.apk** en el celular.

Cada actualizacion en main ejecuta la compilacion. Se puede lanzar manualmente con Run workflow.

## Funciones
- Fuentes guardadas localmente; agregar URL HTTPS o HTML iframe.
- Demo publica y enlace de prueba suministrado durante el desarrollo.
- Reproductor WebView a pantalla completa, recarga, volver, ocultar controles.
- Vista TV horizontal para probar en celular y compatibilidad inicial con control remoto.
- Bloqueo basico de ventanas emergentes.
- No incluye scraping automatico ni metodos para eludir DRM.

Algunos sitios requieren cookies, autenticacion, referencias, o derechos de acceso. Usar solamente fuentes autorizadas. No hay garantia de que un sitio externo funcione.

## Desarrollo
Requiere Java 17, Android SDK 35, Gradle 8.9. Ejecutar: gradle :app:assembleDebug.
