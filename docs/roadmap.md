# Estado y continuidad — 23/11/2026

## Fase 0

Base Android implementada en `feature/fase-0-fundamentos`. Proyecto único `:app`,
tema Compose, Hilt, navegación y contratos iniciales. La documentación de arranque
permanece como fuente de alcance. Estado: **TESTED en entorno local** (APK Debug,
9 pruebas y lint sin errores, más CI aprobada). El usuario confirmó posteriormente
Sync, ejecución desde Android Studio y arranque en celular. Esa validación es
**reportada por el usuario**; no equivale a instrumentación física ejecutada por
el agente. Evidencia histórica y limitaciones: `testing.md`.

En esa entrega no había persistencia/importación ni motores de lectura/voz. No se
incluyen libros de muestra en producción. La Fase A añade biblioteca real sin
adelantar lector, narración, audio ni nube.

## Fase A — Implementación actual

Rama `feature/fase-a-biblioteca`, basada en Fase 0 porque el PR #1 seguía abierto
al iniciar. No se fusiona main automáticamente. Room 2.8.3 y DataStore 1.2.0,
biblioteca persistente, SAF, validación acotada, copias privadas, SHA-256,
duplicados, búsqueda/orden, detalles y eliminación confirmada están implementados.
El protocolo recupera VERIFIED antes/después de renombrar y conserva incidencias
y archivos originales ante error. El esquema Room v1 está exportado y no hay
fallback de recreación destructiva.

El estado real de pruebas y los pendientes físicos están en `testing-phase-a.md`.
PDF usa apertura/render nativos; su prueba instrumentada requiere Android real.
La metadata EPUB de importación no sustituye el futuro lector Readium.

## Siguiente tarea

Validar esta entrega en el teléfono con TXT/EPUB/PDF sintéticos o autorizados,
persistencia tras forzar detención, cancelación/duplicados y eliminación confirmada.
Después, Fase B: resolver el spike Readium/matriz Kotlin y el extractor PDF,
implementar lectura por formato y restauración de ubicaciones sin perder originales.

## Fases siguientes

- **B — Lectura:** spike EPUB Readium, extractor PDF independiente, TXT, secciones y
  restauración del locator; informar PDF escaneado sin prometer OCR.
- **C — Voz local:** TTS offline real, fragmentos y recuperación textual, audio focus,
  pantalla apagada y Bluetooth. Añadir Media3 cuando haya archivos de audio reales.
- **D — IA opcional:** backend seguro, costos, consentimiento, idempotencia y caché.
- **E — Release:** accesibilidad, pruebas largas físicas, rendimiento y firma del dueño.

## Riesgos abiertos

- Readium 3.4.0 declara Kotlin 2.4.20, superior al compilador actual: actualizar y
  verificar la matriz completa antes de enlazarlo; no suprimir controles de metadata.
- No se ha elegido extractor PDF ni evaluado su licencia y comportamiento real.
- Robolectric no sustituye GPU, TalkBack, gestos, ventanas y dispositivos físicos.
- No hay backend, permisos de envío de texto ni proveedores de pago configurados.
