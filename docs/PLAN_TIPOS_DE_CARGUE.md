# Plan de Implementación: Tipos de Cargue (Histórico vs Diario) y Casos de Uso de Negocio

## 1. Objetivo
Diferenciar los procesos de sincronización y procesamiento de documentos basándose en dos dimensiones:
1.  **Tipo de Cargue:** `DIARIO` (Batch del día) vs `HISTORICO` (Lotes masivos o recuperación).
2.  **Caso de Uso de Negocio:** Identificador funcional (ej: `retention`, `extract`) que determina el destino y las reglas.

---

## 2. Definición de Parámetros
Se añadirán dos parámetros obligatorios/opcionales a los endpoints de ejecución:

| Parámetro | Valores | Descripción |
|-----------|---------|-------------|
| `loadType` | `DIARIO`, `HISTORICO` | Define la ventana temporal y prioridad. |
| `useCase` | `retention`, `extract`, etc. | Define el proceso de negocio y mapea al gateway técnico. |

---

## 3. Cambios en el Modelo de Datos

### 3.1. Tabla `documentos`
Se asegurará de que el campo `caso_uso` almacene el identificador de negocio (`retention`, `extract`) en lugar del nombre del gateway técnico (`soap`, `s3`). El mapeo al gateway se hará vía configuración.

### 3.2. Tabla `productos`
Añadir columna `tipo_cargue` para diferenciar de dónde provienen los productos en la base de datos maestra.

---

## 4. Arquitectura de Endpoints

### 4.1. Sincronización (`POST /api/v1/products/sync`)
Permitirá disparar la sincronización filtrando por tipo de cargue y caso de uso.
*   **Query Params:** `?loadType=DIARIO&useCase=retention`

### 4.2. Procesamiento (`GET /api/v1/products`)
Lanzará el proceso de envío solo para los documentos que coincidan con los parámetros.
*   **Query Params:** `?loadType=HISTORICO&useCase=extract&processor=s3`

---

## 5. Mapeo Caso de Uso -> Gateway
Se utilizará el `application.yml` para desacoplar el nombre del proceso de negocio del protocolo técnico:

```yaml
app:
  use-cases:
    retention:
      processor: soap
      load-strategy: incremental
    extract:
      processor: s3
      load-strategy: batch
```

---

## 6. Lógica de Reanudación y Monitoreo
Los endpoints de estado creados anteriormente (`/status`) también deberán aceptar estos filtros para devolver el progreso específico:
*   `GET /api/v1/products/processing/status?useCase=retention&loadType=DIARIO`

---

## 7. Restricción de Concurrencia (Simplex)
Dado que no se contempla ejecución en paralelo, se implementará un bloqueo semafórico (Lock) basado en una tabla de control o un bean `@Service` con estado, que impida lanzar un nuevo proceso si hay uno en `IN_PROGRESS` para el mismo `useCase` y `loadType`.

---

## 8. Pasos de Implementación
1.  Actualizar `ApiConstants` con los nuevos parámetros y valores.
2.  Modificar `DocumentRepository` para filtrar por `tipo_cargue` y `caso_uso`.
3.  Actualizar `SyncDocumentsUseCase` para recibir estos parámetros y propagarlos.
4.  Ajustar `AbstractDocumentProcessingUseCase` para validar los filtros.
5.  Crear un `ProcessLockService` reactivo para gestionar la exclusividad.
