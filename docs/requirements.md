# Requisitos y evidencia — Fases 0 y A

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
