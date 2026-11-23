# Arquitectura implementada y decisiones — Fase 0

Fecha: 23/11/2026. Fuente de requisitos: `MASTER_PLAN.md` v1.0, conservado sin cambios.

## ADR-001 — Un módulo y separación lógica

Se implementa solo `:app`. Las capas se organizan dentro de `com.lectoria.app`:

- `presentation`: Compose, navegación y `LibraryViewModel`, estado expuesto con
  `StateFlow` y recogido mediante `collectAsStateWithLifecycle`.
- `domain`: modelos de libro/progreso y contratos de repositorios, documentos,
  narración y reproducción. No importa Android, Compose, Hilt ni adaptadores.
- `data`: reservado para Room, DataStore y archivos privados en Fase A.
- `engine/document`, `engine/narration`, `engine/playback`: responsabilidades
  reservadas para adaptadores de framework, documentadas sin implementaciones falsas.
- `core/designsystem`: tema oscuro fijo, tokens tormenta medianoche y tipografía.

Hilt genera el contenedor de Application/Activity y crea el ViewModel real. Aún no
se agregan módulos DI vacíos ni repositorios simulados. La UI inicial no consume
casos de uso porque no hay operaciones de negocio implementadas; se incorporarán
al conectar la biblioteca persistente. El dominio define sus contratos por adelantado.

La navegación mantiene una pila acotada con `launchSingleTop`, `popUpTo`, guardado
y restauración de estado; Atrás desde una pantalla secundaria vuelve a Biblioteca.
`Scaffold` maneja insets y los contenidos usan listas desplazables para pantallas
pequeñas y tamaños de fuente del sistema. Iconos decorativos sin descripción
duplicada; las pestañas tienen etiquetas y semántica de selección.

## ADR-002 — Matriz estable y reproducible

| Componente | Versión efectiva |
|---|---|
| JDK de build y pruebas / bytecode | Temurin 21.0.9+10 / Java y Kotlin 17 |
| Gradle | 8.13, Wrapper oficial generado desde distribución verificada |
| Android Gradle Plugin | 8.13.2 |
| Kotlin / plugin Compose | 2.2.21 / 2.2.21 |
| KSP | 2.2.21-2.0.4 |
| Hilt | 2.57.2 |
| Compose BOM | 2025.11.01 |
| Activity Compose / Core KTX | 1.11.0 / 1.17.0 |
| Lifecycle | 2.9.4 |
| Navigation Compose | 2.9.5 |
| AndroidX Hilt Compose | 1.3.0 |
| Coroutines | 1.10.2 |
| JUnit / Robolectric | 4.13.2 / 4.16 |
| minSdk / compileSdk / targetSdk | 26 / 36 / 36 |
| SDK Build-Tools | 36.0.0, fijado explícitamente |

Se eligió una combinación conservadora estable, con versiones exactas en
`gradle/libs.versions.toml`; no se pretende usar automáticamente la última versión
de cada componente. Kotlin y su compilador Compose comparten versión. Hilt usa KSP,
sin KAPT. AGP 8.13 requiere Gradle 8.13 y JDK 17 como mínimo; la compilación es la comprobación
efectiva de compatibilidad de los componentes que realmente se enlazan.

El entorno traía JDK 21 y SDK 36. Se descargó y verificó JDK 17 en tooling externo
al repositorio y se generó el primer APK Debug con él. Sin embargo, **Robolectric
4.16/API 36 exige Java 21**: al ejecutar la prueba de UI con Java 17 se obtuvo
`Android SDK 36 requires Java 21 (have Java 17)`. Esta incompatibilidad comprobada
justifica la excepción solicitada: se usa JDK 21 para build y pruebas, manteniendo
`sourceCompatibility`, `targetCompatibility` y `jvmTarget` en 17 y `minSdk=26`.
La CI también usa JDK 21. El Wrapper incluye checksum de distribución verificado:
`20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`.

Referencias consultadas: metadata oficial de
[AGP](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml),
[Kotlin](https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml),
[KSP](https://repo.maven.apache.org/maven2/com/google/devtools/ksp/symbol-processing-gradle-plugin/maven-metadata.xml),
[Hilt](https://repo.maven.apache.org/maven2/com/google/dagger/hilt-android/maven-metadata.xml)
y [Compose BOM](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml).

## ADR-003 — Readium y PDF se validan antes de integrarlos

Se confirmó la existencia del artefacto **Readium 3.4.0** en Maven Central. Su
[POM publicado](https://repo.maven.apache.org/maven2/org/readium/kotlin-toolkit/readium-shared/3.4.0/readium-shared-3.4.0.pom)
declara **Kotlin 2.4.20** y Coroutines **1.11.0**. Por eso no es correcto afirmar
compatibilidad de esa versión con el compilador Kotlin 2.2.21 de Fase 0.

Readium no se fija como dependencia ni se integra en esta fase: no se necesita
para mostrar estados vacíos y ampliaría el alcance. Antes de Fase B, el spike debe
resolver conjuntamente Kotlin/AGP/KSP/Hilt/Readium, actualizar esta matriz y probar
un EPUB sintético real. El `minSdk=26` sigue el requerimiento explícito de la sesión,
superior al mínimo de referencia del documento maestro; no sustituye el spike.

La documentación web de developer.android.com y readium.org estuvo bloqueada por
el proxy del entorno (HTTP 403); se pudo consultar Maven. No se realizó apertura
EPUB ni extracción PDF. No hay evidencia que permita declarar esas funciones listas.
La elección del extractor PDF y sus licencias sigue abierta. `PdfRenderer` no se
presenta como extractor textual.

## ADR-004 — Contratos honestos para posiciones y voz

`ReadingLocator` conserva el formato: locator EPUB opaco, página PDF o carácter TXT.
`TextLocator` identifica sección y carácter del texto extraído; `MediaPositionMs`
identifica tiempo de un archivo de audio. Sus constructores rechazan posiciones
negativas y ubicaciones vacías. El adaptador EPUB deberá validar su JSON real.

`NarrationEngine` distingue `LiveSpeech` de `AudioFile` y no promete pause/seek por
milisegundos. Solo `AudioPlaybackController` tiene `seekTo(MediaPositionMs)`.
La coordinación, audio focus, motores y caché se implementarán en sus fases.

## ADR-005 — Privacidad y alcance visual

El manifiesto no solicita Internet, almacenamiento general ni permisos de audio.
No hay clientes cloud, credenciales ni contenido de muestra. El backup de la app
se desactiva para no enviar futuros documentos privados al backup del sistema sin
una política explícita: `allowBackup=false`, `fullBackupContent=false` y reglas que
excluyen los dominios de cloud backup y device transfer en Android 12+. Revisar
exportación/respaldo local en su fase de producto; no se ensayaron transferencias físicas.

El tema usa las cinco referencias de color, superficies Glass discretas y texto
claro sobre fondos oscuros. La importación no tiene un botón inerte; lector,
reproductor y ajustes muestran «Pendiente de desarrollo». La única interacción
disponible es la navegación que realmente existe.

## Diferencias documentales resueltas

El prompt original sugiere `feature/fase0-fundamentos`; la solicitud de esta sesión
pide `feature/fase-0-fundamentos`. Se aplica la segunda, partiendo del commit inicial
`3007588`, que coincide con `origin/main`, sin modificar `main`. Los documentos
originales de alcance permanecen intactos; los estados actuales viven en los nuevos
documentos de seguimiento y en la actualización añadida al README.
