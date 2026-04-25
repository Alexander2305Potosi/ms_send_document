# File Processor Service

Microservicio reactivo basado en Spring WebFlux que obtiene productos con sus documentos asociados desde una API REST externa y los envia a un servicio SOAP externo.

## Arquitectura (Clean Architecture Light)

El proyecto sigue **Clean Architecture simplificada** con 2 capas principales. La capa de **dominio es pura Java** (sin dependencias de frameworks), y la capa de **infraestructura** provee los beans Spring.

```
com.example.fileprocessor/
├── domain/                    # Capa de dominio (independiente de frameworks)
│   ├── entity/               # Entidades de negocio
│   │   ├── ProductToProcess.java        # Producto en BD
│   │   ├── ProductDocumentToProcess.java # Documento de producto en BD
│   │   ├── ProductInfo.java             # Producto desde REST API
│   │   ├── ProductDocumentInfo.java    # Documento dentro de ProductInfo
│   │   ├── FileData.java               # Datos de archivo para SOAP
│   │   ├── FileUploadResult.java       # Resultado de upload
│   │   ├── SoapCommunicationLog.java    # Log de comunicacion SOAP
│   │   ├── SoapRequest.java
│   │   ├── SoapResponse.java
│   │   └── ZipArchive.java             # ZIP con documentos extraibles
│   ├── usecase/              # Casos de uso (logica de negocio)
│   │   ├── LoadProductsUseCase.java          # Carga productos y documentos
│   │   ├── ProcessProductDocumentsUseCase.java # Procesa documentos pendientes
│   │   └── FileValidator.java
│   ├── port/
│   │   ├── in/
│   │   │   └── FileValidationConfig.java
│   │   └── out/
│   │       ├── ProductRestGateway.java         # Puerto para API REST productos
│   │       ├── ProductRepository.java          # Puerto para productos
│   │       ├── ProductDocumentRepository.java  # Puerto para documentos de productos
│   │       └── ExternalSoapGateway.java
│   └── exception/            # Excepciones de dominio
│       ├── DomainException.java
│       └── FileValidationException.java
│
└── infrastructure/            # Capa de infraestructura (frameworks)
    ├── config/               # Configuracion (Properties, Beans)
    │   ├── DomainConfig.java          # Beans de casos de uso
    │   ├── DatabaseInitializer.java   # Schema y crash recovery
    │   └── WebFluxConfig.java
    ├── rest/                 # Adapter REST (entrada y Salida)
    │   ├── adapter/
    │   │   └── ProductRestGatewayImpl.java  # Cliente REST para productos
    │   ├── config/
    │   │   └── DocumentRestProperties.java
    │   ├── controller/
    │   │   └── ProductController.java
    │   └── exception/
    │       └── GlobalErrorHandler.java
    └── soap/                 # Adapter SOAP (salida)
        ├── adapter/
        │   └── ExternalSoapGatewayImpl.java
        ├── config/
        │   └── SoapProperties.java
        ├── mapper/
        │   └── SoapMapper.java
        ├── xml/
        │   ├── SoapEnvelopeWrapper.java
        │   ├── SoapNamespaces.java
        │   └── model/
        │       ├── UploadFileRequest.java
        │       └── UploadFileResponse.java
        └── exception/
            └── SoapCommunicationException.java
```

### Reglas de Dependencia

- **Domain** no depende de ninguna otra capa (puro Java, sin frameworks, sin Spring)
- **Domain** no contiene anotaciones de framework (`@Component`, `@Service`, etc.)
- **Infrastructure** depende de Domain (acceso a casos de uso y entidades)
- **Infrastructure** expone los beans de dominio via `DomainConfig.java`

## Modelo de Datos

### Producto
Un **Producto** (ej. Laptop, TV, Monitor) es la entidad raiz que contiene multiples documentos asociados.

### Tablas

**products_to_process**
| Campo | Tipo | Descripcion |
|-------|------|-------------|
| product_id | VARCHAR(255) | PK |
| name | VARCHAR(500) | Nombre del producto |
| status | VARCHAR(50) | Estado del producto |
| trace_id | VARCHAR(255) | UUID de traza |
| created_at | TIMESTAMP | Fecha de creacion |
| processed_at | TIMESTAMP | Fecha de procesamiento |

**product_documents_to_process**
| Campo | Tipo | Descripcion |
|-------|------|-------------|
| document_id | VARCHAR(255) | PK |
| product_id | VARCHAR(255) | FK a products_to_process |
| filename | VARCHAR(255) | Nombre del archivo |
| origin | VARCHAR(500) | Origen (ej. folderA/incoming) |
| status | VARCHAR(50) | PENDING, PROCESSING, SUCCESS, FAILURE, RETRY, SKIPPED |
| created_at | TIMESTAMP | Fecha de creacion |
| processed_at | TIMESTAMP | Fecha de procesamiento |
| trace_id | VARCHAR(255) | UUID de traza |
| soap_correlation_id | VARCHAR(255) | ID de correlacion SOAP |
| error_code | VARCHAR(100) | Codigo de error |

**soap_communication_log**
| Campo | Tipo | Descripcion |
|-------|------|-------------|
| trace_id | VARCHAR(255) | PK |
| status | VARCHAR(50) | SUCCESS o FAILURE |
| retry_count | INT | Numero de reintentos |
| error_code | VARCHAR(100) | Codigo de error |
| filename | VARCHAR(255) | Nombre del archivo |
| created_at | TIMESTAMP | Fecha del log |

## API Endpoints

### POST /api/v1/products/load

Carga productos y sus documentos asociados desde la API REST externa.

**Flujo:**
1. Consulta `GET /api/products` para obtener lista de productos con documentos
2. Guarda cada producto en `products_to_process`
3. Guarda los documentos en `product_documents_to_process` con `status=PENDING`

**Response:**
```json
{
  "status": "LOADING",
  "message": "Product loading from REST API started",
  "traceId": "uuid-generado",
  "success": true
}
```

### GET /api/v1/products

Procesa los documentos pendientes de todos los productos.

**Flujo:**
1. Consulta `product_documents_to_process` donde `status=PENDING`
2. Por cada documento:
   - `claimDocument()` - cambia status a `PROCESSING` (si esta en PENDING)
   - Obtiene contenido del documento via `GET /api/products/{productId}/documents/{documentId}`
   - Envía a SOAP
   - Actualiza status: SUCCESS, FAILURE, RETRY o SKIPPED

**Resiliencia:** Si el MS cae durante el procesamiento:
- El documento queda en `status=PROCESSING`
- Al reiniciar, `DatabaseInitializer` lo restaura a `PENDING`
- Solo ese documento se reprocesa (no se duplican envios SOAP)

**Response:**
```json
{
  "status": "PROCESSING",
  "message": "Pending product documents processing started",
  "traceId": "uuid-generado",
  "success": true
}
```

## Reintentos SOAP

El servicio implementa **3 reintentos maximos** con backoff exponencial:
- **Escenarios reintentables**: Timeout, errores 5xx
- **Delay**: 1s, 2s, 4s entre intentos

## Compilacion y Ejecucion

```bash
# Compilar
./gradlew clean build

# Ejecutar tests
./gradlew test

# Reporte de coverage
./gradlew jacocoTestReport
```

## Mocks Disponibles

### Mock REST de Productos (Java)
```bash
./scripts/start-product-mock.sh
```

### Mock REST de Productos (Mockoon Desktop)
Importar `mockoon/document-rest-mock.json` en Mockoon Desktop.

### Mock SOAP
```bash
./scripts/start-mock.sh
```

## Configuracion

| Variable | Default | Descripcion |
|----------|---------|-------------|
| `DOCUMENT_REST_ENDPOINT` | `http://localhost:3001` | Endpoint de API REST de productos |
| `SOAP_ENDPOINT` | `http://localhost:9000/soap/fileservice` | Endpoint del servicio SOAP |

## Changelog

### 2026-04-25 - Refactorizacion Product-Centric
- **Nuevo:** Entidades ProductToProcess, ProductDocumentToProcess, ProductInfo, ProductDocumentInfo
- **Nuevo:** Puertos ProductRestGateway, ProductRepository, ProductDocumentRepository
- **Nuevo:** Use cases LoadProductsUseCase, ProcessProductDocumentsUseCase
- **Nuevo:** Controlador ProductController con endpoints /api/v1/products/load y /api/v1/products
- **Nuevo:** Mock ProductRestMock y script start-product-mock.sh
- **Eliminado:** FileController, LoadDocumentsUseCase, ProcessFileUseCase (dominio anterior)
- **Actualizado:** DatabaseInitializer con nuevas tablas y crash recovery
- **Actualizado:** Postman y Mockoon con nueva estructura de productos
