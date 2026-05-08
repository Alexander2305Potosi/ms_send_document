package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.HomologationRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import reactor.core.publisher.Mono;

/**
 * Use case for processing documents via SOAP.
 * Standardized on V2 protocol (transmitirDocumento).
 */
public class SoapDocumentProcessingUseCase extends AbstractDocumentProcessingUseCase {

    private final SoapGateway soapGateway;
    private final HomologationRepository homologationRepository;

    public SoapDocumentProcessingUseCase(
            DocumentRepository documentRepository,
            ProductRestGateway productRestGateway,
            SoapGateway soapGateway,
            HomologationRepository homologationRepository,
            RulesBussinesGateway documentValidator) {
        super(documentRepository, productRestGateway, documentValidator);
        this.soapGateway = soapGateway;
        this.homologationRepository = homologationRepository;
    }

    @Override
    protected Mono<FileUploadResult> uploadDocument(ProductDocumentHistory doc, String productId, Long docId) {
        return homologationRepository.resolve(doc.origin(), doc.pais())
            .flatMap(result -> {
                FileUploadRequest request = buildFileUploadRequest(doc, result.origin(), result.paisHomologado(), docId);
                // All SOAP requests now use the unified V2 implementation via SoapGateway.send()
                return soapGateway.send(request);
            })
            .onErrorResume(this::handleUploadError);
    }

    @Override
    protected String implementationName() {
        return "SOAP";
    }

    private FileUploadRequest buildFileUploadRequest(ProductDocumentHistory doc, String origin,
                                                      String paisHomologado, Long docId) {
        return FileUploadRequest.builder()
            .documentId(doc.documentId())
            .content(doc.content() != null ? doc.content() : new byte[0])
            .filename(doc.filename())
            .contentType(doc.contentType())
            .fileSize(doc.size())
            .origin(origin)
            .paisHomologado(paisHomologado)
            .docId(docId)
            .build();
    }
}
