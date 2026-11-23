# Dependencias de lectura — Fase B

| Componente | Uso | Licencia / fuente |
|---|---|---|
| Readium Kotlin Toolkit 3.1.2 shared + streamer | Abrir EPUB, reading order, TOC y extracción HTML textual | BSD 3-Clause, [LICENSE de la versión](https://github.com/readium/kotlin-toolkit/blob/3.1.2/LICENSE) |
| PDFBox Android 2.0.27.0 | Extracción de texto PDF independiente de PdfRenderer | Apache 2.0, [LICENSE](https://github.com/TomRoush/PdfBox-Android/blob/master/LICENSE.txt) y [NOTICE](https://github.com/TomRoush/PdfBox-Android/blob/master/NOTICE.txt) |
| Bouncy Castle 1.87, familia jdk15to18 | Dependencia del port PDFBox | MIT, [LICENSE](https://github.com/bcgit/bc-java/blob/main/LICENSE.html) |
| desugar_jdk_libs 2.1.5 | Compatibilidad Java requerida por Readium en Android | Apache 2.0 y avisos propios publicados por Google |

Los avisos Readium, Apache/PDFBox y MIT/Bouncy Castle se distribuyen en
`app/src/main/assets/licenses`. El NOTICE de PDFBox incluye los componentes de
fuentes/glyphs de su distribución. No se copió código de los SDK al proyecto.

PDFBox Android publica Bouncy Castle 1.72 como dependencia transitiva. Se fija
la familia compatible en 1.87, versión estable publicada en Maven Central al
consultar la metadata durante esta fase. El port PDFBox sigue en 2.0.27.0;
revisar su mantenimiento y las dependencias antes de una release de producción.
Los PDFs cifrados/protegidos no forman parte de esta implementación.

Las dependencias preexistentes conservan sus licencias y avisos propios. Esta
lista explica las incorporaciones del lector, sin afirmar una auditoría legal
o de seguridad exhaustiva de todas las transitivas.
