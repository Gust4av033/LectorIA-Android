# Voz local — validación y prueba en teléfono

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

Resultados finales: en validación. La prueba inicial del coordinador y lectores
pasó antes de conectar UI/servicio. Actualizar este apartado con el árbol final.

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
