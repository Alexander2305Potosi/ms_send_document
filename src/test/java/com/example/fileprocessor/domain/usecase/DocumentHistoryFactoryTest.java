package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.exception.ProcessingException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentHistoryFactoryTest {

    @Test
    void calculateNextStateWhenResponsesIsEmptyReturnsFailed() {
        DocumentHistoryFactory.ProcessingConclusion conclusion =
                DocumentHistoryFactory.calculateNextState(1, Collections.emptyList());

        assertEquals(ProcessingResultCodes.FAILED.name(), conclusion.nextState());
        assertEquals(1, conclusion.nextRetryCount());
    }

    @Test
    void calculateNextStateWhenAllResponsesAreSuccessReturnsProcessed() {
        List<FileUploadResponse> responses = List.of(
                FileUploadResponse.builder().success(true).build(),
                FileUploadResponse.builder().success(true).build()
        );
        DocumentHistoryFactory.ProcessingConclusion conclusion =
                DocumentHistoryFactory.calculateNextState(1, responses);

        assertEquals(ProcessingResultCodes.PROCESSED.name(), conclusion.nextState());
        assertEquals(1, conclusion.nextRetryCount());
    }

    @Test
    void calculateNextStateWhenAnyResponseIsBusinessRuleReturnsBusinessRejection() {
        List<FileUploadResponse> responses = List.of(
                FileUploadResponse.builder().success(true).build(),
                FileUploadResponse.builder().success(false).syncStatus(ProcessingResultCodes.PATTERN_MISMATCH.name()).build()
        );
        DocumentHistoryFactory.ProcessingConclusion conclusion =
                DocumentHistoryFactory.calculateNextState(1, responses);

        assertEquals(ProcessingResultCodes.BUSINESS_REJECTION.name(), conclusion.nextState());
        assertEquals(1, conclusion.nextRetryCount());
    }

    @Test
    void calculateNextStateWhenHasTransientAndUnderMaxRetriesReturnsPendingAndIncrementsRetry() {
        List<FileUploadResponse> responses = List.of(
                FileUploadResponse.builder().success(true).build(),
                FileUploadResponse.builder().success(false).syncStatus(ProcessingResultCodes.GATEWAY_TIMEOUT.name()).build()
        );
        DocumentHistoryFactory.ProcessingConclusion conclusion =
                DocumentHistoryFactory.calculateNextState(1, responses);

        assertEquals(ProcessingResultCodes.PENDING.name(), conclusion.nextState());
        assertEquals(2, conclusion.nextRetryCount());
    }

    @Test
    void calculateNextStateWhenHasTransientAndAtMaxRetriesReturnsFailedWithoutIncrement() {
        List<FileUploadResponse> responses = List.of(
                FileUploadResponse.builder().success(true).build(),
                FileUploadResponse.builder().success(false).syncStatus(ProcessingResultCodes.GATEWAY_TIMEOUT.name()).build()
        );
        DocumentHistoryFactory.ProcessingConclusion conclusion =
                DocumentHistoryFactory.calculateNextState(3, responses);

        assertEquals(ProcessingResultCodes.FAILED.name(), conclusion.nextState());
        assertEquals(3, conclusion.nextRetryCount());
    }

    @Test
    void calculateNextStateWhenHasNonTransientNonBusinessErrorReturnsFailed() {
        List<FileUploadResponse> responses = List.of(
                FileUploadResponse.builder().success(false).syncStatus(ProcessingResultCodes.SOURCE_NOT_FOUND.name()).build()
        );
        DocumentHistoryFactory.ProcessingConclusion conclusion =
                DocumentHistoryFactory.calculateNextState(1, responses);

        assertEquals(ProcessingResultCodes.FAILED.name(), conclusion.nextState());
        assertEquals(1, conclusion.nextRetryCount());
    }

    @Test
    void syncHistoryDTOMapsCorrectData() {
        Document doc = Document.builder()
                .id(123L)
                .useCase("SOAP")
                .retryCount(2)
                .build();
        DocumentHistoryDTO fileHistory = DocumentHistoryDTO.builder()
                .filename("inner.xml")
                .build();
        FileUploadResponse response = FileUploadResponse.builder()
                .success(true)
                .filename("inner_updated.xml")
                .syncStatus("OK")
                .message("Successfully uploaded")
                .attemptCount(3)
                .build();

        DocumentHistoryDTO result = DocumentHistoryFactory.syncHistoryDTO(doc, fileHistory, response);

        assertEquals(123L, result.getDocumentId());
        assertEquals(ProcessingResultCodes.PROCESSED.name(), result.getState());
        assertEquals("SOAP", result.getUseCase());
        assertEquals(3, result.getRetryCount());         // Uses response.attemptCount
        assertEquals(2, result.getBusinessRetryCount()); // Uses doc.retryCount
        assertEquals("inner_updated.xml", result.getFilename());
        assertEquals("OK", result.getSyncStatus());
        assertEquals("Successfully uploaded", result.getSyncMessage());
        assertNotNull(result.getCompletedAt());
    }

    @Test
    void syncHistoryDTOWithZeroAttemptCountFallsBackToDocRetry() {
        Document doc = Document.builder()
                .id(50L)
                .useCase("S3")
                .retryCount(2)
                .build();
        DocumentHistoryDTO fileHistory = DocumentHistoryDTO.builder()
                .filename("report.pdf")
                .build();
        FileUploadResponse response = FileUploadResponse.builder()
                .success(true)
                .filename("report.pdf")
                .syncStatus("SUCCESS")
                .attemptCount(0)
                .build();

        DocumentHistoryDTO result = DocumentHistoryFactory.syncHistoryDTO(doc, fileHistory, response);

        assertEquals(2, result.getRetryCount());          // Fallback to doc.retryCount
        assertEquals(2, result.getBusinessRetryCount());
    }

    @Test
    void syncGlobalHistoryMapsCorrectDataWithResponses() {
        Document doc = Document.builder()
                .id(123L)
                .useCase("S3")
                .retryCount(1)
                .build();
        DocumentHistoryDTO history = DocumentHistoryDTO.builder()
                .filename("master.zip")
                .build();
        List<FileUploadResponse> responses = List.of(
                FileUploadResponse.builder().filename("f1.xml").success(true).syncStatus("SUCCESS").build(),
                FileUploadResponse.builder().filename("f2.xml").success(false).syncStatus("GATEWAY_TIMEOUT").build()
        );
        DocumentHistoryFactory.ProcessingConclusion conclusion =
                new DocumentHistoryFactory.ProcessingConclusion(ProcessingResultCodes.PENDING.name(), 2);

        DocumentHistoryDTO result = DocumentHistoryFactory.syncGlobalHistory(doc, history, responses, conclusion);

        assertEquals(123L, result.getDocumentId());
        assertEquals(ProcessingResultCodes.PENDING.name(), result.getState());
        assertEquals("S3", result.getUseCase());
        assertEquals(1, result.getRetryCount());
        assertEquals(2, result.getBusinessRetryCount());
        assertEquals("master.zip", result.getFilename());
        assertEquals("SUCCESS", result.getSyncStatus());
        assertTrue(result.getSyncMessage().contains("f1.xml: SUCCESS"));
        assertTrue(result.getSyncMessage().contains("f2.xml: GATEWAY_TIMEOUT"));
        assertNotNull(result.getCompletedAt());
    }

    @Test
    void syncGlobalHistoryWithEmptyResponsesSetsRepresentativeStatusToNull() {
        Document doc = Document.builder().id(123L).build();
        DocumentHistoryDTO history = DocumentHistoryDTO.builder().build();
        DocumentHistoryFactory.ProcessingConclusion conclusion =
                new DocumentHistoryFactory.ProcessingConclusion("FAILED", 3);

        DocumentHistoryDTO result = DocumentHistoryFactory.syncGlobalHistory(doc, history, Collections.emptyList(), conclusion);

        assertNull(result.getSyncStatus());
        assertEquals("", result.getSyncMessage());
    }

    @Test
    void calculateFileStateReturnsCorrectStates() {
        assertEquals(ProcessingResultCodes.PROCESSED.name(),
                DocumentHistoryFactory.calculateFileState(FileUploadResponse.builder().success(true).build()));

        assertEquals(ProcessingResultCodes.BUSINESS_REJECTION.name(),
                DocumentHistoryFactory.calculateFileState(FileUploadResponse.builder().success(false).syncStatus(ProcessingResultCodes.PATTERN_MISMATCH.name()).build()));

        assertEquals(ProcessingResultCodes.PENDING.name(),
                DocumentHistoryFactory.calculateFileState(FileUploadResponse.builder().success(false).syncStatus(ProcessingResultCodes.UNKNOWN_ERROR.name()).build()));
    }

    @Test
    void aggregateMessagesWithNullFilenameUsesUnknown() {
        List<FileUploadResponse> responses = List.of(
                FileUploadResponse.builder().success(true).build()
        );
        String message = DocumentHistoryFactory.aggregateMessages(responses);
        assertEquals("unknown: SUCCESS", message);
    }

    @Test
    void handleGlobalErrorWithProcessingExceptionReturnsErrorCode() {
        ProcessingException pe = new ProcessingException("Error occurred", "SOME_ERROR_CODE");
        FileUploadResponse response = DocumentHistoryFactory.handleGlobalError(pe);

        assertFalse(response.isSuccess());
        assertEquals(ProcessingResultCodes.FAILURE.name(), response.getStatus());
        assertEquals("SOME_ERROR_CODE", response.getSyncStatus());
        assertEquals("Error occurred", response.getMessage());
    }

    @Test
    void handleGlobalErrorWithProcessingExceptionBlankCodeReturnsUnknownError() {
        ProcessingException pe = new ProcessingException("Error", "");
        FileUploadResponse response = DocumentHistoryFactory.handleGlobalError(pe);

        assertFalse(response.isSuccess());
        assertEquals(ProcessingResultCodes.UNKNOWN_ERROR.name(), response.getSyncStatus());
    }

    @Test
    void handleGlobalErrorWithProcessingExceptionNullCodeReturnsUnknownError() {
        ProcessingException pe = new ProcessingException("Error", null);
        FileUploadResponse response = DocumentHistoryFactory.handleGlobalError(pe);

        assertFalse(response.isSuccess());
        assertEquals(ProcessingResultCodes.UNKNOWN_ERROR.name(), response.getSyncStatus());
    }

    @Test
    void handleGlobalErrorWithProcessingExceptionWithFilenamePreservesFilename() {
        ProcessingException pe = new ProcessingException("File error", "SIZE_EXCEEDED");
        pe.setFilename("bigfile.pdf");
        FileUploadResponse response = DocumentHistoryFactory.handleGlobalError(pe);

        assertFalse(response.isSuccess());
        assertEquals("SIZE_EXCEEDED", response.getSyncStatus());
        assertEquals("bigfile.pdf", response.getFilename());
    }

    @Test
    void handleGlobalErrorWithWrappedProcessingExceptionUnwrapsAndUsesCode() {
        ProcessingException pe = new ProcessingException("Inner error", "GATEWAY_TIMEOUT");
        RuntimeException wrapper = new RuntimeException("Outer wrapper", pe);

        FileUploadResponse response = DocumentHistoryFactory.handleGlobalError(wrapper);

        assertFalse(response.isSuccess());
        assertEquals("GATEWAY_TIMEOUT", response.getSyncStatus());
        assertEquals("Inner error", response.getMessage());
    }

    @Test
    void handleGlobalErrorWithGenericExceptionReturnsUnknownError() {
        RuntimeException ex = new RuntimeException("Generic exception");
        FileUploadResponse response = DocumentHistoryFactory.handleGlobalError(ex);

        assertFalse(response.isSuccess());
        assertEquals(ProcessingResultCodes.FAILURE.name(), response.getStatus());
        assertEquals(ProcessingResultCodes.UNKNOWN_ERROR.name(), response.getSyncStatus());
        assertEquals("Generic exception", response.getMessage());
    }

    @Test
    void handleGlobalErrorWithNullMessageUsesUnknownErrorDescription() {
        RuntimeException ex = new RuntimeException((String) null);
        FileUploadResponse response = DocumentHistoryFactory.handleGlobalError(ex);

        assertFalse(response.isSuccess());
        assertEquals(ProcessingResultCodes.UNKNOWN_ERROR.value(), response.getMessage());
    }

    @Test
    void mapValidationErrorWithProcessingExceptionAndValidCodeReturnsSame() {
        ProcessingException pe = new ProcessingException("Error message", "CODE");
        DocumentHistoryDTO master = DocumentHistoryDTO.builder().isZip(false).build();

        ProcessingException result = DocumentHistoryFactory.mapValidationError(pe, master, null);
        assertEquals("CODE", result.getErrorCode());
        assertEquals("Error message", result.getMessage());
    }

    @Test
    void mapValidationErrorWithProcessingExceptionAndBlankCodeDefaultsToUnknownError() {
        ProcessingException pe = new ProcessingException("Error message", "");
        DocumentHistoryDTO master = DocumentHistoryDTO.builder().isZip(false).build();

        ProcessingException result = DocumentHistoryFactory.mapValidationError(pe, master, null);
        assertEquals(ProcessingResultCodes.UNKNOWN_ERROR.name(), result.getErrorCode());
    }

    @Test
    void mapValidationErrorWithGenericExceptionWrapsAsProcessingException() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid arg");
        DocumentHistoryDTO master = DocumentHistoryDTO.builder().isZip(false).build();

        ProcessingException result = DocumentHistoryFactory.mapValidationError(ex, master, null);
        assertEquals(ProcessingResultCodes.UNKNOWN_ERROR.name(), result.getErrorCode());
        assertEquals("Invalid arg", result.getMessage());
        assertEquals(ex, result.getCause());
    }

    @Test
    void mapValidationErrorWhenMasterIsZipSetsFilenameFromInner() {
        ProcessingException pe = new ProcessingException("Err", "CODE");
        DocumentHistoryDTO master = DocumentHistoryDTO.builder().isZip(true).build();
        DocumentHistoryDTO inner = DocumentHistoryDTO.builder().filename("inner.xml").build();

        ProcessingException result = DocumentHistoryFactory.mapValidationError(pe, master, inner);
        assertEquals("inner.xml", result.getFilename());
    }

    @Test
    void handleGlobalErrorWithWrappedSslExceptionUnwrapsToSslMessage() {
        javax.net.ssl.SSLHandshakeException sslEx = new javax.net.ssl.SSLHandshakeException("PKIX path building failed");
        RuntimeException wrapper = new RuntimeException("Request failed", sslEx);

        FileUploadResponse response = DocumentHistoryFactory.handleGlobalError(wrapper);

        assertFalse(response.isSuccess());
        assertEquals(ProcessingResultCodes.UNKNOWN_ERROR.name(), response.getSyncStatus());
        assertEquals("PKIX path building failed", response.getMessage());
    }

    @Test
    void handleGlobalErrorWithWrappedSslExceptionHavingNullMessageFallsBackToOuterMessage() {
        javax.net.ssl.SSLHandshakeException sslEx = new javax.net.ssl.SSLHandshakeException(null);
        RuntimeException wrapper = new RuntimeException("Request failed due to SSL handshake issue", sslEx);

        FileUploadResponse response = DocumentHistoryFactory.handleGlobalError(wrapper);

        assertFalse(response.isSuccess());
        assertEquals(ProcessingResultCodes.UNKNOWN_ERROR.name(), response.getSyncStatus());
        assertEquals("Request failed due to SSL handshake issue", response.getMessage());
    }

    @Test
    void aggregateMessagesWithSuccessMessageUsesDetailedMessage() {
        List<FileUploadResponse> responses = List.of(
                FileUploadResponse.builder()
                        .filename("test.pdf")
                        .success(true)
                        .syncStatus("OK")
                        .message("statusCode: OK, messageId: corr-123, idDocumento: doc-123")
                        .build()
        );
        String message = DocumentHistoryFactory.aggregateMessages(responses);
        assertEquals("test.pdf: statusCode: OK, messageId: corr-123, idDocumento: doc-123", message);
    }

    @Test
    void privateConstructorCanBeCalledViaReflection() throws Exception {
        java.lang.reflect.Constructor<DocumentHistoryFactory> constructor = DocumentHistoryFactory.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        DocumentHistoryFactory instance = constructor.newInstance();
        assertNotNull(instance);
    }

    @Test
    void syncHistoryDTOWithValidTraceIdAndNullMessageAppendsTraceId() {
        Document doc = Document.builder().id(1L).useCase("SOAP").build();
        DocumentHistoryDTO fileHistory = DocumentHistoryDTO.builder().filename("doc.pdf").build();
        FileUploadResponse response = FileUploadResponse.builder()
                .success(true)
                .traceId("trace-xyz")
                .message(null)
                .build();
        DocumentHistoryDTO result = DocumentHistoryFactory.syncHistoryDTO(doc, fileHistory, response);
        assertEquals(" [TraceID: trace-xyz]", result.getSyncMessage());
    }

    @Test
    void syncHistoryDTOWithBlankTraceIdDoesNotAppendTraceId() {
        Document doc = Document.builder().id(1L).useCase("SOAP").build();
        DocumentHistoryDTO fileHistory = DocumentHistoryDTO.builder().filename("doc.pdf").build();
        FileUploadResponse response = FileUploadResponse.builder()
                .success(true)
                .traceId("   ")
                .message("Message")
                .build();
        DocumentHistoryDTO result = DocumentHistoryFactory.syncHistoryDTO(doc, fileHistory, response);
        assertEquals("Message", result.getSyncMessage());
    }

    @Test
    void syncHistoryDTOWithUnknownTraceIdDoesNotAppendTraceId() {
        Document doc = Document.builder().id(1L).useCase("SOAP").build();
        DocumentHistoryDTO fileHistory = DocumentHistoryDTO.builder().filename("doc.pdf").build();
        FileUploadResponse response = FileUploadResponse.builder()
                .success(true)
                .traceId("unknown")
                .message("Message")
                .build();
        DocumentHistoryDTO result = DocumentHistoryFactory.syncHistoryDTO(doc, fileHistory, response);
        assertEquals("Message", result.getSyncMessage());
    }

    @Test
    void syncHistoryDTOWithNullFilenameFallsBackToFileHistoryFilename() {
        Document doc = Document.builder().id(1L).useCase("SOAP").build();
        DocumentHistoryDTO fileHistory = DocumentHistoryDTO.builder().filename("fileHistory.pdf").build();
        FileUploadResponse response = FileUploadResponse.builder()
                .success(true)
                .filename(null)
                .build();
        DocumentHistoryDTO result = DocumentHistoryFactory.syncHistoryDTO(doc, fileHistory, response);
        assertEquals("fileHistory.pdf", result.getFilename());
    }

    @Test
    void syncGlobalHistoryWithValidTraceIdAndNullMessageAppendsTraceId() {
        Document doc = Document.builder().id(1L).useCase("SOAP").build();
        DocumentHistoryDTO history = DocumentHistoryDTO.builder().filename("archive.zip").build();
        List<FileUploadResponse> responses = List.of(
                FileUploadResponse.builder().filename("f1.xml").success(true).traceId("trace-abc").build()
        );
        DocumentHistoryFactory.ProcessingConclusion conclusion =
                new DocumentHistoryFactory.ProcessingConclusion(ProcessingResultCodes.PROCESSED.name(), 1);
        DocumentHistoryDTO result = DocumentHistoryFactory.syncGlobalHistory(doc, history, responses, conclusion);
        assertTrue(result.getSyncMessage().contains("[TraceID: trace-abc]"));
    }

    @Test
    void syncGlobalHistoryWithBlankTraceIdDoesNotAppend() {
        Document doc = Document.builder().id(1L).useCase("SOAP").build();
        DocumentHistoryDTO history = DocumentHistoryDTO.builder().filename("archive.zip").build();
        List<FileUploadResponse> responses = List.of(
                FileUploadResponse.builder().filename("f1.xml").success(true).traceId("   ").build()
        );
        DocumentHistoryFactory.ProcessingConclusion conclusion =
                new DocumentHistoryFactory.ProcessingConclusion(ProcessingResultCodes.PROCESSED.name(), 1);
        DocumentHistoryDTO result = DocumentHistoryFactory.syncGlobalHistory(doc, history, responses, conclusion);
        assertFalse(result.getSyncMessage().contains("TraceID"));
    }

    @Test
    void syncGlobalHistoryWithUnknownTraceIdDoesNotAppend() {
        Document doc = Document.builder().id(1L).useCase("SOAP").build();
        DocumentHistoryDTO history = DocumentHistoryDTO.builder().filename("archive.zip").build();
        List<FileUploadResponse> responses = List.of(
                FileUploadResponse.builder().filename("f1.xml").success(true).traceId("unknown").build()
        );
        DocumentHistoryFactory.ProcessingConclusion conclusion =
                new DocumentHistoryFactory.ProcessingConclusion(ProcessingResultCodes.PROCESSED.name(), 1);
        DocumentHistoryDTO result = DocumentHistoryFactory.syncGlobalHistory(doc, history, responses, conclusion);
        assertFalse(result.getSyncMessage().contains("TraceID"));
    }

    @Test
    void handleGlobalErrorWithExceptionHavingSelfAsCauseStopsUnwrapping() {
        // Create an exception where getCause() returns itself
        class SelfCausedException extends RuntimeException {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        }
        SelfCausedException ex = new SelfCausedException();
        FileUploadResponse response = DocumentHistoryFactory.handleGlobalError(ex);
        assertNotNull(response);
    }

    @Test
    void handleGlobalErrorWithBlankMessageReturnsUnknownErrorValue() {
        ProcessingException pe = new ProcessingException("  ", "CODE");
        FileUploadResponse response = DocumentHistoryFactory.handleGlobalError(pe);
        assertEquals(ProcessingResultCodes.UNKNOWN_ERROR.value(), response.getMessage());
    }

    @Test
    void mapValidationErrorWhenMasterIsZipNullDoesNotSetFilename() {
        ProcessingException pe = new ProcessingException("Err", "CODE");
        DocumentHistoryDTO master = DocumentHistoryDTO.builder().isZip(null).build();
        DocumentHistoryDTO inner = DocumentHistoryDTO.builder().filename("inner.xml").build();
        ProcessingException result = DocumentHistoryFactory.mapValidationError(pe, master, inner);
        assertNull(result.getFilename());
    }

    @Test
    void mapValidationErrorWhenMasterIsZipFalseDoesNotSetFilename() {
        ProcessingException pe = new ProcessingException("Err", "CODE");
        DocumentHistoryDTO master = DocumentHistoryDTO.builder().isZip(false).build();
        DocumentHistoryDTO inner = DocumentHistoryDTO.builder().filename("inner.xml").build();
        ProcessingException result = DocumentHistoryFactory.mapValidationError(pe, master, inner);
        assertNull(result.getFilename());
    }

    @Test
    void calculateNextStateWithBusinessRejectionResponseReturnsBusinessRejection() {
        List<FileUploadResponse> responses = List.of(
                FileUploadResponse.builder().success(false).syncStatus(ProcessingResultCodes.BUSINESS_REJECTION.name()).build()
        );
        DocumentHistoryFactory.ProcessingConclusion conclusion =
                DocumentHistoryFactory.calculateNextState(1, responses);

        assertEquals(ProcessingResultCodes.BUSINESS_REJECTION.name(), conclusion.nextState());
        assertEquals(1, conclusion.nextRetryCount());
    }
}
