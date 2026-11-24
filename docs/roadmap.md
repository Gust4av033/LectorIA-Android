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

## Fase A — Biblioteca implementada

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

El usuario confirmó posteriormente que ya puede importar un EPUB. Se registra
como USER_REPORTED para ese flujo, sin atribuirle una suite física.

## Fase B — Implementada y probada en JVM; pendiente de teléfono

Rama `feature/fase-b-lector`, basada en Fase A (PR #2 aún abierto al comenzar).
Versión 0.3.0. Readium 3.1.2 compatible con la matriz existente, TXT incremental,
PDFBox textual y PdfRenderer visual, unidades narrables, navegación por secciones,
modo texto y tamaño de letra, guardado/restauración de avance y migración Room
1→2 conservando libros. No se implementa TTS ni reproducción de audio en esta fase.

Resultados, límites y pasos concretos para el teléfono: `testing-phase-b.md`.
EPUB prioriza lectura textual y no conserva el diseño original; PDF no hace OCR.
Build Debug, 53 pruebas, lint sin errores y empaquetado instrumentado: PASS.
La lectura física, PDFs nativos y detención real siguen NOT_RUN por el agente;
la confirmación previa del usuario corresponde a importación EPUB de Fase A.

## Siguiente tarea

Validar en Android Studio/celular lectura EPUB/TXT, PDF textual y escaneado,
restauración tras forzar detención y conservación de libros previamente
importados. Ejecutar PdfReaderTest/PdfImportTest en dispositivo. Después abordar
Fase C, voz local real y recuperación textual, dentro de su alcance separado.

## Fases siguientes

- **C — Voz local:** TTS offline real, fragmentos y recuperación textual, audio focus,
  pantalla apagada y Bluetooth. Añadir Media3 cuando haya archivos de audio reales.
- **D — IA opcional:** backend seguro, costos, consentimiento, idempotencia y caché.
- **E — Release:** accesibilidad, pruebas largas físicas, rendimiento y firma del dueño.

## Riesgos abiertos

- Antes de actualizar Readium 3.1.2, validar la matriz Kotlin y los locators ya
  persistidos; 3.4.0 exige Kotlin 2.4.20. No suprimir controles de metadata.
- Validar PDFBox y render nativo con los documentos/dispositivo reales; comprobar
  rendimiento y fuentes. PDFs muy complejos pueden superar los límites de memoria.
- Robolectric no sustituye GPU, TalkBack, gestos, ventanas y dispositivos físicos.
- No hay backend, permisos de envío de texto ni proveedores de pago configurados.

## Fase C — Voz local (continuación)

Rama feature/fase-c-voz-local, basada en Fase B (PR #3 pendiente de integración al
comenzar). El usuario confirmó lectura EPUB en su teléfono: USER_REPORTED.
TTS instalado, controles, avance independiente y servicio multimedia implementados.
Estado de pruebas y pasos de teléfono: testing-phase-c.md. La validación de una
sesión larga con pantalla apagada, Bluetooth, llamada y ahorro de batería sigue
pendiente; no se declara DONE por compilar o pasar Robolectric.

### Revisión de Fase C — 0.4.1

El primer intento físico de TTS falló según el usuario. Correcciones de arranque,
solicitud TTS, diagnóstico y controles implementadas en la misma rama/PR #4.
Repetir prueba de voz y EPUB en teléfono antes de avanzar a IA u otra fase.

### Revisión 0.4.2

Se corrige la cola SDK que podía cancelar la carga de voz, se sustituye la conexión
fallida con un reintento limitado y se añaden estados de conexión/diagnóstico.
La muestra del sistema sí funciona según el usuario; la app requiere nueva prueba
física después de actualizar. Build y 94 tests locales PASS. No avanzar a Fase D
hasta validar la escucha real de esta entrega.

## Extensión — Lector sincronizado y estilo

Rama `feature/lectura-sincronizada`, versión 0.5.0, sobre Fase C sin fusionar main.
El usuario confirmó que ya se escucha la voz (USER_REPORTED); aún no acredita
Bluetooth, llamadas o lectura prolongada con pantalla apagada.
Implementados rango narrado, seguimiento desactivable en lector, perfiles locales
de interpretación y nuevo diseño oscuro minimalista pedido por el usuario.
Evidencia automática y pasos en teléfono: `testing-reader-sync.md`.
La IA cloud sigue pendiente de backend, proveedor y consentimiento; no se añadió
un servicio de pago ni archivos de audio.
