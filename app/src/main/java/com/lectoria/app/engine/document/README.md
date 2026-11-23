# Adaptadores de documentos

`AndroidDocumentInspector` valida importaciones y obtiene metadata acotada en Fase A:
TXT (codificación), EPUB (ZIP/OPF/spine y portada raster) y PDF (apertura nativa,
primera página y número de páginas). No implementa `DocumentReader` ni extracción
textual PDF. El renderizador PDF requiere Android real para validar su ejecución.

Readium sigue siendo el candidato del lector EPUB en Fase B. El inspector de
importación no lo reemplaza ni promete conformidad EPUB completa. Un extractor
PDF independiente y sus licencias siguen pendientes.
