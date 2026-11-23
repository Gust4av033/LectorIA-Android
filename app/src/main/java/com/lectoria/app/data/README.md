# Adaptadores de datos

Fase A implementa Room (`local/`), DataStore (`LibraryPreferences`) y archivos
privados (`importing/`). `RoomBookRepository` cumple los contratos de dominio;
la UI accede a casos de uso. `SafSourceReader` abre documentos seleccionados con
permiso temporal de lectura; no solicita almacenamiento general ni conserva URI
como requisito para volver a abrir una copia importada.

El protocolo persistido distingue COPYING, VERIFIED, READY, UNAVAILABLE, FAILED y
DELETING. Room y filesystem no comparten una transacción atómica. Ver ADR-006 y
las pruebas de recuperación, deduplicación, reapertura y eliminación.
