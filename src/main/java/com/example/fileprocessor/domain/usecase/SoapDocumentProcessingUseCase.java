package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.port.out.DocumentHistoryRepository;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.HomologationRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.domain.port.out.SoapGatewayV2;
import reactor.core.publisher.Mono;

public class SoapDocumentProcessingUseCase extends AbstractDocumentProcessingUseCase {

    private final SoapGateway soapGateway;
    private final HomologationRepository homologationRepository;

    public SoapDocumentProcessingUseCase(
            DocumentRepository documentRepository,
            DocumentHistoryRepository historyRepository,
            ProductRestGateway productRestGateway,
            SoapGateway soapGateway,
            HomologationRepository homologationRepository,
            RulesBussinesGateway documentValidator) {
        super(documentRepository, historyRepository, productRestGateway, documentValidator);
        this.soapGateway = soapGateway;
        this.homologationRepository = homologationRepository;
    }

    @Override
    protected Mono<FileUploadResult> uploadDocument(ProductDocumentHistory doc, String productId, Long docId) {
        return homologationRepository.resolve(doc.origin(), doc.pais())
            .flatMap(result -> {
                FileUploadRequest request = buildFileUploadRequest(doc, result.origin(), result.paisHomologado(), docId);
                if (result.useV2()) {
                    SoapGatewayV2 v2Gateway = (SoapGatewayV2) soapGateway;
                    return v2Gateway.transmitirDocumento(request);
                }
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
