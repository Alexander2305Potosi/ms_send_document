package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.port.out.DocumentHistoryRepository;
import com.example.fileprocessor.domain.port.out.HomologationRepository;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import reactor.core.publisher.Mono;

/**
 * SOAP-specific document processing use case.
 */
public class SoapDocumentProcessingUseCase extends AbstractDocumentProcessingUseCase {

    private final SoapGateway soapGateway;
    private final HomologationRepository homologationRepository;

    public SoapDocumentProcessingUseCase(
            ProductRepository productRepository,
            ProductRestGateway productRestGateway,
            SoapGateway soapGateway,
            DocumentHistoryRepository historyRepository,
            RulesBussinesGateway documentValidator,
            HomologationRepository homologationRepository) {
        super(productRepository, historyRepository, productRestGateway, documentValidator);
        this.soapGateway = soapGateway;
        this.homologationRepository = homologationRepository;
    }

    @Override
    protected Mono<FileUploadResult> uploadDocument(ProductDocumentHistory doc, String productId) {
        return homologationRepository.resolve(doc.origin(), doc.pais())
            .flatMap(result ->
                soapGateway.send(buildFileUploadRequest(doc, result.origin(), result.paisHomologado()))
                    .onErrorResume(this::handleUploadError)
            );
    }

    @Override
    protected String implementationName() {
        return "SOAP";
    }

    private FileUploadRequest buildFileUploadRequest(ProductDocumentHistory doc, String origin, String paisHomologado) {
        return FileUploadRequest.builder()
            .documentId(doc.documentId())
            .content(doc.content() != null ? doc.content() : new byte[0])
            .filename(doc.filename())
            .contentType(doc.contentType())
            .fileSize(doc.size())
            .origin(origin)
            .paisHomologado(paisHomologado)
            .build();
    }
}