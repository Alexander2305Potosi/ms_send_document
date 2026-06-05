# Proposal: Individual File Processing for ZIP Archives

## Overview
Currently, the microservice decompresses ZIP files but only processes and uploads the first file found. This proposal describes the changes needed to process **all** files within a ZIP sequentially, creating **individual audit records** for each file, while maintaining a **global state** for the parent document based on aggregated results and retry counts.

## Impacted File
`src/main/java/com/example/fileprocessor/domain/usecase/AbstractDocumentProcessingUseCase.java`

---

## Detailed Changes

### 1. `fetchAndValidate`
**Proposed Change:** Remove `.next()` and change the return type to `Flux<DocumentHistoryDTO>`. This ensures all files in the ZIP are emitted into the processing pipeline.

#### Proposed Code:
```java
private Flux<DocumentHistoryDTO> fetchAndValidate(DocumentHistoryDTO history) {
    return productRestGateway.getDocument(history.getProductId(), history.getBusinessDocumentId())
            .flatMapMany(file -> {
                DocumentHistoryDTO updatedHistory = history.toBuilder()
                        .content(file.getContent())
                        .size(file.getSize())
                        .contentType(file.getContentType())
                        .filename(file.getFilename())
                        .originFolder(file.getOriginFolder())
                        .originCountry(file.getOriginCountry())
                        .isZip(file.getIsZip())
                        .build();
                return this.decompress(updatedHistory);
            })
            .concatMap(h -> documentValidator.validate(h, true)
                    .onErrorMap(e -> {
                        // ... keep existing error mapping ...
                        return pe;
                    }))
            .switchIfEmpty(Mono.defer(() -> {
                if (Boolean.TRUE.equals(history.getIsZip())) {
                    return Mono.error(new ProcessingException(
                            ProcessingResultCodes.EMPTY_CONTENT.value(),
                            ProcessingResultCodes.EMPTY_CONTENT.name()));
                }
                return Mono.empty();
            }).flatMapMany(Mono::error))
            .onErrorResume(ProcessingException.class, e -> {
                // ... keep existing error mapping ...
                return Flux.error(pe);
            });
}
```

---

### 2. `processWithTracking`
**Proposed Change:**
1. Use `flatMapMany` to handle the `Flux` from `fetchAndValidate`.
2. Use `concatMap` to upload each file and **immediately** save its individual history record.
3. Use `collectList` to aggregate all responses to determine the final global state of the `document`.

#### Proposed Code:
```java
private Mono<FileUploadResponse> processWithTracking(Document doc, String traceId) {
    final DocumentHistoryDTO historyDto = DocumentHistoryDTO.fromDocument(doc);

    return persistencePort.lockDocumentForProcessing(doc.getId(), doc.getRetryCountSafe())
            .filter(rows -> rows > 0)
            .flatMapMany(unused -> fetchAndValidate(historyDto))
            .concatMap(h -> uploadDocument(h, doc.getId())
                    .map(resp -> (Boolean.TRUE.equals(doc.getIsZip()) && resp.getFilename() == null)
                            ? resp.toBuilder().filename(h.getFilename()).build()
                            : resp)
                    .flatMap(response -> {
                        // PERSISTENCE INDIVIDUAL: Save history for each specific file in the ZIP
                        return persistencePort.saveHistory(
                            syncHistoryDTO(doc, h, response)) 
                            .thenReturn(response);
                    })
            )
            .collectList()
            .flatMap(responses -> {
                // Logic to determine final state based on all responses
                boolean hasTechnicalRetry = responses.stream().anyMatch(FileUploadResponse::isTechnicalRetry);
                
                if (hasTechnicalRetry) {
                    return saveAuditOnly(doc, historyDto, responses, traceId)
                            .thenReturn(responses.get(0)); // Representative response
                } else {
                    return finalizeProcessing(doc, historyDto, responses, traceId)
                            .thenReturn(responses.get(0));
                }
            });
}
```

---

### 3. `syncHistoryDTO` and `finalizeProcessing`
**Proposed Change:** 
*   `syncHistoryDTO` now processes a **single** file response and a **single** `DocumentHistoryDTO`.
*   It must map the current `doc.getRetryCountSafe()` to the `retryCount` field in the history record.

#### Proposed `syncHistoryDTO`:
```java
private DocumentHistoryDTO syncHistoryDTO(Document doc, DocumentHistoryDTO fileHistory, FileUploadResponse response) {
    return fileHistory.toBuilder()
            .documentId(doc.getId())
            .state(calculateFileState(response))
            .retryCount(doc.getRetryCountSafe()) // Map current attempt number
            .filename(response.getFilename() != null ? response.getFilename() : fileHistory.getFilename())
            .syncStatus(response.getSyncStatus())
            .syncMessage(response.getMessage())
            .completedAt(Instant.now())
            .build();
}
```

#### Proposed `finalizeProcessing`:
Updates the `document` table based on the aggregated `List<FileUploadResponse>`.
```java
private Mono<Void> finalizeProcessing(Document doc, DocumentHistoryDTO history,
        List<FileUploadResponse> responses, String traceId) {
    String nextState = calculateNextState(doc, responses);
    doc.setState(nextState);
    doc.setSyncMessage(aggregateMessages(responses)); // Concatenated results

    return persistencePort.finalizeProcessingAtomically(
        syncGlobalHistory(doc, history, responses, nextState)
    );
}
```

---

### 4. State Aggregation Logic (`calculateNextState`)

The final state of the document is determined by the "most critical" error in the batch:

| Condition | Resulting State | Logic |
| :--- | :--- | :--- |
| **All Success** | `PROCESSED` | All responses are `success=true`. |
| **Any Business Error** | `BUSINESS_REJECTION` | At least one response has a `syncStatus` that is a Business Rule. |
| **Technical Error** | `PENDING` | At least one response is transient AND `retry_count < 3`. |
| **Technical Fatal** | `FAILED` | Technical error persists AND `retry_count >= 3`. |

---

## 5. Unit Testing Strategy

To ensure the correctness of the multi-file processing and retry logic, the test suite in `AbstractDocumentProcessingUseCaseTest.java` will be expanded with the following scenarios.

### Mocking Requirements
- **`ProductRestGateway`**: Mock to return a `ProductDocumentFile` with `isZip = true` and binary content of a valid ZIP file.
- **`RulesBussinesGateway`**: Mock to return `Mono.just(h)` for success or `Mono.error(ProcessingException)` for business rejections.
- **`DocumentPersistenceGateway`**: 
    - Verify `lockDocumentForProcessing` is called once.
    - Verify `saveHistory` is called **N times** per attempt (where N is the number of files in the ZIP).
    - Verify `finalizeProcessingAtomically` is called once at the end of the batch.

### Detailed Test Cases

| Scenario | Input | Mock Behavior | Expected Verifications | Final Document State |
| :--- | :--- | :--- | :--- | :--- |
| **Total Success** | ZIP with 2 files | Both files $\rightarrow$ `Succeed` | `saveHistory` called 2x; `retryCount` in history = 0. | `PROCESSED` |
| **Partial Business Failure** | ZIP with 2 files | File 1 $\rightarrow$ `Succeed`, File 2 $\rightarrow$ `PATTERN_MISMATCH` | `saveHistory` called 2x; One history record has `BUSINESS_REJECTION`. | `BUSINESS_REJECTION` |
| **Technical Retry (Succeeded on 2nd try)** | ZIP with 2 files | **Try 1**: File 2 $\rightarrow$ `TIMEOUT`. **Try 2**: Both $\rightarrow$ `Succeed`. | `saveHistory` called 4x total (2 per attempt). First 2 have `retryCount=0`, next 2 have `retryCount=1`. | `PROCESSED` |
| **Technical Exhaustion** | ZIP with 2 files | 3 consecutive attempts $\rightarrow$ `TIMEOUT` | `saveHistory` called 6x total. Final `finalizeProcessing` called with `retryCount=3`. | `FAILED` |
| **Empty ZIP** | ZIP with 0 files | `ZipDecompressor` returns empty Flux | `saveHistory` called 0x. `finalizeProcessing` called with `EMPTY_CONTENT` error. | `BUSINESS_REJECTION` / `FAILED` |

### Implementation Detail for ZIPs in Tests
Use a helper method to generate real ZIP bytes in memory to test the `ZipDecompressor` integration:
```java
private byte[] createZipBytes(String... filenames) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
        for (String name : filenames) {
            zos.putNextEntry(new ZipEntry(name));
            zos.write("dummy content".getBytes());
            zos.closeEntry();
        }
    }
    return baos.toByteArray();
}
```
