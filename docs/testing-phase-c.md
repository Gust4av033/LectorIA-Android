# Voz local — validación y prueba en teléfono

**24/11/2026, actualización:** el usuario confirmó que la voz ya se escucha tras
0.4.2 (USER_REPORTED). No equivale a instrumentación ejecutada por el agente ni
acredita Bluetooth/pantalla apagada. Extensión siguiente: `testing-reader-sync.md`.

Rama `feature/fase-c-voz-local`, versión 0.4.0. El usuario confirmó el funcionamiento
de la lectura EPUB de Fase B. Esa confirmación es USER_REPORTED y no acredita aún
TTS, Bluetooth, llamadas ni PDFs nativos.

## Alcance

Android TextToSpeech real, voces instaladas que el motor declara sin red, elección
de motor/voz/velocidad, escucha desde el lector y reproductor. El servicio foreground
usa MediaSession de Android para controles multimedia y notificación. No se generan
archivos de audio; no hay permisos Internet ni integración cloud.

Pausa al perder audio focus (incluido ducking) y al recibir AUDIO_BECOMING_NOISY.
No reanuda al recuperar foco ni reconectar auriculares: requiere acción del usuario.
El servicio no se reinicia automáticamente tras morir el proceso.

El avance de escucha es independiente del de lectura. Se guarda al iniciar/terminar
fragmentos, al pausar y hasta una vez por segundo al recibir marcación de palabras.
Si el motor no ofrece onRangeStart, se repite el fragmento actual al reanudar
(máximo 1600 caracteres). Incluso con callbacks puede repetirse la última palabra;
no hay seek por milisegundos. Room 2→3 añade solo narration_progress; 1→2→3 también
se admite. Los libros, archivos y progreso de lectura permanecen intactos.

## Comprobaciones automáticas

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --console=plain
```

Código final `b814e93`: **PASS** local con JDK 21/SDK 36. El comando completo
terminó en 1 min 32 s, 95 tareas (25 ejecutadas, 70 up-to-date): assembleDebug,
testDebugUnitTest, lintDebug y assembleDebugAndroidTest. **73 pruebas**, 0 fallos,
errores u omitidas. Incluyen 16 del coordinador, 2 del puente Android y 1 de
migración v2→v3, además de regresión y traducción de offsets en los tres formatos.

El primer intento detectó índices opcionales ausentes en el fixture del esquema
Room y una advertencia de permiso de notificación. Se corrigió la reconstrucción
del fixture; la excepción multimedia se documenta con una supresión localizada
NotificationPermission únicamente para notificaciones MediaStyle con token de
MediaSession. No se ocultan otros errores de lint ni se introduce un baseline.
La firma debug del APK también se verificó. Los dispositivos físicos permanecen
NOT_RUN; la compilación del test no equivale a escuchar una voz.

Los tests del coordinador usan motores/focus sintéticos solo en test para comprobar
orden, cancelación, voces de red rechazadas, callbacks atrasados, avance, pausa,
fallos de guardado y ausencia de texto. El adaptador Android tiene pruebas de su
puente de callbacks y errores inmediatos. La migración reconstruye el esquema v2
exportado y verifica independencia/cascade de los progresos. Los lectores traducen
offsets de habla a locators reales de EPUB/TXT/PDF.

## En Android Studio, sin terminal

1. Git → Fetch → origin/feature/fase-c-voz-local → Checkout. Conservar JDK 21.
   Sync y Run sobre la instalación existente para conservar libros y progreso.
2. Abrir el EPUB y tocar **Escuchar desde aquí**. En Reproductor elegir una voz
   instalada en español. Si falta, **Configurar voces en Android**, descargar su
   paquete de voz desde el sistema y volver a **Actualizar voces**.
3. Probar reproducción, pausa y reanudación; anterior/siguiente cambian fragmentos.
   Cambiar voz o velocidad pausa y espera Reproducir. «Abrir esta parte en el
   lector» lleva el avance de escucha a lectura mediante acción explícita.
4. Activar modo avión, reproducir varios minutos y apagar la pantalla. Verificar
   continuidad, notificación y controles de pausa/reanudación.
5. Probar botones del auricular/Bluetooth con una sesión activa. Desconectarlo:
   debe pausar; reconectarlo no inicia voz automáticamente. Probar llamada y otra
   aplicación de audio: la lectura debe ceder el audio y esperar reanudación.
6. Pausar, forzar detención en Ajustes de Android, abrir la app e ir al Reproductor.
   Debe conservar el avance y esperar Reproducir. Confirmar la tolerancia de palabra
   o fragmento indicada arriba, según el motor instalado.
7. Probar 30–60 minutos, batería/modo ahorro y Samsung Android 16; anotar cortes,
   cambios de ruta o voces que el motor anuncie offline pero no funcionen en avión.
8. Si se dispone de PDF textual, probar escucha; un escaneado sin texto debe dar
   aviso y no fabricar narración. Verificar que leer otro libro no mueve la escucha.

## Pruebas conectadas — NOT_RUN por el agente

Sin teléfono/emulador accesible en este Linux cloud. Audio real, pantalla apagada,
foco durante llamada, Bluetooth, batería y límites del fabricante requieren el
teléfono del propietario. LocalVoiceTest, en androidTest, pronuncia una sola frase
sintética si existe una voz española offline; de lo contrario se omite con motivo.
Ejecutarlo con el motor configurado y después en modo avión. No usa libros privados.
Las pruebas conectadas de Fase B también siguen pendientes de ejecución del agente.

## Corrección 0.4.1 — reporte del teléfono

El usuario reportó error «El motor no pudo pronunciar este fragmento» con motores
Samsung y Google, además de controles confusos. Se registra como USER_REPORTED de
fallo de reproducción; no se considera validación física exitosa de Fase C.

Se eliminó el flag legacy de síntesis, se ajustó el orden de inicialización y se
acortaron las unidades enviadas a TTS conservando Unicode/posiciones. Se protege
foreground durante preparación y se mantienen códigos SDK de error. Probar voz
permite distinguir un problema del motor/salida de uno asociado al libro. Las
pruebas del puente usan una fábrica estricta que exige ready, comprueban errores
numéricos y múltiples callbacks/una sola finalización. La prueba del coordinador
cubre pausa durante lookup y que la muestra no mueva el avance del libro.

Nueva UI: tarjeta de libro, botón central Escuchar/Pausar con icono, saltos con
nombre, voz en idioma/país, velocidad separada y ayuda/texto desplegables. Dos
pruebas de Compose verifican acciones/etiquetas y selección sin IDs técnicos.
Captura sintética revisada: app/build/reports/screenshots/player.png, sin versionar.

Validación local final 0.4.1: **PASS**, 80 pruebas, 0 fallos/errores/omitidas.
assembleDebug, testDebugUnitTest, lintDebug y assembleDebugAndroidTest: PASS,
1 min 17 s, 95 tareas; lint 0 errores y 16 avisos. Captura revisada con texto claro
y controles legibles. Después de actualizar desde Android
Studio, usar primero Probar voz y luego Escuchar en el EPUB. Si aún falla, copiar
el código específico que ahora devuelve el motor. La voz real sigue NOT_RUN por
el agente; no hay acceso al USB/Android Studio del teléfono del propietario.

## 0.4.2 — servicio TTS y prueba del propio Android

El usuario confirma que Probar voz también falla sin libro, mientras que el ejemplo
del sistema Android sí se escucha. El fallo queda acotado a la integración de la
app; no es evidencia de un EPUB defectuoso. Esto sigue siendo USER_REPORTED.

Se corrige el uso de QUEUE_FLUSH que podía descartar la carga encolada de la voz.
Un test llama al adaptador Android y verifica QUEUE_ADD en el SDK mediante
Robolectric. Se añaden pruebas de recuperación única antes de inicio, no repetición
tras inicio, cancelación durante reconexión, callbacks viejos, binder inválido,
timeout y preferencia por la voz española offline predeterminada de Android.
También se verifican recuperación manual sin autoplay/avance y diagnóstico sin
contenido privado. La UI distingue espera de inicio confirmado y ofrece Reconectar.

Validación local final: **94 pruebas, 0 fallos/errores/omitidas**. assembleDebug,
testDebugUnitTest, lintDebug y assembleDebugAndroidTest: **PASS**, 1m47s, 95 tareas.
Lint: 0 errores y 16 avisos. Sin cambios de esquema/datos ni librerías de runtime.
No hay dispositivo conectado al agente: la corrección no se declara verificada
en hardware ni se atribuye voz audible a los tests JVM.

Actualizar la misma instalación desde Android Studio (rama feature/fase-c-voz-local,
Pull, Sync y Run), comprobar 0.4.2 y usar Probar voz. Después probar el EPUB.
Si continúa el fallo: Reconectar voz; Usar voz de Android si se ofrece; Probar voz.
Copiar diagnóstico permite informar del error sin compartir el libro. Repetir luego
modo avión/pantalla apagada/Bluetooth cuando la muestra ya sea audible.
