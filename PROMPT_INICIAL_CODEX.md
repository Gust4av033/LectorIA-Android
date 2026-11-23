# PROMPT INICIAL PARA CODEX — LECTOR IA

Copiar y pegar este mensaje en Codex **después de abrir o conectar la carpeta/repositorio `LectorIA-Android`**. Deben estar presentes `docs/MASTER_PLAN.md` y `AGENTS.md`.

---

Actúa como **ingeniero senior Android**, especializado en Kotlin, Jetpack Compose, Clean Architecture/MVVM, Readium, Android TextToSpeech, Media3, Room, WorkManager y pruebas Android.

Vamos a comenzar **Lector IA**, una aplicación Android nativa que importa EPUB, PDF y TXT; permite leerlos, reproducirlos con TTS local gratuito y, más adelante, generar audio opcional con IA y guardarlo offline. El proyecto debe ser **offline-first**, modular de forma razonable, seguro y funcional. Este repositorio es independiente de Copiloto Moto.

**Documento rector obligatorio:** lee íntegramente `docs/MASTER_PLAN.md` y sigue `AGENTS.md`. Cuando una decisión técnica no esté cerrada, documenta el trade-off y propone la opción más simple y verificable; no contradigas silenciosamente el documento maestro.

## Tarea de esta ejecución: FASE 0, SOLO FUNDAMENTOS

1. Primero inspecciona el workspace: archivos, rama actual, `git status`, entorno Android, JDK y herramientas. No sobreescribas cambios existentes.
2. Comprueba compatibilidad entre versiones estables de Kotlin, AGP, Gradle, Compose y Readium; anota las versiones efectivamente utilizadas. No uses dependencias inventadas ni versiones dinámicas.
3. Crea o adapta un proyecto Android **Kotlin + Jetpack Compose** con navegación mínima entre Biblioteca, Lector, Reproductor y Ajustes. Usa estados vacíos reales, etiquetados como no implementados donde corresponda; sin libros de ejemplo falsos.
4. Implementa el sistema de diseño inicial estilo **tormenta medianoche**, con tokens `#040A1C`, `#0A1633`, `#123A9E`, `#2F6BFF`, `#79DDFF`; superficies Glass discretas y tipografía legible.
5. Establece modelos de dominio y contratos básicos para libro, posición de lectura, lector de documentos y narración; no introduzcas llamadas reales a nube en esta fase.
6. Prepara infraestructura de pruebas mínima, documentación de arquitectura y configuración `.gitignore` segura.
7. Si puedes, crea un spike acotado para validar apertura de EPUB con Readium y evaluación de PDF; NO presentes PdfRenderer como extractor de texto sin prueba.
8. Trabaja en `feature/fase0-fundamentos` (o crea una rama equivalente si existe conflicto), manteniendo `main` estable.
9. Ejecuta `assembleDebug` y pruebas pertinentes. Si el entorno no tiene SDK/JDK o falla la compilación, explica qué faltó y entrega evidencia; no afirmes que compiló si no fue ejecutado.
10. No comiences todavía Fases A–E (biblioteca real, TTS, IA, caché) salvo cambios triviales necesarios para compilar y claramente indicados.

## Reglas innegociables

- Sin funciones simuladas para aparentar que están listas.
- Sin secretos, claves API, `.env`, `local.properties` ni keystores en Git.
- Sin subir documentos del usuario ni enviar texto de libros a APIs externas.
- No eliminar archivos, reinicializar Git ni hacer `force push` sin autorización.
- Evitar arquitectura excesivamente fragmentada; separar gradualmente cuando exista necesidad.
- Mantener compatibilidad y registrar decisiones técnicas en ADR.
- Cada cambio funcional requiere pruebas y manejo de error.

## Entrega al finalizar esta ejecución

Responde con: (1) qué hiciste realmente, (2) árbol de archivos principales, (3) versiones de dependencias, (4) rama/commits, (5) comandos y resultados de build/tests, (6) limitaciones de entorno, (7) lo que quedó explícitamente NO implementado y (8) propuesta concreta para la Fase A: importación y biblioteca.

**Comienza examinando el repositorio y el documento maestro. No hagas promesas de ejecución futura ni reportes ficticios.**
