# File Processor Service - Code Quality Improvement Plan

## Executive Summary

The codebase follows hexagonal architecture with clear separation between domain, application, and infrastructure layers. The overall structure is sound, but several issues were identified across code smells, dead code, unused code paths, best practices violations, and debugging opportunities.

**Key Findings:**
- **HIGH severity**: 4 issues (duplicate code, error context loss, architecture anomalies)
- **MEDIUM severity**: 12 issues (unused code, inconsistent patterns, naming issues)
- **LOW severity**: 8 issues (minor improvements, cosmetic changes)

---

## 1. Code Smells

### 1.1 Duplicate Constants in ApiConstants (HIGH)
**Location**: `src/main/java/com/example/fileprocessor/infrastructure/entrypoints/rest/constants/ApiConstants.java:29-40`

**Description**: ApiConstants duplicates domain constants from `AsyncOperationStatus` using fully-qualified references instead of delegating:
```java
public static final String OPERATION_LOAD = com.example.fileprocessor.domain.entity.AsyncOperationStatus.OPERATION_LOAD;
```

This creates:
- Code duplication (single source of truth violated)
- Tight coupling between infrastructure and domain entities
- Maintenance hazard when domain constants change

**Severity**: HIGH
**Recommendation**: Remove the constants from ApiConstants that reference domain constants. Use the domain constant directly or create a single re-export in the infrastructure layer if import visibility is a concern.

---

### 1.2 Duplicate MediaTypeConstants in ZipArchive (MEDIUM)
**Location**: `src/main/java/com/example/fileprocessor/domain/entity/ZipArchive.java:31`

**Description**: Hardcoded string literals for image mime types instead of using existing `MediaTypeConstants`:
```java
".png", "image/png",
".jpg", "image/jpeg",
".gif", "image/gif",
```

`MediaTypeConstants` already defines `APPLICATION_PDF`, `APPLICATION_WORD`, etc., but not image types.

**Severity**: MEDIUM
**Recommendation**: Add image mime types to `MediaTypeConstants` and update `ZipArchive.EXT_TO_MIME` to reference them.

---

### 1.3 Conflicting Package Names (MEDIUM)
**Location**: `src/main/java/com/example/fileprocessor/application/`

**Description**: Two package name patterns exist:
- `application/app/service/config/` (singular `app`)
- `application/app-service/config/` (hyphenated `app-service`)

This is confusing and may indicate a copy-paste error or inconsistent naming convention.

**Severity**: MEDIUM
**Recommendation**: Standardize on one naming convention. The pattern `application.appservice.config` (all lowercase, no hyphens) follows Java conventions better.

---

### 1.4 Overly Complex SOAP Message Construction (MEDIUM)
**Location**: `src/main/java/com/example/fileprocessor/infrastructure/helpers/soap/mapper/SoapMapper.java:66-71`

**Description**: SOAP envelope is built using string concatenation:
```java
return ApiConstants.SOAP_HEADER_PREFIX
    + ApiConstants.SOAP_HEADER_ENVELOPE_START + SoapNamespaces.SOAP_ENVELOPE + "\"\n"
    ...
```

This approach is fragile, not type-safe, and makes XML validation difficult.

**Severity**: MEDIUM
**Recommendation**: Create a `SoapEnvelope` model class with JAXB annotations and marshal it properly, similar to how `UploadFileRequest` is handled.

---

### 1.5 Non-Immutable Configuration Objects (MEDIUM)
**Location**: `src/main/java/com/example/fileprocessor/infrastructure/helpers/config/ProcessorSettings.java`

**Description**: `ProcessorSettings` has mutable fields with setters despite being a configuration object:
```java
private long maxSize;
public void setMaxSize(long maxSize) { this.maxSize = maxSize; }
```

Spring `@ConfigurationProperties` with records is the modern approach.

**Severity**: MEDIUM
**Recommendation**: Convert to a Java record. Spring Boot supports record-based `@ConfigurationProperties` since 2.2.

---

### 1.6 Mixed Error Handling Patterns in SoapGatewayAdapter (HIGH)
**Location**: `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/soap/SoapGatewayAdapter.java:142`

**Description**: Inconsistent error handling - some errors propagate for circuit breaker to handle, others return `FileUploadResult`:
```java
// Timeout propagates - infrastructure failure
if (cause instanceof TimeoutException) { ... return Mono.error(...); }

// 4xx errors return as result - business failure
return Mono.just(toFileUploadResultError(...));

// Unknown errors propagate
return Mono.error(throwable);
```

**Severity**: HIGH
**Recommendation**: Create a clear distinction between infrastructure errors (connection, timeout, 5xx) that should propagate vs business errors (4xx, validation) that should return as results. Document this in the class.

---

### 1.7 Potential Timestamp Information Loss (LOW)
**Location**: `src/main/java/com/example/fileprocessor/infrastructure/helpers/soap/mapper/SoapMapper.java:54`

**Description**: Timestamp is generated in infrastructure rather than preserving request timestamp:
```java
Instant.now().toString()  // Timestamp generated in infrastructure
```

While this is acceptable for idempotency, it loses the original request's temporal context.

**Severity**: LOW
**Recommendation**: Consider accepting timestamp as a parameter or using the request's timestamp if available.

---

### 1.8 Inconsistent Logging Patterns

**Location**: Various files

**Description**: Logging is inconsistent across components:
- `ProductHandler.java:44-48` logs `doOnNext` after `thenMany(Mono.empty())` - this won't execute
- Some places use `log.info`, others use `log.error` with no context

**Severity**: MEDIUM

---

## 2. Dead Code

### 2.1 Unused Import in SoapGatewayAdapter
**Location**: `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/soap/SoapGatewayAdapter.java:6`
**Description**: `ExternalServiceResponse` imported but not used (line 146 uses `FileUploadResult` directly).
**Severity**: LOW

---

### 2.2 Unused Import in SoapMapper
**Location**: `src/main/java/com/example/fileprocessor/infrastructure/helpers/soap/mapper/SoapMapper.java:27`
**Description**: `IOException` imported but not used.
**Severity**: LOW

---

### 2.3 Unused Import in R2dbcProductRepository
**Location**: `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/R2dbcProductRepository.java:14`
**Description**: `UUID` imported but not used.
**Severity**: LOW

---

### 2.4 Unused Import in FileValidator
**Location**: `src/main/java/com/example/fileprocessor/domain/usecase/FileValidator.java:10`
**Description**: `Set` imported but the actual type `java.util.Set` is used via fully qualified name in line 111.
**Severity**: LOW

---

### 2.5 Unused Method in SoapEnvelopeWrapper
**Location**: `src/main/java/com/example/fileprocessor/infrastructure/helpers/soap/xml/SoapEnvelopeWrapper.java:51`
**Description**: `getJaxbContext()` returns `JAXBContext` but `jaxbContext` field is already accessible. The getter is redundant.
**Severity**: LOW

---

## 3. Unused Code Paths

### 3.1 Empty Switch Default in ProductStatusAggregator
**Location**: `src/main/java/com/example/fileprocessor/domain/usecase/ProductStatusAggregator.java:102-110`
**Description**: Switch statement has no default case:
```java
switch (doc.getStatus()) {
    case "SUCCESS" -> success++;
    case "FAILURE" -> failure++;
    // ... other cases
    // No default - unknown statuses are silently ignored
}
```
Unknown statuses are silently ignored rather than being logged or handled explicitly.

**Severity**: MEDIUM
**Recommendation**: Add a default case that logs unknown statuses for debugging and monitoring.

---

### 3.2 Switch Without Default in ProductHandler (Design Smell)
**Location**: `src/main/java/com/example/fileprocessor/infrastructure/entrypoints/rest/handler/ProductHandler.java:55`
**Description**: `if/else` chain for processor type has no final else:
```java
if (ApiConstants.PROCESSOR_S3.equalsIgnoreCase(processorType)) {
    // S3
} else {
    // Defaults to SOAP - no validation that it's a valid processor
}
```

If an invalid processor is passed, it silently defaults to SOAP with no warning.

**Severity**: MEDIUM
**Recommendation**: Validate processor type and return 400 Bad Request for unknown values.

---

### 3.3 ZIP Empty Check Returns Success
**Location**: `src/main/java/com/example/fileprocessor/domain/entity/ZipArchive.java:187-198`
**Description**: When a ZIP is empty, it's treated as SUCCESS:
```java
if (children.isEmpty()) {
    log.warn("ZIP {} is empty", zipDoc.getFilename());
    return documentRepository.updateStatus(..., DocumentStatus.SUCCESS.name(), ...);
}
```

An empty ZIP being "successful" may be questionable business logic.

**Severity**: LOW
**Recommendation**: Consider whether empty ZIP should be SUCCESS, SKIPPED, or a distinct status. Document the decision.

---

## 4. Best Practices Violations

### 4.1 Timestamp Information Loss on Error
**Location**: `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/restclient/ProductRestGatewayAdapter.java:106-109`
**Description**: Error thrown loses original traceId:
```java
throw new com.example.fileprocessor.domain.exception.CommunicationException(
    "Base64 decode failed for document: " + documentId,
    com.example.fileprocessor.domain.usecase.ProcessingResultCodes.INVALID_BASE64,
    null);  // traceId is null here!
```

**Severity**: HIGH
**Recommendation**: Pass traceId parameter to decodeSafe or include it in the exception context.

---

### 4.2 Error Context Loss in SoapMapper
**Location**: `src/main/java/com/example/fileprocessor/infrastructure/helpers/soap/mapper/SoapMapper.java:109-111`
**Description**: Exception wrapping loses original cause details:
```java
throw new SoapCommunicationException(
    "Failed to parse SOAP response: " + e.getMessage(),
    ProcessingResultCodes.INVALID_RESPONSE, traceId);  // No cause passed
```

**Severity**: MEDIUM
**Recommendation**: Pass the original exception as cause: `SoapCommunicationException(..., traceId, e)`.

---

### 4.3 Missing Circuit Breaker Configuration
**Location**: `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/soap/SoapGatewayAdapter.java`

**Description**: The code mentions "propagate for CB" (circuit breaker) but there's no actual circuit breaker implementation. Errors propagate but there's no fallback behavior or state management.

**Severity**: MEDIUM
**Recommendation**: Consider implementing a circuit breaker pattern (Resilience4j) or clarify that "CB" refers to external handling.

---

### 4.4 Inconsistent Base64 Handling
**Location**: `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/restclient/ProductRestGatewayAdapter.java:95-113`

**Description**: Content is decoded from Base64 to `byte[]`, but then stored in repository where it's re-encoded. This creates a round-trip: `Base64 -> byte[] -> Base64` for stored content.

**Severity**: MEDIUM
**Recommendation**: If content is stored as Base64, work with Base64 strings throughout. If stored as bytes, avoid re-encoding.

---

### 4.5 Missing Validation on Repository Responses
**Location**: `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/R2dbcProductDocumentRepository.java:65-70`

**Description**: `decodeContent` returns null for blank input, but `encodeContent` returns empty string for null input. This asymmetry could cause issues.

**Severity**: LOW
**Recommendation**: Ensure encode/decode are symmetric - decide on null vs empty string handling.

---

## 5. Debugging Opportunities

### 5.1 Silent Failures in InMemoryAsyncOperationRepository
**Location**: `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/async/InMemoryAsyncOperationRepository.java:38-40`

**Description**: When operation not found, logs warning but returns silently:
```java
if (current == null) {
    log.warn("Cannot update progress - operation not found: traceId={}", traceId);
    return null;  // Silently returns null
}
```

**Severity**: MEDIUM
**Recommendation**: Consider throwing `IllegalStateException` or returning a error Mono for debugging.

---

### 5.2 Missing TraceId in Error Messages
**Location**: `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/restclient/ProductRestGatewayAdapter.java:106`

**Description**: CommunicationException is created without traceId, making distributed tracing difficult.

**Severity**: HIGH

---

### 5.3 No Logging for Retry Attempts
**Location**: `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/soap/SoapGatewayAdapter.java:83-89`

**Description**: Retry attempts are logged but the log level and details could be improved:
```java
log.warn("Retrying SOAP call for traceId={}, attempt {}/{} (backoff={}ms)",
```

**Severity**: LOW

---

### 5.4 Missing Correlation ID in Some Error Paths
**Location**: `src/main/java/com/example/fileprocessor/domain/usecase/AbstractDocumentProcessingUseCase.java:183-184`

**Description**: Error path for ZIP extraction uses result's correlationId but document may not have one assigned yet.

**Severity**: MEDIUM

---

## Priority-Ordered Action Items

### P0 - Critical (Fix Immediately)

| # | Issue | Location | Description |
|---|-------|----------|-------------|
| 1 | Error context loss | `ProductRestGatewayAdapter.java:106` | CommunicationException created without traceId |
| 2 | Error context loss | `SoapMapper.java:109-111` | Exception wrapping loses cause chain |
| 3 | Error handling inconsistency | `SoapGatewayAdapter.java` | Infrastructure vs business error distinction unclear |

### P1 - High Priority

| # | Issue | Location | Description |
|---|-------|----------|-------------|
| 4 | Duplicate constants | `ApiConstants.java:29-40` | Duplicates domain constants instead of referencing |
| 5 | Silent failures | `InMemoryAsyncOperationRepository.java:38-40` | Operation not found returns silently |
| 6 | Potential race condition | `InMemoryAsyncOperationRepository.java` | No protection against concurrent modifications |

### P2 - Medium Priority

| # | Issue | Location | Description |
|---|-------|----------|-------------|
| 7 | Missing switch default | `ProductStatusAggregator.java:102` | Unknown statuses silently ignored |
| 8 | No processor validation | `ProductHandler.java:55` | Invalid processor silently defaults |
| 9 | Conflicting package names | `application/` | `app` vs `app-service` naming |
| 10 | Non-immutable config | `ProcessorSettings.java` | Has setters, should be record |
| 11 | Inconsistent Base64 handling | `ProductRestGatewayAdapter.java` | Round-trip encoding/decoding |
| 12 | Duplicate mime types | `ZipArchive.java:31` | Hardcoded instead of using constants |

### P3 - Low Priority

| # | Issue | Location | Description |
|---|-------|----------|-------------|
| 13 | Unused imports | Multiple files | Various unused imports |
| 14 | Complex SOAP construction | `SoapMapper.java:66` | String concatenation for XML |
| 15 | Empty ZIP returns success | `ZipArchive.java:187` | Business logic questionable |
| 16 | Unused getter | `SoapEnvelopeWrapper.java:51` | Redundant getJaxbContext() |

---

## Estimated Refactoring Effort

| Item | Effort | Complexity | Files Affected |
|------|--------|------------|----------------|
| Error context fixes (P0) | 2 hours | Low | 2 |
| Duplicate constants removal | 1 hour | Low | 1 |
| Silent failure handling | 3 hours | Medium | 1 |
| Package naming standardization | 4 hours | Medium | 2 |
| Switch default cases | 1 hour | Low | 2 |
| Config to record conversion | 2 hours | Low | 1 |
| Base64 handling fix | 4 hours | Medium | 3 |
| Remaining P3 items | 3 hours | Low | 6 |

**Total estimated effort**: 20 hours

---

## Architectural Notes

The hexagonal architecture is well-implemented overall:
- Domain entities are clean and focused
- Ports (interfaces) properly separate domain from infrastructure
- Driven adapters correctly implement port interfaces

Key areas for architectural improvement:
1. Consider introducing a circuit breaker library (Resilience4j) if not already present
2. Add distributed tracing configuration (e.g., Micrometer + OpenTelemetry)
3. Consider standardizing on immutable DTOs throughout