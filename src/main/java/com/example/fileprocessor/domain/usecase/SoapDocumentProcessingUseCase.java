package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.port.out.CategoryManualRepository;
import com.example.fileprocessor.domain.port.out.DocumentHistoryRepository;
import com.example.fileprocessor.domain.port.out.PaisHomologadoRepository;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

/**
 * SOAP-specific document processing use case.
 */
public class SoapDocumentProcessingUseCase extends AbstractDocumentProcessingUseCase {

    private final SoapGateway soapGateway;
    private final CategoryManualRepository categoryRepository;
    private final PaisHomologadoRepository paisRepository;

    public SoapDocumentProcessingUseCase(
            ProductRepository productRepository,
            ProductRestGateway productRestGateway,
            SoapGateway soapGateway,
            DocumentHistoryRepository historyRepository,
            RulesBussinesGateway documentValidator,
            CategoryManualRepository categoryRepository,
            PaisHomologadoRepository paisRepository) {
        super(productRepository, historyRepository, productRestGateway, documentValidator);
        this.soapGateway = soapGateway;
        this.categoryRepository = categoryRepository;
        this.paisRepository = paisRepository;
    }

    @Override
    protected Mono<FileUploadResult> uploadDocument(ProductDocumentHistory doc, String productId) {
        return categoryRepository.findByCategoria(doc.origin())
            .flatMap(categoryEntity -> {
                String resolvedOrigin = categoryEntity.getDescripcionManual() != null
                    ? categoryEntity.getDescripcionManual() : categoryEntity.getCategoria();
                LocalDate vigencia = categoryEntity.getFechaVigencia();

                return paisRepository.findByPais(doc.pais())
                    .flatMap(paisEntity -> {
                        String paisHomologado = paisEntity.getPaisHomologado() != null
                            ? paisEntity.getPaisHomologado() : paisEntity.getPais();
                        return soapGateway.send(buildFileUploadRequest(doc, resolvedOrigin, vigencia, paisHomologado))
                            .onErrorResume(this::handleUploadError);
                    });
            });
    }

    @Override
    protected String implementationName() {
        return "SOAP";
    }

    private com.example.fileprocessor.domain.entity.FileUploadRequest buildFileUploadRequest(
            ProductDocumentHistory doc, String origin, LocalDate vigencia, String paisHomologado) {
        return com.example.fileprocessor.domain.entity.FileUploadRequest.builder()
            .documentId(doc.documentId())
            .content(doc.content() != null ? doc.content() : new byte[0])
            .filename(doc.filename())
            .contentType(doc.contentType())
            .fileSize(doc.size())
            .origin(origin)
            .vigencia(vigencia)
            .paisHomologado(paisHomologado)
            .build();
    }
}