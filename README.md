# Lector IA — Kit de inicio

> **Actualización Fase C, 0.4.0:** voz local y reproductor en validación; pasos al final y en `docs/testing-phase-c.md`.

> **Historial, Fase B (0.3.0):** lector EPUB/TXT en modo texto, PDF visual y
> extracción textual, secciones y avance persistente por libro. Instrucciones
> actuales al final; las secciones de Fase 0/A se conservan como historial.

> **Actualización de implementación (23/11/2026):** el texto de arranque de abajo
> se conserva como contexto histórico. El repositorio ya contiene la base Android
> de Fase 0. Consulta las instrucciones actuales y la evidencia al final de este archivo.

> **Fase A:** la rama `feature/fase-a-biblioteca` añade importación y biblioteca
> persistente. El arranque de Fase 0 en teléfono fue confirmado por el usuario.
> Los resultados de esta nueva entrega están en `docs/testing-phase-a.md`.

Este paquete contiene la **documentación de arranque**, no el código de la aplicación.

## Archivos

- `docs/MASTER_PLAN.md`: documento maestro de arquitectura, alcance, fases y criterios de aceptación.
- `PROMPT_INICIAL_CODEX.md`: prompt para iniciar **Fase 0** en Codex.
- `AGENTS.md`: reglas operativas para agentes de código.

## Empezar

1. Crear o abrir un repositorio Git llamado `LectorIA-Android`.
2. Copiar este contenido manteniendo las rutas.
3. Abrir el repositorio en Codex (o en Android Studio con Codex).
4. Copiar el prompt de `PROMPT_INICIAL_CODEX.md` y enviarlo.
5. Revisar el resultado y los logs de compilación antes de avanzar a Fase A.

> Documentación creada el 23 de noviembre de 2026. No contiene una app implementada ni valida por sí misma ninguna compilación.

## Proyecto Android — Fase 0

Base nativa `com.lectoria.app`, Kotlin, Compose Material 3, Hilt y navegación entre
Biblioteca, Lector, Reproductor y Ajustes. La biblioteca está vacía: no hay importación,
parsers, voces ni servicios cloud conectados. Las pantallas pendientes lo indican.

### Abrir y ejecutar en Android Studio

1. Abrir **la raíz `LectorIA-Android`**, donde está `settings.gradle.kts`; no crear
   otro proyecto ni seleccionar solo `app`.
2. Usar una versión estable de Android Studio compatible con **AGP 8.13.2**.
3. En **Settings → Build, Execution, Deployment → Build Tools → Gradle**, seleccionar
   **Gradle Wrapper** y un **Gradle JDK 21**. Esta excepción al JDK 17 inicial es
   necesaria para las pruebas Robolectric de Android 16/API 36. El proyecto sigue
   compilando bytecode Java/Kotlin 17; no cambia el mínimo Android API 26.
4. En **SDK Manager**, instalar **Android SDK Platform 36**, **Build-Tools 36.0.0**
   y **Android SDK Platform-Tools**. Aceptar las licencias del SDK.
5. Ejecutar **File → Sync Project with Gradle Files**. Android Studio crea
   `local.properties` con la ruta local del SDK; este archivo se excluye de Git.
6. Seleccionar la configuración **app** y un dispositivo API 26 o superior.
   Para un emulador, crear un AVD en **Device Manager**; se recomienda API 36 para
   la primera comprobación. En un teléfono habilitar depuración USB y autorizar el equipo.
7. Pulsar **Run**. Debe aparecer Biblioteca; las cuatro pestañas permiten navegar.

### Compilar desde terminal

Con `JAVA_HOME` apuntando a JDK 21 y `ANDROID_HOME` al SDK:

```bash
./gradlew --version
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleDebugAndroidTest
# Solo con dispositivo/emulador conectado y autorizado:
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:installDebug
adb shell am start -n com.lectoria.app/.MainActivity
```

En Windows usar `gradlew.bat` en lugar de `./gradlew`. El APK de desarrollo queda
en `app/build/outputs/apk/debug/app-debug.apk` y usa la firma debug local.
No es un APK de publicación.

### Estructura implementada

```text
app/src/main/java/com/lectoria/app/
├── LectorIaApplication.kt / MainActivity.kt
├── core/designsystem/        # Paleta, tema y tipografía
├── presentation/             # Compose, navegación y LibraryViewModel
├── domain/                   # Modelos y contratos sin SDK Android
├── data/                     # Responsabilidad documentada; adaptadores pendientes
└── engine/                   # Documentos, narración y reproducción, aún sin motores
app/src/test/                 # Dominio y navegación con Robolectric
app/src/androidTest/          # Smoke test para dispositivo/emulador
gradle/                      # Catálogo de versiones y Wrapper con checksum
docs/                        # Plan original, decisiones, requisitos y pruebas
```

Versiones y decisiones: [architecture.md](docs/architecture.md).
Comandos, resultados y limitaciones: [testing.md](docs/testing.md).
Estado y siguiente tarea: [roadmap.md](docs/roadmap.md).

## Biblioteca e importación — Fase A

Versión 0.2.0. Importación mediante el selector Android de EPUB/PDF/TXT hasta 64 MiB,
copia privada, detección de duplicados por SHA-256 y persistencia Room. La biblioteca
permite buscar, ordenar (preferencia guardada en DataStore), consultar metadata y
eliminar la copia privada con confirmación. La lectura de páginas y la narración
siguen pendientes; tocar un libro abre sus detalles reales.

### Probar desde Android Studio y el teléfono

1. Actualizar el repositorio y cambiar a **`feature/fase-a-biblioteca`**. La Fase 0
   no debe recrearse. Consultar el PR para conocer su base y estado de integración.
2. Mantener **Gradle JDK 21**, **Gradle Wrapper** y **Gradle user home** en una
   carpeta del usuario (en Windows, `%USERPROFILE%\.gradle`). El JDK y la caché
   de Gradle son campos distintos. Java 25 no es compatible con este Wrapper.
3. Sincronizar, elegir **app**, seleccionar el celular y pulsar **Run ▶**.
4. En Biblioteca, tocar **Importar libro** y seleccionar un documento de prueba
   propio/sintético. Debe aparecer con título, formato y tamaño; EPUB puede incluir
   autor/portada y PDF una vista previa de su primera página.
5. Volver a seleccionar el mismo documento: debe informar que ya existe, sin
   agregar otra copia. Cancelar el selector no inicia una importación.
6. Cerrar y volver a abrir la app. Para probar un reinicio de proceso real, usar
   **Ajustes de Android → Aplicaciones → Lector IA → Forzar detención**, y abrirla
   de nuevo. Los libros y el orden seleccionado deben conservarse.
7. Tocar un libro, elegir **Eliminar libro** y primero cancelar: debe conservarse.
   Repetir confirmando: se elimina su copia de Lector IA; el archivo del proveedor
   seleccionado y los demás libros deben permanecer intactos.

Validación PDF nativa, selector real y pruebas físicas de esta fase: consultar el
estado exacto en [testing-phase-a.md](docs/testing-phase-a.md). Los resultados de
Fase 0 no sustituyen la comprobación de las funciones nuevas en el teléfono.

La base Room comienza en versión 1, con esquema exportado en `app/schemas` y sin
recreación destructiva automática. Las importaciones fallidas se muestran para
descartarlas con confirmación; las verificadas interrumpidas se intentan recuperar
al cargar la biblioteca. Los originales válidos no se barren automáticamente.

## Lectura y avance — Fase B

Rama **`feature/fase-b-lector`**, basada en Fase A mientras su PR siga abierto.
En Android Studio: **Git → Fetch**, selector de ramas → **Remote →
origin/feature/fase-b-lector → Checkout**; después Sync y **app → celular → Run ▶**.
Conserva JDK 21 y tu Gradle user home. Actualiza la instalación existente con la
misma firma debug de tu equipo para conservar datos; no desinstales la app.

Toca un libro y **Leer libro**. EPUB/TXT ofrecen texto seleccionable, A−/A+,
fragmento anterior/siguiente y **Secciones**. EPUB utiliza Readium 3.1.2 para
estructura, índice y extracción; este modo prioriza lectura textual y no reproduce
el diseño, imágenes, tablas complejas o interactividad del EPUB original.

PDF muestra su página nativa; **Ver texto** permite consultar la extracción real
de PDFBox Android. En la vista visual, anterior/siguiente cambian de página; en
texto recorren fragmentos. Una página sin texto avisa que puede necesitar OCR,
todavía fuera de alcance. No se genera texto ficticio a partir de una imagen.

El avance se guarda por libro al cambiar de fragmento/sección. Al volver a abrirlo,
o tocar la pestaña Lector, se restaura la ubicación. La granularidad es el inicio
del fragmento (hasta 1600 caracteres), no el píxel de desplazamiento. PDF guarda
página y carácter; EPUB guarda recurso y offset en un locator Readium. La migración
Room 1→2 conserva la biblioteca y añade progreso, sin recrear las tablas previas.
El lector verifica SHA-256 al abrir. No descarga ni extrae copias de capítulos al
disco: índice TXT y página/sección actual se mantienen en memoria.

Pruebas y pasos en teléfono: [testing-phase-b.md](docs/testing-phase-b.md).
Licencias incorporadas: [third-party.md](docs/third-party.md). Narración/TTS y el
reproductor permanecen para Fase C.

## Voz local — Fase C (0.4.0)

Actualizar a `feature/fase-c-voz-local` desde las ramas remotas de Android Studio,
sincronizar y Run conservando la instalación existente y JDK 21. Abrir el libro y
**Escuchar desde aquí**. Reproductor/Ajustes permiten escoger motor, voz instalada
sin red y velocidad. Si falta español, configurar/descargar voces desde Android y
actualizar la lista. No se genera audio en disco ni se añade una API de pago.

Pausa/reanudación, anterior/siguiente por fragmentos y controles multimedia están
conectados al servicio de voz. El avance de escucha se guarda separado de lectura.
Pasos de modo avión, pantalla apagada, Bluetooth y recuperación en
[testing-phase-c.md](docs/testing-phase-c.md). Estas pruebas físicas siguen pendientes.

### Actualización 0.4.1 del reproductor

En la misma rama `feature/fase-c-voz-local`, hacer Pull/Update Project, Sync y Run
sobre la instalación existente. **Probar voz** comprueba el motor sin leer el libro;
**Escuchar/Pausar** es el botón central; **Parte anterior/siguiente** recorren texto.
La selección de voz muestra idioma y país en un diálogo. El error de Android,
cuando está disponible, muestra un motivo concreto para orientar la recuperación.
Se conserva el libro y su avance; hace falta confirmar la voz en el teléfono.

### Corrección de voz 0.4.2

La voz se carga antes de encolar el habla, se recupera una vez un fallo de servicio
antes del inicio y se ofrece **Reconectar voz** sin perder avance. La pantalla
distingue conexión/espera de voz iniciada. Se prefiere la voz predeterminada de
Android cuando sea española y offline. Actualizar con Pull, Sync y Run en la rama
actual y probar la muestra antes del EPUB. **Copiar diagnóstico** excluye el libro.
Validación local: 94 pruebas y build PASS; sonido real pendiente de repetir.

### Lectura sincronizada y diseño 0.5.0

Actualización en `feature/lectura-sincronizada`: cambiar a esa rama en Android
Studio, actualizar proyecto, Sync y Run en el teléfono sin desinstalar.
Desde el reproductor, **Ver libro** mantiene la escucha y muestra un recuadro en el
texto narrado. **Siguiendo la voz** permite desactivarlo; navegar manualmente
suspende el seguimiento y **Volver a la voz** recupera la posición actual.
En PDF, la sincronización se muestra sobre el texto extraído.

En **Ajustes de voz → Interpretación**, elegir **Narrativa** o **Expresiva**, probar
la voz y continuar la escucha. Ajustan tono, ritmo y pausas; la calidad depende del
motor instalado. La app usa ahora un diseño oscuro neutro, con texto marfil y
superficies opacas. No se generan archivos de audio ni se añaden servicios cloud.
Validación y límites: [testing-reader-sync.md](docs/testing-reader-sync.md).

### Refinamiento UI y narración local — VS Code Copilot

La rama **`feature/ui-pro-vscode-copilot`** parte de la última entrega de UI/lector
sincronizado (`9a38e2a`). Refina la biblioteca y el reproductor con portadas,
tarjetas y controles centrados en la parte/sección real, sin inventar duración
para el TTS. La narración usa el locale de la voz instalada para separar frases y
modula pausas, puntuación, ritmo y tono de forma moderada.

En Voz de lectura puedes ver la calidad indicada por Android y abrir los ajustes
del sistema para descargar/mejorar voces sin conexión si tu motor las ofrece.
Esto no crea una voz nueva: la calidad real depende del teléfono y del paquete de
voz. La app no añade pagos, servicios cloud ni sube libros. Pruebas y limitaciones
actuales: [testing-reader-sync.md](docs/testing-reader-sync.md).

La app recomienda la voz española instalada/offline con mayor clasificación del
motor elegido y conserva una selección manual. No cambia de motor ni descarga
voces por su cuenta. En el Samsung SM-S906U, Google TTS y Samsung TTS pudieron
pronunciar la muestra sintética local; ambos reportaron quality 400. Puedes
compararlos desde **Cambiar voz → Probar voz** y elegir el que te resulte más
natural.
