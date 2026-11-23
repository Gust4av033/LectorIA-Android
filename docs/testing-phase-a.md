# Validación de Fase A — 23/11/2026

Estado: **TESTED en entorno local**. Las funciones nuevas no se declaran validadas
físicamente por haber pasado pruebas locales. La prueba PDF nativa conectada queda
explícitamente NOT_RUN.

| Comprobación ejecutada | Resultado |
|---|---|
| assembleDebug | PASS, APK 0.2.0 generado |
| testDebugUnitTest | PASS: **37 pruebas, 0 fallos, 0 errores, 0 omitidas** |
| lintDebug | PASS: **0 errores, 7 avisos de versiones disponibles** |
| assembleDebugAndroidTest | PASS, smoke test y PdfImportTest compilados |
| Checks de whitespace/diff | PASS |
| Capturas nativas de biblioteca/detalles | Generadas y revisadas visualmente |
| Selector real / renderer PDF / instrumentación conectada | NOT_RUN por el agente |

Distribución: 14 pruebas de repositorio/recuperación, 9 de inspección, 4 del flujo
completo UI/importación, 4 de navegación, 4 de dominio, 1 de contraste y 1 de DataStore.

Última ejecución completa:

```text
BUILD SUCCESSFUL in 2m 28s
94 actionable tasks: 24 executed, 70 up-to-date
```

## Entorno y comandos

Se reutiliza el entorno de Fase 0: Temurin 21.0.9, SDK/Build-Tools 36, Gradle 8.13,
AGP 8.13.2 y Kotlin 2.2.21. Nuevas dependencias publicadas verificadas:
Room 2.8.3 y DataStore Preferences 1.2.0. Bytecode Java/Kotlin 17, minSdk 26.

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --max-workers=2 --console=plain --continue
git diff --check
```

En este sandbox se carga `/workspace/tooling/android-env.sh` y se añade
`--init-script /workspace/tooling/robolectric-local.init.gradle` por las restricciones
de carpeta personal/proxy ya documentadas en `testing.md`. No se altera la lógica
de pruebas ni se imponen esas rutas a Android Studio o a GitHub Actions.

## Cobertura implementada

- **Dominio y tema:** integridad de identificadores, posiciones, hash/metadata y
  contraste WCAG AA; se conservan las pruebas de Fase 0.
- **Room/archivos:** copia real, metadata persistente tras cerrar/reabrir Room,
  hash y duplicados con nombres distintos, solicitudes concurrentes, rechazo de
  extensión contradictoria incluso en duplicados, tamaño declarado/real, vacíos
  y binarios, fallo del proveedor, cancelación propagada y descarte de temporales.
- **Recuperación:** error antes/después del movimiento atómico, COPYING incompleto,
  archivo privado ausente, adopción de original huérfano, conservación de directorio
  desconocido y reintento de eliminación sin tocar otro libro ni el proveedor.
- **Esquema:** nueva base v1 exportada; prueba de versión incompatible confirma
  rechazo sin borrar libros. No existe una migración productiva desde Fase 0 porque
  allí no había Room; se debe añadir una migración real al cambiar el esquema.
- **Documentos:** UTF-8, UTF-16 LE/BE con BOM, Windows-1252, español/Unicode,
  controles binarios, MIME/extensión, EPUB con metadata/entidades XML predefinidas y
  portada PNG, rutas inseguras y codificadas, DTD, LCP/cifrado, spine ausente,
  inflación ZIP, CRC alterado y archivo ZIP que no es EPUB. PDF truncado se rechaza
  antes de entrar al renderer nativo.
- **Preferencias:** orden por título guardado, cierre y reapertura de DataStore.
- **Flujo de UI:** Activity/Hilt/Room reales, contrato OPEN_DOCUMENT/OPENABLE,
  devolución/cancelación del resultado con proveedor sintético, importación TXT,
  duplicados, metadata EPUB, búsqueda y orden, recreación, eliminación cancelada
  y confirmada, conservación del archivo externo. No se simula un selector de
  archivos del teléfono: se entrega el resultado al contrato real en Robolectric.
- **Navegación:** cuatro destinos, selección repetida, Atrás y restauración.

Los fixtures se generan por código y son sintéticos. No hay libros del usuario en
Git ni datos de ejemplo que se instalen en producción.

## PDF: límite real del entorno de pruebas

Robolectric 4.16 no implementa shadows de PdfRenderer/PdfProcessor. Al probar la
apertura nativa de un PDF válido en API 36 se obtuvo:

```text
java.lang.NoSuchMethodError: long java.io.FileDescriptor.getOwnerId$()
at libcore.io.IoUtils.acquireRawFd
at android.graphics.pdf.PdfProcessor.create
```

Se comprobó que los sources publicados de Robolectric 4.18.1 tampoco proporcionan
esos shadows; no se actualizó una dependencia ni se inventó un renderer para
hacer pasar esa prueba. `PdfImportTest` vive en `androidTest`, genera un PDF con
PdfDocument y comprueba apertura, páginas, miniatura y rechazo de corrupción en
Android real. Se compila junto con el smoke test; su ejecución conectada está
**NOT_RUN** porque el agente no tiene un dispositivo/emulador Android disponible.

## Evidencia y límites

Los reportes de la ejecución final están en:

- `app/build/reports/tests/testDebugUnitTest/index.html`.
- `app/build/test-results/testDebugUnitTest/TEST-*.xml`.
- `app/build/reports/lint-results-debug.html` y `.xml`.
- `app/build/reports/screenshots/library.png` (biblioteca vacía).
- `app/build/reports/screenshots/library-with-import.png` y `book-details.png`
  (renderizado de prueba con EPUB sintético, no un teléfono).
- Log de sesión: `/workspace/validation/lector-ia-fase-a/build-final.log`.

El usuario confirmó Sync/Run y arranque de **Fase 0** en su celular. Esa evidencia
es **USER_REPORTED** y no demuestra aún las nuevas importaciones de Fase A.

Pendiente en dispositivo: selector SAF real, apertura/render PDF, EPUB de distintos
proveedores, documentos con contraseña/DRM, importación desde proveedores cloud
cuando corresponda, arranque offline con copias privadas, force-stop, TalkBack,
fuente grande, controles/insets y ejecución instrumentada conectada. La app no
implementa lector, extracción textual PDF, TTS ni servicios IA en esta fase.

## Pasos físicos de aceptación

Seguir el README con la rama de Fase A y JDK 21. Importar TXT Unicode, EPUB y PDF
propios/sintéticos; comprobar metadata y vista previa cuando corresponda. Cancelar
el selector, repetir un documento y comprobar que no aparecen copias adicionales.
Buscar/ordenar; forzar detención desde Ajustes de Android y abrir de nuevo.
Los libros y el orden deben persistir. Cancelar una eliminación debe preservar el
libro; confirmarla debe borrar solo la copia privada y conservar el original y los
demás libros. Para ensayo de interrupción usar un documento de prueba, reabrir la
app y comprobar la recuperación o incidencia explícita.

APK Debug local: 13416576 bytes; SHA-256:
`85ee3c771258fc599b36e9d6e4b2f0bd4af9bec5f15ea43ad60f77feabbddb27`. Firma de desarrollo local, no release.
