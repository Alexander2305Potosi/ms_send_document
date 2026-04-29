# Unused Code Report

This report lists all unused code found in `src/main/java` of the file-processor-service project.

---

## 1. UNUSED CLASSES

### 1.1 FileUploadProperties - Never Used

**File Path:** `src/main/java/com/example/fileprocessor/infrastructure/helpers/config/FileUploadProperties.java`

**Type of Issue:** Unused class - never instantiated or referenced anywhere in the codebase

**Problem:** 
- This is a record implementing `FileValidationConfig` 
- Only `ProcessorSettings` implements `FileValidationConfig` and IS actively used via `ProcessorConfig`
- `FileUploadProperties` is registered as a Spring `@ConfigurationProperties` but is NEVER injected into any class

**What Should Reference It:** Nothing - all validation config is handled via `ProcessorSettings` through `ProcessorConfig`

**Recommendation:** DELETE

---

### 1.2 AsyncOperationRepository Interface - All Methods Never Called

**File Path:** `src/main/java/com/example/fileprocessor/domain/port/out/AsyncOperationRepository.java`

**Type of Issue:** Unused interface - implemented but none of its methods are ever called

**Problem:**
- Interface defines 5 methods: `save`, `updateProgress`, `incrementProgress`, `markCompleted`, `findByTraceId`
- Only `InMemoryAsyncOperationRepository` implements this interface
- NO other code in the entire codebase calls ANY of these methods
- The interface is essentially dead code

**What Should Reference It:** If async operation tracking is needed, it should be used by handlers/controllers that expose operation status to clients

**Recommendation:** DELETE (and its implementation `InMemoryAsyncOperationRepository` as well)

---

### 1.3 InMemoryAsyncOperationRepository - Never Injected

**File Path:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/async/InMemoryAsyncOperationRepository.java`

**Type of Issue:** Unused Spring component - `@Component` annotated but never injected anywhere

**Problem:**
- Has `@Component` annotation
- Implements `AsyncOperationRepository` which is unused
- Nothing injects this repository - it is never used by any handler or use case

**What Should Reference It:** Nothing - the async status tracking feature appears to have been started but never completed

**Recommendation:** DELETE

---

## 2. UNUSED SPRING BEANS / CONFIGURATIONS

### 2.1 WebFluxConfig - Never Used

**File Path:** `src/main/java/com/example/fileprocessor/infrastructure/helpers/config/WebFluxConfig.java`

**Type of Issue:** Unused configuration class - registered as `@Configuration` but never imported or used

**Problem:**
- Implements `WebFluxConfigurer`
- Only has one method: `configureHttpMessageCodecs` setting `maxInMemorySize` to 10MB
- No class references `WebFluxConfig` anywhere

**What Should Reference It:** Spring Boot auto-configuration should pick this up if properly configured, but no evidence it's needed

**Recommendation:** DELETE (or investigate why it was created and if it's actually needed)

---

### 2.2 AwsConfig - Profile-Gated Bean Never Used

**File Path:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/aws/config/AwsConfig.java`

**Type of Issue:** Unused configuration - creates S3AsyncClient bean but nothing uses it

**Problem:**
- Has `@Profile("s3")` annotation
- Creates `S3AsyncClient` bean via `@Bean` method `s3AsyncClient`
- `S3GatewayAdapter` (also `@Profile("s3")`) accepts `S3AsyncClient` but I need to verify if S3GatewayAdapter is ever injected

**What Should Reference It:** `S3GatewayAdapter` should use the S3AsyncClient bean

**Recommendation:** INVESTIGATE - If S3 profile is not enabled or S3GatewayAdapter is not used, delete this too

---

### 2.3 GracefulShutdownManager - Public Methods Never Called

**File Path:** `src/main/java/com/example/fileprocessor/infrastructure/helpers/shutdown/GracefulShutdownManager.java`

**Type of Issue:** Spring component with unused public API

**Problem:**
- Has `@Component` annotation
- Listens for `ContextClosedEvent` (this part works)
- BUT public methods `isDraining()`, `elapsedSinceShutdown()`, `hasRemainingTime()` are NEVER called by any other class
- These methods appear to be half-implemented shutdown hooks

**What Should Reference It:** If graceful shutdown coordination is needed, some other component should call these methods

**Recommendation:** DELETE these unused methods, or implement proper shutdown coordination if intended

---

## 3. UNUSED CONSTANTS

### 3.1 ApiConstants - Multiple Unused Constants

**File Path:** `src/main/java/com/example/fileprocessor/infrastructure/entrypoints/rest/constants/ApiConstants.java`

**Type of Issue:** Unused constants (class itself is used)

**Unused Constants:**
```java
public static final String STATUS_FAILED = "FAILED";           // Never referenced
public static final String MSG_NOT_FOUND = "Operation not found for traceId: ";  // Never referenced
public static final String SOAP_STATUS_OK = "OK";              // Never referenced
public static final String EXTENSION_ZIP = "zip";            // Never referenced (duplicated in ProductDocumentInfo)
public static final String PATH_DOUBLE_DOT = "..";            // Never referenced
public static final String PATH_SLASH = "/";                   // Never referenced
public static final String PATH_BACKSLASH = "\\";             // Never referenced
public static final String MSG_S3_NOT_AVAILABLE = "...";       // Never referenced
public static final String MSG_UNKNOWN_PROCESSOR = "...";     // Never referenced
```

**What Should Reference Them:** If these constants represent API contracts or shared values, they should be used; otherwise they're dead code

**Recommendation:** DELETE unused constants (9 total)

---

### 3.2 RestApiPaths - Unused Path Constant

**File Path:** `src/main/java/com/example/fileprocessor/infrastructure/entrypoints/rest/constants/RestApiPaths.java`

**Type of Issue:** Unused constant

```java
public static final String API_V1_OPERATIONS_STATUS = "/api/v1/operations/{traceId}/status";
```

**Problem:** Defined but never used in `ProductRoutes` or anywhere else

**Recommendation:** DELETE

---

## 4. UNUSED METHODS

### 4.1 SoapMapper.toSoapXml - Only Used Internally

**File Path:** `src/main/java/com/example/fileprocessor/infrastructure/helpers/soap/mapper/SoapMapper.java`

**Type of Issue:** Public method only called internally

**Problem:**
- `toSoapXml` is a public method
- It's only called by `toFullSoapMessage` within the same class
- External code only calls `toFullSoapMessage`

**What Should Reference It:** Could be kept if it's meant to be part of a public API, but since it's only used internally, it's a candidate for private

**Recommendation:** MAKE PRIVATE (change visibility)

---

### 4.2 ProductStatusAggregator.calculateStatus - Never Called

**File Path:** `src/main/java/com/example/fileprocessor/domain/usecase/ProductStatusAggregator.java`

**Type of Issue:** Unused public static method

**Problem:**
- `public static ProductStatus calculateStatus(List<ProductDocumentToProcess> documents)` exists
- NO other class calls this method - not even `createSummary` which has its own inline logic

**What Should Reference It:** This appears to be duplicated logic - `createSummary` has its own inline calculation

**Recommendation:** DELETE (it's a duplicate of logic in `createSummary`)

---

## 5. UNUSED IMPORTS

### 5.1 SoapCommunicationException Unused in SoapGatewayAdapter

**File Path:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/soap/SoapGatewayAdapter.java`

**Type of Issue:** Unused import (the exception is thrown by SoapMapper, not this class)

**Problem:**
- `SoapCommunicationException` is imported
- But `SoapGatewayAdapter` never throws this exception directly
- The exception IS thrown by `SoapMapper.fromSoapXml` which SoapGatewayAdapter calls

**What Should Reference It:** This import can be removed as SoapGatewayAdapter doesn't directly throw SoapCommunicationException

**Recommendation:** REMOVE IMPORT (line 10)

---

## 6. UNUSED BEAN IMPLEMENTATIONS

### 6.1 S3GatewayAdapter - Profile-Gated, Possibly Unused

**File Path:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/aws/S3GatewayAdapter.java`

**Type of Issue:** Profile-gated component that may never be used

**Problem:**
- Has `@Profile("s3")` annotation
- Implements `FileGateway` interface
- `SoapGatewayAdapter` is the default `FileGateway` implementation
- Unless the 's3' profile is explicitly enabled, this never gets registered

**What Should Reference It:** `DomainConfig` should conditionally create `S3DocumentProcessingUseCase` only when S3 profile is active

**Recommendation:** INVESTIGATE - Verify if S3 profile is ever enabled in any configuration

---

## 7. SUMMARY TABLE

| File | Type of Issue | Recommendation |
|------|--------------|----------------|
| `FileUploadProperties.java` | Unused class | DELETE |
| `AsyncOperationRepository.java` | Unused interface | DELETE |
| `InMemoryAsyncOperationRepository.java` | Unused component | DELETE |
| `WebFluxConfig.java` | Unused configuration | DELETE |
| `GracefulShutdownManager.java` | Unused public methods | DELETE methods |
| `ApiConstants.java` (9 constants) | Unused constants | DELETE constants |
| `RestApiPaths.API_V1_OPERATIONS_STATUS` | Unused constant | DELETE |
| `SoapMapper.toSoapXml` | Only internal use | MAKE PRIVATE |
| `ProductStatusAggregator.calculateStatus` | Unused method | DELETE |
| `SoapGatewayAdapter.java` | Unused import | REMOVE IMPORT |

---

## 8. SAFE TO DELETE (No Dependencies)

1. **FileUploadProperties.java** - No references
2. **AsyncOperationRepository.java** - No references  
3. **InMemoryAsyncOperationRepository.java** - No references
4. **WebFluxConfig.java** - No references
5. **9 unused ApiConstants** - No references
6. **API_V1_OPERATIONS_STATUS** - No references
7. **ProductStatusAggregator.calculateStatus** - No references

---

## 9. NEEDS INVESTIGATION BEFORE DELETION

1. **AwsConfig.java** - Verify S3 profile usage
2. **S3GatewayAdapter.java** - Verify S3 profile usage  
3. **GracefulShutdownManager.java** - Understand if shutdown coordination was intended

---

*Report generated by analyzing all 61 Java files in src/main/java*
