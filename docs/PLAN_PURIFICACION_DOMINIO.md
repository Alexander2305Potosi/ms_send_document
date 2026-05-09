# Plan de Purificación del Dominio y Cumplimiento de Arquitectura

Este plan detalla las acciones necesarias para eliminar las dependencias de infraestructura de las capas de **Model** y **Use Case**, asegurando que el dominio permanezca agnóstico a la tecnología y cumpla con las reglas de validación de Clean Architecture.

---

## 1. Objetivos
1.  **Eliminar Spring del Dominio**: Remover `MediaTypeFactory` y `TransactionalOperator` de las clases de dominio.
2.  **Inversión de Dependencia**: Utilizar Puertos (interfaces) para delegar las tareas de infraestructura.
3.  **Asegurar el Build**: Resolver los errores de Gradle que impiden la compilación por violación de capas.

---

## 2. Cambios de Código (Antes vs Después)

### 2.1 Inferencia de MIME Types
**Antes (`domain.util.ZipDecompressor`):**
Dependía directamente de `org.springframework.http.MediaTypeFactory`.

**Después:**
1. Se crea el puerto `com.example.fileprocessor.domain.port.out.MimeTypeResolver`.
2. `ZipDecompressor` recibe este resolver como parámetro.
3. La implementación reside en `infrastructure.helpers.SpringMimeTypeResolver`.

### 2.2 Manejo de Transacciones
**Antes (`domain.usecase.AbstractDocumentProcessingUseCase`):**
Utilizaba `TransactionalOperator` para orquestar la persistencia.

**Después:**
1. Se elimina `TransactionalOperator` del caso de uso.
2. La transaccionalidad se envuelve en el **Entrypoint** (ej: `ProductHandler`) o se delega a un decorador de infraestructura.
3. El caso de uso solo se enfoca en la lógica de negocio y la orquestación de puertos.

---

## 3. Automatización de Reglas (Arquitectura como Código)

Para evitar que estas violaciones vuelvan a ocurrir, implementaremos una prueba de **ArchUnit**. Esto automatiza el mensaje de error que viste en Gradle.

### Ejemplo de Regla ArchUnit (`src/test/java/com/example/fileprocessor/architecture/CleanArchitectureTest.java`):

```java
@AnalyzeClasses(packages = "com.example.fileprocessor")
public class CleanArchitectureTest {

    @ArchTest
    public static final ArchRule domain_should_not_depend_on_infrastructure = 
        noClasses().that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
        .orShould().dependOnClassesThat().resideInAPackage("org.springframework..");

    @ArchTest
    public static final ArchRule use_cases_should_only_depend_on_model_and_ports = 
        classes().that().resideInAPackage("..domain.usecase..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "..domain.entity..", "..domain.port..", "..domain.usecase..", 
            "java..", "reactor.core..", "org.slf4j.."); // Permitimos reactor y logs básicos
}
```

---

## 4. Beneficios
*   **Testabilidad**: Podemos probar los Casos de Uso sin necesidad de levantar contextos de Spring o bases de datos reales.
*   **Independencia**: Si mañana cambiamos de Spring WebFlux a otro framework, el corazón del negocio (Dominio) no sufrirá cambios.
*   **Cumplimiento**: El comando de Gradle `verify-build` pasará satisfactoriamente.
