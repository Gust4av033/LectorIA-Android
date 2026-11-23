# Estado y continuidad — 23/11/2026

## Fase 0

Base Android implementada en `feature/fase-0-fundamentos`. Proyecto único `:app`,
tema Compose, Hilt, navegación y contratos iniciales. La documentación de arranque
permanece como fuente de alcance. Estado: **TESTED en entorno local** (APK Debug,
9 pruebas y lint sin errores). Falta validación manual en dispositivo para cerrar
todos los criterios de salida. Evidencia y limitaciones: `testing.md`.

No hay bibliotecas ni contenido de prueba presentados como reales. Persistencia,
importación, motores de lectura/voz, audio y nube siguen sin implementar. La salida
completa de fase requiere además la validación visual/manual en dispositivo.

## Próxima tarea concreta — Fase A: biblioteca e importación

1. Introducir Room y DataStore con versiones compatibles verificadas, esquemas
   exportados y estrategia de migración. Añadir casos de uso e inyección de repositorios.
2. Implementar SAF, copia temporal a almacenamiento privado, validación, límites,
   hash, duplicados y estados recuperables antes de marcar un libro listo.
3. Definir cómo validar apertura/metadata por formato sin adelantar todo el lector;
   resolver el spike Readium y matriz Kotlin/AGP/KSP si la validación EPUB lo requiere.
4. Mostrar libros reales y estado de importación/errores; conectar un botón Importar
   solamente cuando realice la operación. Confirmar eliminación explícitamente.
5. Probar inválidos, vacíos, grandes, Unicode, duplicados, importación interrumpida,
   reinicio, reconciliación y migraciones sin perder originales ni progreso.

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
