# Plan de Resiliencia, Trazabilidad y Recuperación (Carga Diaria)

Este documento detalla la arquitectura de recuperación, reintentos y auditoría para el *File Processor Service*.

---

## 1. Responsabilidad de Tablas (Separación de Auditoría)

### Tabla `documentos` (Estado Actual)
*   **Función:** Tablero de control del proceso actual.
*   **Modificaciones:** Solo se actualiza el campo `estado`, `reintentos` y `fecha_actualizacion`.
*   **Estados:** `PENDING` -> `IN_PROGRESS` -> `PROCESSED` / `FAILED`.

### Tabla `historico_documentos` (Trazabilidad Total)
*   **Función:** Libro de auditoría técnica y de negocio.
*   **Mensajería Detallada:** El campo `mensaje_error` debe ser descriptivo.
    *   *Ejemplo de Negocio:* "Regla de negocio: El archivo excede el tamaño máximo permitido para no ser enviado al API".
    *   *Ejemplo Técnico:* "Fallo de comunicación: Timeout al conectar con el servidor SOAP (Intento 2/3)".

---

## 2. Alcance: Solo Día en Curso
**Regla de Negocio:** El sistema solo procesa y recupera documentos cuya `fecha_creacion` sea el día de hoy.

---

## 3. Política de 3 Reintentos

### Lógica de Negocio (No enviados por Reglas)
*   Si falla validaciones (tamaño, origen, patrón):
    *   `documentos`: Estado final `FAILED` (o `REJECTED`).
    *   `historico_documentos`: Registro con el mensaje descriptivo de la regla incumplida. **No se reintenta.**

### Lógica Técnica (Errores de API/Red)
*   Si ocurre un error transitorio:
    *   **Si intentos < 3:** `documentos` vuelve a `PENDING` (reintentos++). Se guarda traza con el número de intento.
    *   **Si intentos == 3:** `documentos` pasa a `FAILED`. Se guarda traza final del fallo.

---

## 4. Recuperación de Bloqueos Huérfanos (Stale Locks)
Al iniciar la carga, se buscan documentos de **hoy** que lleven más de 15 minutos en `IN_PROGRESS` y se regresan a `PENDING` para asegurar la continuidad operativa ante caídas del sistema.

---

## 5. Consistencia Transaccional
Se utilizará `TransactionalOperator` para asegurar que el cambio de estado en `documentos` y el registro en `historico_documentos` ocurran en una misma transacción atómica.
