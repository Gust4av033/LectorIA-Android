# Requisitos y evidencia — Fases 0, A y B

Fuente: `MASTER_PLAN.md` y solicitud de Fase 0 del 23/11/2026.
`IMPLEMENTED` describe código; los resultados de ejecución están en `testing.md`.

| Requisito | Implementación / evidencia | Estado |
|---|---|---|
| Repo existente, documentación preservada, rama feature | Inspección inicial limpia; rama `feature/fase-0-fundamentos` | IMPLEMENTED |
| Android Kotlin DSL, app, package y minSdk 26 | settings, catálogo, app/build.gradle.kts, Manifest | IMPLEMENTED |
| Wrapper completo y checksum | gradlew, gradlew.bat, gradle-wrapper.jar y properties | IMPLEMENTED |
| Compose + Material 3, tema tormenta medianoche | core/designsystem/Theme.kt, recursos e iconos propios | IMPLEMENTED |
| Hilt mínimo real | Application, Activity y LibraryViewModel anotados | IMPLEMENTED |
| MVVM, Coroutines/Flow y dominio sin SDK | StateFlow, collectAsStateWithLifecycle, domain/* | IMPLEMENTED |
| Biblioteca vacía sin libros de muestra | LibraryScreen y pruebas de UI | IMPLEMENTED |
| Cuatro destinos, navegación, Atrás y restauración | LectorIaApp y NavigationTest | IMPLEMENTED |
| Sin funciones aparentes | Pantallas pendientes, sin botón Importar/Play falso | IMPLEMENTED |
| Contratos separados de datos, documentos, narración y audio | domain/* y responsabilidades data/engine | IMPLEMENTED |
| Sin claves privadas ni solicitudes cloud | Sin permiso Internet ni clientes red; .gitignore | IMPLEMENTED |
| Sync/build/lint/pruebas verificables | Comandos y resultados en testing.md | Consultar evidencia |
| Ejecución física, Bluetooth y Android 16 Samsung | Requiere dispositivo autorizado | NOT_RUN |
| Importación, Room, DataStore, EPUB/PDF/TXT y TTS | Fases A–C, sin implementaciones en esta entrega | PLANNED |
| IA, backend y caché | Fase D y consentimiento explícito | PLANNED |

Los identificadores y posiciones inválidas se prueban como límites del dominio.
Las pruebas Robolectric ejercitan la Activity/Hilt/navegación reales. El smoke test
instrumentado se compila para ejecutarlo en el dispositivo del propietario.

## Fase A — Biblioteca e importación

La matriz anterior corresponde a la entrega histórica de Fase 0. La fase actual
conecta sus contratos con almacenamiento real. Resultados finales y límites en
`testing-phase-a.md`; no inferir pruebas físicas a partir del éxito de Robolectric.

| Requisito | Implementación | Evidencia |
|---|---|---|
| Selección EPUB/PDF/TXT con SAF y acceso acotado | OpenLibraryDocument, SafSourceReader | LibraryFlowTest: contrato OPEN_DOCUMENT/OPENABLE y proveedor sintético |
| Copia privada, hash, tamaño y duplicados | LibraryFiles, RoomBookRepository | LibraryRepositoryTest: límites declarados/reales, identidad, concurrencia |
| Metadata e inspección EPUB/TXT | AndroidDocumentInspector | DocumentInspectorTest y flujo EPUB con título/autor reales |
| PDF nativo, páginas y vista previa | PdfRenderer en inspector | PdfImportTest instrumentado; ejecución conectada pendiente |
| Room persistente sin recreación destructiva | LibraryDatabase v1, esquema exportado | Reapertura y rechazo de versión incompatible conservando datos |
| Recuperación ante interrupción | COPYING/VERIFIED/FAILED/UNAVAILABLE | Fallos antes/después de publicar, cancelación, huérfanos, archivos faltantes |
| Eliminación confirmada y recuperable | DeletionRequest y estado DELETING | UI cancela/confirma; datos verifican reintento y preservación de otro libro |
| Búsqueda y orden persistente | ObserveLibrary, LibraryPreferences/DataStore | Flujo UI y cierre/reapertura de DataStore |
| UI honesta, errores y detalles | LibraryRoute/Screen y LibraryViewModel | Pruebas de UI con Activity/Hilt/Room reales |
| Original externo y permisos protegidos | Proveedor abierto solo para lectura; sin permiso de almacenamiento general | Test de proveedor comprueba original después de eliminar copia |
| Continuidad de Fase 0 | Navegación y tema conservados | NavigationTest, ReadingPositionTest y ThemeContrastTest |

El usuario confirmó el 23/11/2026 el Sync y arranque de Fase 0 desde Android Studio
en su celular. Se registra como **USER_REPORTED**; no acredita ejecución de pruebas
instrumentadas, Bluetooth o de las nuevas importaciones de Fase A.

En la continuación del 23/11/2026 el usuario confirmó que ya puede **importar un
libro EPUB**. Estado **USER_REPORTED** para ese flujo de Fase A; no especificó
modelo ni ejecución de la suite instrumentada.

## Fase B — Lectura, extracción y restauración

| Requisito | Implementación | Evidencia |
|---|---|---|
| Abrir desde la biblioteca | Leer libro → ReaderRoute/ViewModel, casos de uso reales | LibraryFlowTest importa TXT, abre y navega |
| EPUB mediante Readium, con/sin TOC | shared/streamer 3.1.2, reading order, títulos TOC y modo texto Compose | DocumentReaderTest: Unicode, sin TOC, varios recursos, ida/vuelta y reapertura |
| TXT largo y codificaciones importadas | Índice incremental, UTF-16 offsets, lectura acotada | Reconstrucción completa sin pérdida/duplicación, emoji y UTF-16LE |
| PDF visual + extracción real | PdfRenderer separado de PDFBox Android | Texto real y ausencia de texto verificados en JVM; PDF mixto/render instrumentados pendientes |
| Tratamiento explícito de PDF sin texto | Aviso por página, sin OCR simulado | PDF sin texto no entrega fragmentos narrables; imagen escaneada en PdfReaderTest |
| Secciones, tamaño de letra y navegación | Compose real, texto seleccionable, anterior/siguiente e índice | Flujos UI reales y captura nativa |
| Progreso por libro y último libro leído | RoomReadingProgressRepository, codec versionado | Roundtrip TXT/PDF/EPUB, reapertura, cascade acotado, updates antiguos rechazados |
| Conservar biblioteca al actualizar | Migración Room 1→2, sin fallback destructivo | Migración desde esquema v1 exportado, metadatos conservados y progreso persistente |
| No borrar avance ante locator inválido | Error y selección explícita de sección | Test UI verifica valor antes/después de recuperar |
| No crecer en copias de documentos | Sesiones/índices en memoria, sin EPUB extraído o PDF scratch en disco | Test TXT comprueba ausencia de archivos extra; pasos físicos pendientes |
| Integridad al abrir | Comparación SHA-256 completa | Rechaza modificación de igual longitud sin sobrescribir archivo |
| Voz local/narración | Contratos previos, unidades textuales disponibles | Fase C, PLANNED |

Estado exacto de ejecución y límites de formato: `testing-phase-b.md` y
`architecture.md` ADR-009/010. La implementación visual PDF y el reinicio real
en teléfono no se declaran DONE a partir de Robolectric.

## Fase C — Voz local implementada, en validación

Motor/voz/velocidad reales, entrada desde lector, reproductor y ajustes conectados,
pausa/reanudación textual, avance de escucha independiente, servicio foreground,
MediaSession/notificación, foco de audio y pausa por desconexión implementados.
Migración Room 2→3 conserva libros y ReadingProgress. Pruebas del coordinador,
puente SDK y migración: consultar testing-phase-c.md para resultados finales.
Audio/Bluetooth/llamadas/pantalla apagada/batería requieren prueba física; no se
atribuyen al agente ni a la confirmación del usuario sobre el lector EPUB.

Seguimiento 0.4.1: el usuario reportó fallo de voz con Samsung/Google y confusión en
los controles. Se implementa recuperación/inicialización TTS, protección de
foreground y rediseño de reproductor con prueba de voz independiente. Evidencia y
límites en testing-phase-c.md; no se declara corregido en hardware sin confirmación.

0.4.2: AOSP y la prueba del adaptador verifican preservación de LoadVoiceItem al
usar QUEUE_ADD. Recuperación limitada del -4, timeout, confirmación por callback,
voz predeterminada offline y reconexión manual implementados. 94 pruebas locales
PASS; el usuario confirma voz audible en ajustes de Android y fallo en la app.
Repetición de la prueba física tras esta corrección: pendiente.
