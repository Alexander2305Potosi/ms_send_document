# Plan de Refactorización: Esquema Maestro para Homologación

## 1. Objetivo y Necesidad
Actualmente, el sistema gestiona reglas de homologación de manera independiente para cada base de datos o contexto. El objetivo es **centralizar las tablas de referencia y maestras** (`categoria_manual` y `pais_homologado`) en un único y nuevo esquema de base de datos (por ejemplo, `reference_data` o `esquema_maestros`). 

Esto permitirá reutilizar los mismos repositorios y la misma lógica de homologación tanto para el caso de uso de documentos (SOAP) como para el de Animales. La única diferenciación será lógica, filtrando las reglas pertinentes mediante una nueva columna de "Caso de Uso" (`use_case`).

---

## 2. Cambios a Nivel de Base de Datos (DDL)
1. **Nuevo Esquema:** Crear un esquema de base de datos dedicado para aislar las tablas maestras compartidas.
2. **Migración de Tablas:** Mover las tablas `categoria_manual` y `pais_homologado` a este nuevo esquema.
3. **Nueva Columna Discriminadora:** Agregar la columna `use_case` (`VARCHAR(50)`) a la tabla `pais_homologado`. Esta columna contendrá valores como `'Document'` o `'Animal'` (o los nombres formales de los Use Cases).

---

## 3. Cambios a Nivel de Entidades (Capa de Infraestructura)

**A. `CategoryManualEntity.java`**
*   Actualizar la anotación de la tabla para que apunte explícitamente al nuevo esquema.
    ```java
    @Table("reference_data.categoria_manual")
    public class CategoryManualEntity { ... }
    ```

**B. `CountryHomologatedEntity.java` (PaisHomologadoEntity)**
*   Actualizar la anotación de la tabla para el nuevo esquema.
*   Agregar el nuevo atributo `useCase` mapeado a la columna.
    ```java
    @Table("reference_data.pais_homologado")
    public class CountryHomologatedEntity {
        // ... otros campos
        
        @Column("use_case")
        private String useCase;
    }
    ```

---

## 4. Cambios a Nivel de Repositorio y Adaptadores

**A. Repositorios Spring Data R2DBC**
*   Modificar las consultas en `CountryHomologatedRepository` (y en `CategoryManualRepository` si aplica) para que obligatoriamente filtren por el nuevo campo.
    ```java
    // Ejemplo de método de consulta actualizado
    Mono<CountryHomologatedEntity> findByCodeAndUseCase(String code, String useCase);
    ```

**B. `HomologationR2dbcAdapter.java` (y clases derivadas)**
*   **Envío de Contexto:** Al invocar los métodos de resolución (`resolve(...)`), el adaptador deberá extraer del objeto de contexto (`DocumentHistoryDTO` o el request base) el valor del Caso de Uso (ej. `history.getUseCase()`) y pasarlo al repositorio.
*   **Consolidación de Conexiones:** Al mover estas tablas a un esquema neutral/maestro, la necesidad de tener repositorios ruteados por bases de datos separadas se elimina para las homologaciones. Se podrá consolidar el adaptador para que utilice una única conexión maestra de sólo lectura (Reference DB).
*   **Filtrado Dinámico:** Con esto, si entra un documento SOAP, el adaptador buscará `findByCodeAndUseCase("COL", "SOAP")`, garantizando que aplique las reglas de envío correctas, mientras que si es Animal buscará `findByCodeAndUseCase("COL", "Animal")`.

---

## 5. Hoja de Ruta de Implementación
1. **Estructura DB:** Escribir los scripts SQL (`schema.sql` / migraciones) creando el nuevo esquema, moviendo las tablas y añadiendo la columna `use_case`.
2. **Código Entidades:** Modificar las entidades Java (anotaciones `@Table` y el nuevo campo).
3. **Capa R2DBC:** Actualizar los repositorios reactivos para soportar el nuevo filtro.
4. **Refactor de Adaptadores:** Actualizar `HomologationR2dbcAdapter` para recibir y utilizar el discriminador del caso de uso. Eliminar cualquier duplicación de adaptadores si el esquema dual (Strategy) para homologación ya no es necesario.
5. **Testing:** 
    * Actualizar `mocks.py` (si la BD es in-memory o simulada) o los scripts de creación de base de datos en las pruebas E2E.
    * Añadir casos de prueba donde se inserte la misma clave/país pero con distintos `use_case` y verificar que el código resuelve el correcto.
