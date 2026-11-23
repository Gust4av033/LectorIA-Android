# Adaptadores de datos

Reservado para Room, DataStore y archivos privados en Fase A. Los contratos están en
`domain/repository`; no hay repositorios en memoria ni libros simulados en Fase 0.
La UI no consulta almacenamiento ni solicita permisos. La importación deberá usar
estados recuperables: Room y filesystem no comparten una transacción atómica.
