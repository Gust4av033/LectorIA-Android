# Adaptadores de documentos

`AndroidDocumentInspector` valida importaciones y obtiene metadata acotada en Fase A:
TXT (codificación), EPUB (ZIP/OPF/spine y portada raster) y PDF (apertura nativa,
primera página y número de páginas). No implementa `DocumentReader` ni extracción
textual PDF. El renderizador PDF requiere Android real para validar su ejecución.

En Fase B, `AndroidDocumentReader` implementa lectura real con sesiones por
formato. Readium 3.1.2 shared/streamer interpreta EPUB y extrae su texto para el
modo de lectura Compose. TXT usa un índice de offsets en memoria y streams
acotados. PDFBox Android extrae texto PDF; PdfRenderer solo dibuja las páginas.
La ausencia de texto se informa por página y no activa OCR.

`TextPagination` genera fragmentos de hasta 1600 caracteres sin partir pares
surrogate; `textFrom` los entrega como unidades narrables con TextLocator.
No hay EPUB descomprimido ni scratch PDF en disco. La imagen PDF solo vive
en memoria. Las sesiones y streams tienen dueño y cierre explícitos.
Límites, compatibilidad y licencias: `docs/architecture.md` ADR-009/010 y
`docs/third-party.md`.
