# Plan: Nuevo Esquema de Homologación (Reference Data)

## 1. Objetivo
Unificar el mapeo de valores estáticos (homologación de datos) que actualmente se encuentra "quemado" en el código (ej: estados de SOAP, tipos de documentos, tipologías de animales) hacia un modelo administrable en Base de Datos.

## 2. Modelo de Datos Propuesto
Crear un esquema centralizado (ej: `esquema_maestro` o `reference_data`) con una tabla unificada `homologacion_campos` o similar.

**Tabla propuesta: `homologacion_campos`**
- `id` (PK)
- `dominio` (ej: 'ANIMALES', 'PRODUCTOS')
- `sistema_origen` (ej: 'REST_V1')
- `sistema_destino` (ej: 'SOAP_V2')
- `nombre_campo` (ej: 'tipo_documento', 'estado_civil')
- `valor_origen` (ej: 'CC', 'VIVO')
- `valor_destino` (ej: '1', 'A')
- `activo` (boolean)

## 3. Fases de Implementación

### Fase 1: Entidades y Puertos (Domain)
1. Crear la entidad `HomologationRule` en `domain/entity`.
2. Crear el puerto de salida `HomologationRepository` en `domain/port/out` para consultar las reglas por dominio y campo.

### Fase 2: Adaptadores de Persistencia (Infrastructure)
1. Crear un adaptador R2DBC `HomologationPersistenceAdapter` que implemente `HomologationRepository`.
2. Crear la configuración en `master-schema.sql` (para pruebas E2E) con los inserts de las homologaciones actuales (para que no se rompan las pruebas existentes).

### Fase 3: Integración con la Lógica de Negocio
1. Inyectar `HomologationRepository` en las estrategias de metadatos (ej: `AnimalMetadataStrategy` y `SoapMetadataStrategy`).
2. Reemplazar los condicionales o mapas en memoria actuales por llamadas asíncronas (`Mono`/`Flux`) a la base de datos a través del repositorio.
3. Configurar caché en memoria local (ej. Caffeine o un `Map` reactivo estático que se refresque periódicamente) para evitar golpear la BD por cada documento procesado.

### Fase 4: Pruebas (Testing)
1. Ajustar el mock de inicialización de BD (`master-schema.sql`).
2. Correr la suite E2E completa para garantizar que la generación de los XMLs para SOAP utiliza la homologación de la tabla correctamente.
