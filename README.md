# Lector IA — Kit de inicio

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
