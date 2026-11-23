# AGENTS.md — Reglas para agentes de desarrollo de Lector IA

Este repositorio implementa la aplicación Android **Lector IA**. La fuente de verdad es `docs/MASTER_PLAN.md`. Antes de implementar, leer dicho archivo y revisar el estado real del repositorio.

## Prioridades

1. Funcionalidad verificable y seguridad de datos.
2. Offline-first, TTS local y narración IA solo opcional.
3. Separación entre lectura de documentos, extracción textual, narración, reproducción, datos y UI.
4. UI Compose estilo tormenta medianoche, cuidando accesibilidad.
5. Mínima complejidad razonable; no inventar integraciones ni APIs.

## Flujo

- Inspeccionar `git status`, rama y archivos antes de modificar.
- Trabajar en rama `feature/*` y PR cuando exista repositorio remoto.
- No borrar ni sobrescribir cambios preexistentes sin autorización.
- Mantener `main` estable; commits pequeños.
- Ejecutar build y tests después de cada bloque importante; indicar comandos, salida y cualquier prueba `NOT RUN`.
- Actualizar `docs/architecture.md`, `docs/requirements.md`, `docs/testing.md` o `docs/roadmap.md` al modificar decisiones/alcance/estado.
- Entregar resumen factual con pendientes, riesgos y rutas de archivos.

## Restricciones técnicas

- Kotlin + Jetpack Compose. MVVM con dominio desacoplado; Room y DataStore para persistencia.
- Readium para EPUB; PDF requiere validación independiente para extracción de texto.
- TTS local y Media3 audio-file no tienen idéntica semántica de pause/seek. Diseñar interfaces honestas.
- `CloudNarrationGateway` solo mediante backend seguro y consentimiento explícito; jamás incrustar una clave privada en APK.
- Caché IA con integridad, claves estables y prevención de regeneración duplicada.
- Importación de archivos robusta y recuperable; nunca asumir transacción atómica conjunta entre Room y filesystem.
- No evadir DRM ni subir libros privados a terceros automáticamente.
- Sin ejemplos de libros, voces o operaciones facturables simuladas como disponibles en producción.

## Seguridad del repositorio

Nunca subir `local.properties`, `.env`, credenciales, keystores, tokens, bases de datos privadas, PDFs/EPUBs personales ni audio generado con contenido privado. Los fixtures de prueba deben ser sintéticos o de uso permitido.

## Terminación

Una tarea no está DONE sin código real, compilación, pruebas pertinentes y documentación. Si algo no se pudo ejecutar, explicar el motivo con claridad.
