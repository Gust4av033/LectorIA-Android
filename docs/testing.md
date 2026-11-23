# Validación de Fase 0 — 23/11/2026

> Este documento conserva la evidencia de Fase 0. Para las nuevas funciones de
> biblioteca/importación consultar [testing-phase-a.md](testing-phase-a.md).
> Posteriormente el usuario confirmó Sync y arranque desde Android Studio en su
> celular (**USER_REPORTED**). Las pruebas conectadas no fueron ejecutadas por el agente.

Base implementada y validada mediante CLI y pruebas locales. **No se declara DONE
la validación manual en dispositivo.** La siguiente tabla contiene resultados
ejecutados, no estimaciones.

## Entorno inspeccionado

- Repositorio inicial: solo cuatro documentos, commit `3007588`, Git limpio.
- Rama de trabajo: `feature/fase-0-fundamentos`.
- SDK disponible: API 36, Build-Tools 36.0.0 y Platform-Tools 37.0.3.
- JDK inicial: 21. Se preparó Temurin 17.0.17+10 externo al repo con SHA verificado.
  El primer APK compiló con 17; la prueba Robolectric/API 36 exigió Java 21. Se
  usa Temurin 21.0.9+10 para la validación final, con bytecode 17 (ADR-002).
- No hay emulador instalado ni `/dev/kvm`. ADB no pudo crear su carpeta por defecto
  bajo `/home/agent` (filesystem de solo lectura). No se ha conectado un teléfono.
- Gradle 8.13 descargado y verificado; Wrapper generado desde esa distribución,
  no creado a mano. El proxy de este entorno requiere propiedades Java locales
  y el truststore del sistema; esa configuración no pertenece al proyecto.

## Comandos y resultados finales

```bash
./gradlew --version
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --max-workers=2 --console=plain --continue
git diff --check
```

| Verificación | Resultado observado |
|---|---|
| Gradle 8.13, configuración y resolución del proyecto | PASS con Temurin 21.0.9+10 |
| `:app:assembleDebug` | PASS, APK generado con firma debug válida |
| `:app:testDebugUnitTest` | PASS, **9 pruebas, 0 fallos, 0 errores, 0 omitidas** |
| Dominio / contraste / navegación real Hilt | 4 / 1 / 4 pruebas PASS |
| `:app:lintDebug` | PASS, **0 errores y 7 avisos `NewerVersionAvailable`** |
| `:app:assembleDebugAndroidTest` | PASS, APK instrumentado compilado; no ejecutado en dispositivo |
| Identidad del APK con `aapt2 dump badging` | `com.lectoria.app`, Lector IA, versión 0.1.0, min 26 / target 36 |
| `apksigner verify --verbose` | PASS, APK Signature Scheme v2, un firmante debug |
| Wrapper JAR contra checksum oficial | PASS, SHA-256 coincidente |
| Permisos en manifiesto fusionado | Solo permiso interno signature de AndroidX; sin Internet ni almacenamiento/audio |
| Dominio sin imports Android/Compose/Hilt | PASS, inspección de imports |
| Captura de Biblioteca | Generada y revisada visualmente; render nativo de Robolectric/API 36 |
| Sync desde la interfaz de Android Studio | NOT_RUN; se validó el proyecto con Gradle CLI |
| Instalación y prueba instrumentada conectada | NOT_RUN; sin dispositivo/emulador disponible |

Última ejecución completa:

```text
BUILD SUCCESSFUL in 29s
94 actionable tasks: 23 executed, 71 up-to-date
```

Los 7 avisos son actualizaciones disponibles de Kotlin/plugin Compose, Hilt,
Coroutines y Robolectric. Se conserva la matriz estable probada de ADR-002; no
se silenciaron errores de lint. No quedan avisos de APIs incompatibles con minSdk,
respaldo o iconos. Se eliminó del tema un atributo exclusivo de API 27; los colores
de barras se configuran mediante `enableEdgeToEdge` compatible con API 26.

Artefactos locales (no se versionan APK, logs ni contenido privado):

- `app/build/outputs/apk/debug/app-debug.apk` — 11 781 112 bytes.
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
- `app/build/reports/tests/testDebugUnitTest/index.html` y XML bajo `app/build/test-results`.
- `app/build/reports/lint-results-debug.html` y `.xml`.
- `app/build/reports/screenshots/library.png` — representación de prueba, no captura de un teléfono.
- Log completo de esta sesión: `/workspace/validation/lector-ia/build-test-lint.log`.

SHA-256 del APK Debug generado en esta sesión:
`f3febb3e548110554fd84294746f1ccdfec2d0a77a2038e9e336ba62cdfcb589`.
El hash/firma debug pueden variar al recompilar en otro equipo.

Checksum oficial comprobado de `gradle-wrapper.jar`:
`81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`.

## Pruebas incluidas

- `ReadingPositionTest`: límites válidos/negativos, locators por formato, identidad
  y metadata mínima de un libro verificado.
- `NavigationTest`: inicio vacío, ninguna acción Importar falsa, cuatro destinos,
  selección repetida, Atrás y restauración tras recrear Activity, con Hilt real.
  API 36, renderizado nativo Robolectric y captura en
  `app/build/reports/screenshots/library.png`.
- `ThemeContrastTest`: contraste WCAG AA de texto normal y estados sobre las
  superficies de lectura del tema.
- `AppSmokeTest` instrumentado: arranque y navegación en dispositivo/emulador.

## Problemas de entorno investigados

1. Las variables heredadas `ANDROID_SDK_HOME` y `ANDROID_USER_HOME` apuntaban a
   preferencias distintas. AGP rechazó la configuración; se eliminó la variable
   obsoleta en el script local, sin tocar el proyecto ni rutas del usuario.
2. El primer `assembleDebug` pasó con JDK 17, pero Robolectric/API 36 rechazó ese
   runtime porque exige Java 21. Se documentó la excepción en ADR-002 y se mantuvo
   el bytecode en 17.
3. Robolectric intentó crear `.robolectric-download-lock` en `/home/agent`, de solo
   lectura. Un init script **externo al repositorio** dirige exclusivamente la
   carpeta Java de las pruebas y su caché Maven a `/workspace/tooling`, usando
   Maven Central oficial. No modifica `$HOME` ni las pruebas/assertions.
4. El proxy necesita propiedades JVM y truststore del sistema para descargar
   Gradle/dependencias. La configuración es local al entorno; no hay proxy ni rutas
   absolutas impuestas a Android Studio o CI desde el proyecto.

El comando ejecutado en este sandbox añadió a los argumentos de arriba:
`--init-script /workspace/tooling/robolectric-local.init.gradle`, después de cargar
`/workspace/tooling/android-env.sh`. El init script solo contiene:

```groovy
allprojects {
    tasks.withType(Test).configureEach {
        systemProperty 'user.home', '/workspace/tooling/robolectric-home'
        systemProperty 'maven.repo.local', '/workspace/tooling/maven'
        systemProperty 'robolectric.dependency.repo.url', 'https://repo.maven.apache.org/maven2'
    }
}
```

Las pruebas esperan a que termine la navegación antes de enviar Atrás. La captura
usa `decorView.draw(Canvas)` con renderizado nativo de Robolectric: el callback de
redibujado de PixelCopy usado por Compose `captureToImage` no llegó en su ventana
virtual. Ninguno de estos ajustes sustituye las aserciones de navegación o inventa
una ejecución física. El wrapper Windows se conserva con CRLF mediante
`.gitattributes`; su ejecución en Windows está `NOT_RUN`.

## Pendientes que requieren dispositivo

`NOT_RUN`: instalación/arranque nativo, prueba instrumentada conectada, revisión
visual en Samsung Galaxy S22+ Android 16, TalkBack, gestos/insets, tamaño de fuente
grande y modo offline en dispositivo. El smoke test no se declara ejecutado por
haberlo compilado. Tampoco se hicieron pruebas de libros, PDF, TTS, audio o nube.

## CI en GitHub

El workflow `Android` ejecuta los mismos cuatro objetivos Gradle con JDK 21 y sube
los reportes. Usa el SDK/Command-line Tools preinstalado del runner Ubuntu e instala
solo Platform-Tools, Platform 36 y Build-Tools 36.0.0. Las acciones v5 están fijadas
por SHA a tags verificados y usan el runtime actual de GitHub.

La primera ejecución del PR falló **antes del build** porque
`android-actions/setup-android@v3` intentaba instalar el paquete retirado `tools`:
`Warning: Failed to find package 'tools'`. Se eliminó ese paso redundante y se usa
`$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager` directamente. No se modificaron
los resultados locales ni se omitieron tareas de CI para resolverlo.

Los resultados remotos están en el
[PR #1](https://github.com/Gust4av033/LectorIA-Android/pull/1) y en
[GitHub Actions](https://github.com/Gust4av033/LectorIA-Android/actions).
