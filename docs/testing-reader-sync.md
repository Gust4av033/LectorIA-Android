# Lectura sincronizada — 0.5.0

Rama `feature/lectura-sincronizada`, sobre `feature/fase-c-voz-local` (PR #4).
El usuario confirmó que la voz ya se escucha en 0.4.2 (USER_REPORTED).
Esta extensión añade seguimiento de texto y ajustes locales de interpretación;
no valida por sí sola audio físico, Bluetooth o funcionamiento prolongado.

## Prueba desde Android Studio

1. Cambiar a `feature/lectura-sincronizada` desde el selector de ramas, actualizar,
   sincronizar Gradle y ejecutar con Run en el mismo teléfono. No desinstalar.
2. Reproducir el EPUB y pulsar **Ver libro**. Debe seguir la voz y dibujar un
   recuadro en la palabra/rango comunicado por el motor; algunos motores solo
   marcan la unidad completa (hasta 350 caracteres). Comprobar cambio de fragmento
   y capítulo, pausa/continuación y volver al lector sin repetir el audio.
3. Pulsar **Siguiendo la voz** para desactivarlo o navegar con Anterior/Siguiente.
   La escucha continúa. **Volver a la voz** lleva al rango actual sin reiniciarla.
   El botón principal pausa/continúa; **Escuchar desde esta parte** permite
   cambiar explícitamente el punto de escucha cuando se explora otra parte.
4. Elegir **Ajustes de voz → Interpretación → Narrativa / Expresiva**, probar la
   voz y continuar. Cambiar perfil pausa; el perfil debe sobrevivir a cerrar/abrir
   la app. Comparar preguntas/exclamaciones con Natural en Samsung y Google.
5. Comprobar que biblioteca, lector, reproductor y ajustes tienen fondo oscuro
   neutro, superficies opacas y controles legibles. Probar fuente del sistema
   grande, TalkBack y contraste en el teléfono.
6. Para PDF textual, la sincronización usa **Mostrar texto**. No hay recuadros
   sobre la imagen del PDF ni OCR en documentos escaneados.

## Cobertura automática

- ReaderSynchronizationTest: intersección de rango/fragmento, sección distinta,
  BOM, Unicode, seguimiento entre fragmentos sin reiniciar audio, no cargar ni
  guardar por palabra, exploración manual, reactivación, exclusión de otro libro
  y de la muestra de voz, finalización y suspensión del seguimiento ante fallo de página.
- LocalNarrationEngineTest: callbacks con inicio/fin absolutos y fallback del
  rango completo, perfiles aplicados al motor, conservación de cada carácter,
  límites UTF-16 y regresión de cola/cancelación/reconexión.
- SpeechPreferencesTest: ajustes antiguos sin perfil, persistencia del perfil y
  valor desconocido; PlayerScreenTest: selección del perfil real.
- ReaderScreenTest: rango visible, controles de pausa/seguimiento y captura
  sintética `app/build/reports/screenshots/reader-synchronized.png`.
- ThemeContrastTest y regresión completa de importación, EPUB/PDF/TXT, Room,
  progreso, TTS, servicio y navegación.

## Validación pendiente

NOT_RUN por el agente: teléfono/USB, sonido real, calidad subjetiva de entonación,
precisión/latencia de onRangeStart en Samsung/Google, PDF nativo, TalkBack,
fuente grande, pantalla apagada prolongada, Bluetooth y llamadas. El agente usa
Linux cloud, sin conexión al Android Studio Windows ni al celular.
No se añaden motores cloud, permisos, dependencias, migraciones ni audio en disco.
Se reutilizan SDK/cachés y `app/build`; las capturas usan texto sintético y los
reportes se sobrescriben, sin libros personales ni APKs duplicados.

## Resultado local del código 70f9e48

JDK 21, SDK 36 y cachés reutilizados. Comando completo:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --console=plain
```

En este sandbox se añade el init script externo existente
`/workspace/tooling/robolectric-local.init.gradle` y `--max-workers=2` para el
repositorio/SSL de Robolectric; no se impone al Android Studio del usuario.

**PASS**, 1 min 47 s, 95 tareas (24 ejecutadas, 71 up-to-date).

| Comprobación | Resultado |
|---|---|
| assembleDebug | PASS, APK 0.5.0 / versionCode 7 en su ruta estándar |
| testDebugUnitTest | PASS, 102 pruebas, 0 fallos/errores/omitidas |
| lintDebug | PASS, 0 errores y 16 avisos (mismo total previo) |
| assembleDebugAndroidTest | PASS, empaquetado; sin ejecución conectada |
| Revisión visual | Capturas sintéticas del lector sincronizado y reproductor revisadas |
| git diff --check | PASS |

Se detectó texto oscuro en la primera captura aislada del lector. El tema ahora
establece superficie y color de contenido raíz explícitos; la captura final
muestra texto marfil legible sobre negro. Se captura antes de los toques para
excluir estados transitorios de ripple; las acciones de pausa y seguimiento se
comprueban después. No se usa una imagen generada como evidencia de UI.

## Refinamiento UI y prosodia local — `feature/ui-pro-vscode-copilot`

Base: commit `9a38e2a`, tip de `feature/ui-dark-pro`. Cambios: portada y jerarquía
de reproducción, estado y navegación por parte/sección, tarjetas de biblioteca,
calidad declarada de voz y acceso a ajustes TTS de Android. La barra del reproductor
indica el fragmento actual dentro de la sección; no representa tiempo de audio.
Las voces siguen siendo las que instala/provee Android, sin backend, costo de API
ni modelos nuevos.

Validación en Windows, JDK 21:

| Comprobación | Resultado |
|---|---|
| `:app:testDebugUnitTest --tests 'com.lectoria.app.engine.LocalNarrationEngineTest' --tests 'com.lectoria.app.presentation.PlayerScreenTest'` | PASS, 20 pruebas |
| `:app:assembleDebug` | PASS |
| `:app:assembleDebugAndroidTest` | PASS, empaquetado sin ejecución en dispositivo |
| `:app:testDebugUnitTest` completa | 101/102 PASS; una prueba Room (`orphan original...`) falla en contexto de suite por `SQLiteCantOpenDatabaseException`; repetida aislada: PASS |
| `:app:lintDebug` | FAIL por `PropertyEscape` en `local.properties` ignorado/no versionado; 29 avisos preexistentes de dependencias/API y recursos |
| Captura `app/build/reports/screenshots/player.png` | Generada desde Robolectric y revisada |
| Voz real, usabilidad, accesibilidad/TalkBack y tamaños grandes | NOT RUN; requieren teléfono y voces instaladas |

La prueba Room que falla está en código de datos no modificado. Aislada pasa, pero
falla en las dos ejecuciones de la suite completa realizadas en este entorno; no se
atribuye una causa raíz sin investigarla. El error de lint proviene de la ruta SDK
local que contiene `local.properties`; no se cambió ese archivo del usuario. Los
perfiles y pausas se verifican con pruebas automatizadas, pero eso no confirma la
naturalidad subjetiva del audio en un motor físico.
