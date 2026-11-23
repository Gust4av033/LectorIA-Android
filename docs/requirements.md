# Requisitos y evidencia — Fase 0

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
