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
    void calculateNextState_whenResponsesIsEmpty_returnsFailed() {
        DocumentHistoryFactory.ProcessingConclusion conclusion =
                DocumentHistoryFactory.calculateNextState(1, Collections.emptyList());

        assertEquals(ProcessingResultCodes.FAILED.name(), conclusion.nextState());
        assertEquals(1, conclusion.nextRetryCount());
    }

    @Test
    void calculateNextState_whenAllResponsesAreSuccess_returnsProcessed() {
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
    void calculateNextState_whenAnyResponseIsBusinessRule_returnsBusinessRejection() {
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
    void calculateNextState_whenHasTransientAndUnderMaxRetries_returnsPendingAndIncrementsRetry() {
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
    void calculateNextState_whenHasTransientAndAtMaxRetries_returnsFailedWithoutIncrement() {
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
    void calculateNextState_whenHasNonTransientNonBusinessError_returnsFailed() {
        List<FileUploadResponse> responses = List.of(
                FileUploadResponse.builder().success(false).syncStatus(ProcessingResultCodes.SOURCE_NOT_FOUND.name()).build()
        );
        DocumentHistoryFactory.ProcessingConclusion conclusion =
                DocumentHistoryFactory.calculateNextState(1, responses);

        assertEquals(ProcessingResultCodes.FAILED.name(), conclusion.nextState());
        assertEquals(1, conclusion.nextRetryCount());
    }

    @Test
    void syncHistoryDTO_mapsCorrectData() {
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
                .build();

        DocumentHistoryDTO result = DocumentHistoryFactory.syncHistoryDTO(doc, fileHistory, response);

        assertEquals(123L, result.getDocumentId());
        assertEquals(ProcessingResultCodes.PROCESSED.name(), result.getState());
        assertEquals("SOAP", result.getUseCase());
        assertEquals(2, result.getRetryCount());
        assertEquals(2, result.getBusinessRetryCount());
        assertEquals("inner_updated.xml", result.getFilename());
        assertEquals("OK", result.getSyncStatus());
        assertEquals("Successfully uploaded", result.getSyncMessage());
        assertNotNull(result.getCompletedAt());
    }

    @Test
    void syncGlobalHistory_mapsCorrectData_withResponses() {
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
    void syncGlobalHistory_withEmptyResponses_setsRepresentativeStatusToNull() {
        Document doc = Document.builder().id(123L).build();
        DocumentHistoryDTO history = DocumentHistoryDTO.builder().build();
        DocumentHistoryFactory.ProcessingConclusion conclusion =
                new DocumentHistoryFactory.ProcessingConclusion("FAILED", 3);

        DocumentHistoryDTO result = DocumentHistoryFactory.syncGlobalHistory(doc, history, Collections.emptyList(), conclusion);

        assertNull(result.getSyncStatus());
        assertEquals("", result.getSyncMessage());
    }

    @Test
    void calculateFileState_returnsCorrectStates() {
        assertEquals(ProcessingResultCodes.PROCESSED.name(),
                DocumentHistoryFactory.calculateFileState(FileUploadResponse.builder().success(true).build()));

        assertEquals(ProcessingResultCodes.BUSINESS_REJECTION.name(),
                DocumentHistoryFactory.calculateFileState(FileUploadResponse.builder().success(false).syncStatus(ProcessingResultCodes.PATTERN_MISMATCH.name()).build()));

        assertEquals(ProcessingResultCodes.PENDING.name(),
                DocumentHistoryFactory.calculateFileState(FileUploadResponse.builder().success(false).syncStatus(ProcessingResultCodes.UNKNOWN_ERROR.name()).build()));
    }

    @Test
    void aggregateMessages_withNullFilename_usesUnknown() {
        List<FileUploadResponse> responses = List.of(
                FileUploadResponse.builder().success(true).build()
        );
        String message = DocumentHistoryFactory.aggregateMessages(responses);
        assertEquals("unknown: SUCCESS", message);
    }

    @Test
    void handleGlobalError_withProcessingException_returnsErrorCode() {
        ProcessingException pe = new ProcessingException("Error occurred", "SOME_ERROR_CODE");
        FileUploadResponse response = DocumentHistoryFactory.handleGlobalError(pe);

        assertFalse(response.isSuccess());
        assertEquals(ProcessingResultCodes.FAILURE.name(), response.getStatus());
        assertEquals("SOME_ERROR_CODE", response.getSyncStatus());
        assertEquals("Error occurred", response.getMessage());
    }

    @Test
    void handleGlobalError_withProcessingExceptionBlankCode_returnsUnknownError() {
        ProcessingException pe = new ProcessingException("Error", "");
        FileUploadResponse response = DocumentHistoryFactory.handleGlobalError(pe);

        assertFalse(response.isSuccess());
        assertEquals(ProcessingResultCodes.UNKNOWN_ERROR.name(), response.getSyncStatus());
    }

    @Test
    void handleGlobalError_withProcessingExceptionNullCode_returnsUnknownError() {
        ProcessingException pe = new ProcessingException("Error", null);
        FileUploadResponse response = DocumentHistoryFactory.handleGlobalError(pe);

        assertFalse(response.isSuccess());
        assertEquals(ProcessingResultCodes.UNKNOWN_ERROR.name(), response.getSyncStatus());
    }

    @Test
    void handleGlobalError_withProcessingExceptionWithFilename_preservesFilename() {
        ProcessingException pe = new ProcessingException("File error", "SIZE_EXCEEDED");
        pe.setFilename("bigfile.pdf");
        FileUploadResponse response = DocumentHistoryFactory.handleGlobalError(pe);

        assertFalse(response.isSuccess());
        assertEquals("SIZE_EXCEEDED", response.getSyncStatus());
        assertEquals("bigfile.pdf", response.getFilename());
    }

    @Test
    void handleGlobalError_withWrappedProcessingException_unwrapsAndUsesCode() {
        ProcessingException pe = new ProcessingException("Inner error", "GATEWAY_TIMEOUT");
        RuntimeException wrapper = new RuntimeException("Outer wrapper", pe);

        FileUploadResponse response = DocumentHistoryFactory.handleGlobalError(wrapper);

        assertFalse(response.isSuccess());
        assertEquals("GATEWAY_TIMEOUT", response.getSyncStatus());
        assertEquals("Inner error", response.getMessage());
    }

    @Test
    void handleGlobalError_withGenericException_returnsUnknownError() {
        RuntimeException ex = new RuntimeException("Generic exception");
        FileUploadResponse response = DocumentHistoryFactory.handleGlobalError(ex);

        assertFalse(response.isSuccess());
        assertEquals(ProcessingResultCodes.FAILURE.name(), response.getStatus());
        assertEquals(ProcessingResultCodes.UNKNOWN_ERROR.name(), response.getSyncStatus());
        assertEquals("Generic exception", response.getMessage());
    }

    @Test
    void handleGlobalError_withNullMessage_usesUnknownErrorDescription() {
        RuntimeException ex = new RuntimeException((String) null);
        FileUploadResponse response = DocumentHistoryFactory.handleGlobalError(ex);

        assertFalse(response.isSuccess());
        assertEquals(ProcessingResultCodes.UNKNOWN_ERROR.value(), response.getMessage());
    }

    @Test
    void mapValidationError_withProcessingExceptionAndValidCode_returnsSame() {
        ProcessingException pe = new ProcessingException("Error message", "CODE");
        DocumentHistoryDTO master = DocumentHistoryDTO.builder().isZip(false).build();

        ProcessingException result = DocumentHistoryFactory.mapValidationError(pe, master, null);
        assertEquals("CODE", result.getErrorCode());
        assertEquals("Error message", result.getMessage());
    }

    @Test
    void mapValidationError_withProcessingExceptionAndBlankCode_defaultsToUnknownError() {
        ProcessingException pe = new ProcessingException("Error message", "");
        DocumentHistoryDTO master = DocumentHistoryDTO.builder().isZip(false).build();

        ProcessingException result = DocumentHistoryFactory.mapValidationError(pe, master, null);
        assertEquals(ProcessingResultCodes.UNKNOWN_ERROR.name(), result.getErrorCode());
    }

    @Test
    void mapValidationError_withGenericException_wrapsAsProcessingException() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid arg");
        DocumentHistoryDTO master = DocumentHistoryDTO.builder().isZip(false).build();

        ProcessingException result = DocumentHistoryFactory.mapValidationError(ex, master, null);
        assertEquals(ProcessingResultCodes.UNKNOWN_ERROR.name(), result.getErrorCode());
        assertEquals("Invalid arg", result.getMessage());
        assertEquals(ex, result.getCause());
    }

    @Test
    void mapValidationError_whenMasterIsZip_setsFilenameFromInner() {
        ProcessingException pe = new ProcessingException("Err", "CODE");
        DocumentHistoryDTO master = DocumentHistoryDTO.builder().isZip(true).build();
        DocumentHistoryDTO inner = DocumentHistoryDTO.builder().filename("inner.xml").build();

        ProcessingException result = DocumentHistoryFactory.mapValidationError(pe, master, inner);
        assertEquals("inner.xml", result.getFilename());
    }
}
