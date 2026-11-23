# Validación de Fase B — 23/11/2026

Versión 0.3.0, rama `feature/fase-b-lector`, basada en `feature/fase-a-biblioteca`.
El usuario confirmó que importar un libro funciona en su teléfono y aclaró que
es **EPUB** (USER_REPORTED; modelo no especificado). Las comprobaciones de esta
fase distinguen JVM/Robolectric de Android nativo.

## Comprobaciones automáticas

Comando reproducible del proyecto:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --console=plain
```

En el sandbox se usa el mismo JDK 21/SDK 36 y las cachés ya instaladas. El init
script externo de Robolectric conserva los ajustes de repositorio/SSL del
entorno, sin imponer rutas del agente al equipo del usuario. Se limitan workers
a 2 y se reutiliza `app/build`; no se hace clean ni se guardan APKs duplicados.

Resultado del árbol final de código (`2bdeb54`): **PASS**, 5 min 54 s,
95 tareas (73 ejecutadas, 22 up-to-date).

| Comprobación | Resultado observado |
|---|---|
| assembleDebug | PASS, APK 0.3.0 / versionCode 3 |
| testDebugUnitTest | PASS, 53 pruebas; 0 fallos, errores u omitidas |
| lintDebug | PASS, 0 errores; 9 NewerVersionAvailable y 1 aviso de dependencia explicado abajo |
| assembleDebugAndroidTest | PASS, incluidas PdfReaderTest/PdfImportTest/AppSmokeTest |
| Firma y metadata APK | apksigner verify PASS, firma debug v2, minSdk 26 / target 36 |
| Manifiesto fusionado | Solo permiso interno de receptor; sin Internet/almacenamiento general |
| git diff --check | PASS |

Las 53 pruebas se distribuyen en repositorio/importación (14), inspector (10),
lectores (9), UI biblioteca/lector (6), progreso/migración (4), navegación (4),
dominio (4), preferencias (1) y contraste (1). El test EPUB incluye un TOC con
subsecciones que apuntan al mismo recurso: conserva el título principal.

APK Debug generado una sola vez en su ruta estándar, 21 561 684 bytes;
SHA-256 `ef259caabd60b7d5904bd3f9bf03f298c9fee624ed1e965343c2d22dec024339`.
Los cuatro avisos/licencias nuevos se comprobaron dentro del APK. Su firma debug
es propia del agente: para conservar la instalación del usuario se recomienda
compilar/actualizar desde su Android Studio, con su keystore habitual.

Limpieza de herramientas del agente: se eliminaron 775,4 MiB de bootstrap JDK 17,
Gradle duplicado y archivos de instalación ya reemplazados, además de 1,4 MiB de
fuentes temporales consultadas para los SDKs. Se conservaron JDK 21, SDK, cachés
reutilizables y evidencia final; no se borraron libros ni datos del usuario.

Cobertura añadida:

- EPUB real generado en memoria: texto español, TOC/sin TOC, varios recursos,
  paginación, retroceso entre capítulos y reapertura de un locator Readium.
- TXT largo con Unicode/emoji, UTF-16LE/BOM, reconstrucción sin repetir/perder
  caracteres, rangos inválidos y ausencia de copias adicionales.
- PDFBox procesa un PDF textual real; PDF sin texto no genera chunks ficticios.
  Esto **no valida** PdfRenderer en Robolectric.
- Integridad: archivo ausente o cambiado con igual longitud falla sin borrarlo.
- Room 1→2 desde el esquema v1 exportado, incluidos índices; conserva libros,
  persiste los tres tipos de locator y reabre la base. Cascade solo del libro
  eliminado, rechazo de updates viejos y libros no disponibles.
- UI real con Activity/Hilt/Room y proveedor sintético: importa, abre TXT, avanza,
  vuelve a biblioteca, reabre y recrea Activity conservando el fragmento; una
  ubicación inválida permanece guardada hasta elegir una sección explícitamente.
- Regresión de biblioteca, importación, recuperación, eliminación, preferencias,
  navegación y contraste de Fases 0/A.

Lint conserva avisos de versiones más recientes disponibles que se contrastan
con la matriz de compatibilidad elegida. También informa un
`TrustAllX509TrustManager` dentro de **bcpkix-jdk15to18 1.87**: no proviene de un
cliente TLS de la app. La app no solicita Internet y su HttpClient Readium rechaza
solicitudes remotas. El aviso no se suprime ni se afirma una auditoría completa
del proveedor; debe revisarse si se habilita red en una fase futura.

La captura `app/build/reports/screenshots/reader-txt.png` se genera con contenido
sintético en los tests y se revisa visualmente. No se versionan capturas ni libros.

## Pruebas conectadas — NOT_RUN

El agente trabaja en Linux cloud, sin acceso al Android Studio de Windows ni al
teléfono USB del usuario. No hay dispositivo/emulador conectado aquí.
`PdfImportTest`, `PdfReaderTest` y `AppSmokeTest` se compilan; su ejecución física
queda pendiente y no se declara realizada por el agente.

En Android Studio se pueden ejecutar desde el triángulo de cada clase de
`app/src/androidTest`. `PdfReaderTest` genera un PDF con texto español y una
segunda página con una imagen de texto; comprueba extracción frente a ausencia
de texto, render nativo y navegación. El archivo se borra en `finally`.

## Probar en el teléfono sin terminal

1. **Git → Fetch**. En las ramas remotas, elegir
   **origin/feature/fase-b-lector → Checkout**. Conservar cambios locales si el
   IDE los informa; no sobrescribirlos para cambiar de rama.
2. Sync con **Gradle JDK 21**; seleccionar **app**, el celular y **Run ▶**.
   Actualizar la instalación existente con la firma debug de ese equipo.
3. Verificar que siguen los libros importados en Fase A. No desinstalar ni borrar
   datos para probar una migración.
4. Abrir un TXT/EPUB propio: **libro → Leer libro**. Leer, probar A−/A+, avanzar
   varios fragmentos y seleccionar otra sección. EPUB con/sin TOC debe abrir;
   la vista es texto, sin conservar CSS/ilustraciones del original.
5. Volver a biblioteca y abrir el mismo libro. Debe conservar sección/fragmento.
   Abrir otro libro y comprobar que cada uno guarda su avance independiente.
6. Forzar detención desde Ajustes de Android y volver a abrir: **Lector** debe
   retomar el libro más reciente. Se restaura el inicio del fragmento, no el
   píxel de scroll dentro de él. La navegación guarda el fragmento solicitado
   antes de mostrarlo; una detención durante carga puede retomar esa transición.
7. Abrir un PDF textual: consultar página, cambiar de página y usar **Ver texto**.
   En texto, anterior/siguiente recorren fragmentos; en visual, páginas. Confirmar
   que tildes, columnas y fuentes del documento particular resultan legibles.
8. Abrir un PDF escaneado/mixto: la imagen debe verse y las páginas sin texto
   deben avisar de OCR pendiente; no deben ofrecer narración inexistente.
9. Repetir sin conexión. Revisar un TXT/EPUB largo y varios cambios de página,
   anotando lentitud, cierres o contenido incompleto sin enviar libros privados.

No se implementan todavía voz, Play, OCR, nube ni preservación visual del EPUB.
La validación física de rendimiento, fuentes y PDFs complejos continúa pendiente.
