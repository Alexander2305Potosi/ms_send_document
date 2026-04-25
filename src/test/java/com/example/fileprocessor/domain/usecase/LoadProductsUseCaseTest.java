package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.ProductDocumentInfo;
import com.example.fileprocessor.domain.entity.ProductInfo;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoadProductsUseCaseTest {

    @Mock
    private ProductRestGateway productGateway;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductDocumentRepository documentRepository;

    @Test
    void execute_shouldLoadProductsAndDocuments() {
        ProductDocumentInfo doc1 = new ProductDocumentInfo(
            "doc-001", "manual.pdf", new byte[]{1}, "application/pdf", 1024, false, "folderA"
        );
        ProductDocumentInfo doc2 = new ProductDocumentInfo(
            "doc-002", "specs.pdf", new byte[]{2}, "application/pdf", 2048, false, "folderB"
        );

        ProductInfo product = ProductInfo.builder()
            .productId("prod-001")
            .name("Laptop")
            .documents(List.of(doc1, doc2))
            .build();

        when(productGateway.getAllProducts(any())).thenReturn(Flux.just(product));
        when(productRepository.save(any())).thenReturn(Mono.empty());
        when(documentRepository.saveAll(any())).thenReturn(Mono.empty());

        LoadProductsUseCase useCase = new LoadProductsUseCase(productGateway, productRepository, documentRepository);

        StepVerifier.create(useCase.execute())
            .assertNext(result -> {
                assert result.getProductId().equals("prod-001");
                assert result.getName().equals("Laptop");
                assert result.getDocumentCount() == 2;
                assert result.isSuccess();
            })
            .verifyComplete();

        verify(productRepository).save(any());
        verify(documentRepository).saveAll(any());
    }

    @Test
    void execute_shouldHandleMultipleProducts() {
        ProductDocumentInfo doc1 = new ProductDocumentInfo(
            "doc-001", "manual.pdf", new byte[]{1}, "application/pdf", 1024, false, "folderA"
        );
        ProductDocumentInfo doc2 = new ProductDocumentInfo(
            "doc-002", "specs.pdf", new byte[]{2}, "application/pdf", 2048, false, "folderB"
        );

        ProductInfo product1 = ProductInfo.builder()
            .productId("prod-001")
            .name("Laptop")
            .documents(List.of(doc1))
            .build();

        ProductInfo product2 = ProductInfo.builder()
            .productId("prod-002")
            .name("TV")
            .documents(List.of(doc2))
            .build();

        when(productGateway.getAllProducts(any())).thenReturn(Flux.just(product1, product2));
        when(productRepository.save(any())).thenReturn(Mono.empty());
        when(documentRepository.saveAll(any())).thenReturn(Mono.empty());

        LoadProductsUseCase useCase = new LoadProductsUseCase(productGateway, productRepository, documentRepository);

        StepVerifier.create(useCase.execute())
            .expectNextCount(2)
            .verifyComplete();

        verify(productRepository, times(2)).save(any());
        verify(documentRepository, times(2)).saveAll(any());
    }

    @Test
    void execute_shouldHandleEmptyProducts() {
        when(productGateway.getAllProducts(any())).thenReturn(Flux.empty());

        LoadProductsUseCase useCase = new LoadProductsUseCase(productGateway, productRepository, documentRepository);

        StepVerifier.create(useCase.execute())
            .verifyComplete();

        verify(productRepository, never()).save(any());
        verify(documentRepository, never()).saveAll(any());
    }

    @Test
    void execute_shouldPropagateGatewayError() {
        when(productGateway.getAllProducts(any()))
            .thenReturn(Flux.error(new RuntimeException("Gateway error")));

        LoadProductsUseCase useCase = new LoadProductsUseCase(productGateway, productRepository, documentRepository);

        StepVerifier.create(useCase.execute())
            .expectErrorMatches(error -> error.getMessage().contains("Gateway error"))
            .verify();

        verify(productRepository, never()).save(any());
    }

    @Test
    void execute_shouldSaveProductAndDocumentsSeparately() {
        ProductDocumentInfo doc1 = new ProductDocumentInfo(
            "doc-001", "manual.pdf", new byte[]{1}, "application/pdf", 1024, false, "folderA"
        );

        ProductInfo product = ProductInfo.builder()
            .productId("prod-001")
            .name("Laptop")
            .documents(List.of(doc1))
            .build();

        when(productGateway.getAllProducts(any())).thenReturn(Flux.just(product));
        when(productRepository.save(any())).thenReturn(Mono.empty());
        when(documentRepository.saveAll(any())).thenReturn(Mono.empty());

        LoadProductsUseCase useCase = new LoadProductsUseCase(productGateway, productRepository, documentRepository);

        StepVerifier.create(useCase.execute())
            .assertNext(result -> {
                assert result.getProductId().equals("prod-001");
                assert result.getDocumentCount() == 1;
            })
            .verifyComplete();

        verify(productRepository).save(any());
        verify(documentRepository).saveAll(any());
    }
}
