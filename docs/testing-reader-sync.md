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
