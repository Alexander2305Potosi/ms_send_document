package com.example.fileprocessor.infrastructure.drivenadapters.masterdb;

import com.example.fileprocessor.domain.entity.product.maestro.ProductMaestro;
import com.example.fileprocessor.domain.port.out.ProductMasterRepository;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import com.example.fileprocessor.infrastructure.drivenadapters.masterdb.repository.ProductMasterR2dbcRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.masterdb.entity.ProductMasterEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.logging.Logger;

@Component
@RequiredArgsConstructor
public class ProductMasterR2dbcAdapter implements ProductMasterRepository {

    private static final Logger LOGGER = Logger.getLogger(ProductMasterR2dbcAdapter.class.getName());

    private final ProductMasterR2dbcRepository repository;

    private record ProductFilter(LocalDateTime start, LocalDateTime end, String state) {}

    private Optional<ProductFilter> getProductFilter(reactor.util.context.ContextView ctx) {
        String dateInit = ctx.getOrDefault(ApiConstants.HEADER_DATE_INIT, "");
        String dateEnd = ctx.getOrDefault(ApiConstants.HEADER_DATE_END, "");
        String state = ctx.getOrDefault(ApiConstants.HEADER_PRODUCT_STATUS, "");

        LocalDateTime start = (dateInit != null && !dateInit.isBlank()) ? parseDateTime(dateInit, false) : null;
        LocalDateTime end = (dateEnd != null && !dateEnd.isBlank()) ? parseDateTime(dateEnd, true) : null;
        String filterState = (state != null && !state.isBlank()) ? state : null;

        if (start == null && end == null && filterState == null) {
            return Optional.empty();
        }
        return Optional.of(new ProductFilter(start, end, filterState));
    }

    @Override
    public Flux<ProductMaestro> getAllProducts() {
        return Flux.deferContextual(ctx -> {
            Optional<ProductFilter> productFilter = getProductFilter(ctx);
            LOGGER.info(() -> "Fetching master products from EXTERNAL DATABASE.");

            String estado = productFilter.map(ProductFilter::state).orElse(null);
            LocalDateTime start = productFilter.map(ProductFilter::start).orElse(null);
            LocalDateTime end = productFilter.map(ProductFilter::end).orElse(null);

            return repository.findAllProducts(estado, start, end)
                    .map(entity -> ProductMaestro.builder()
                            .id(entity.getId())
                            .productId(entity.getProductId())
                            .name(entity.getNombre())
                            .loadDate(entity.getFechaCargue())
                            .state(entity.getEstado())
                            .originFolder(entity.getCarpetaOrigen())
                            .originCountry(entity.getPaisOrigen())
                            .build());
        });
    }

    @Override
    public Mono<Long> countAllProducts() {
        return Mono.deferContextual(ctx -> {
            Optional<ProductFilter> productFilter = getProductFilter(ctx);

            String estado = productFilter.map(ProductFilter::state).orElse(null);
            LocalDateTime start = productFilter.map(ProductFilter::start).orElse(null);
            LocalDateTime end = productFilter.map(ProductFilter::end).orElse(null);

            return repository.countAllProducts(estado, start, end);
        });
    }

    private LocalDateTime parseDateTime(String dateStr, boolean endOfDay) {
        try {
            String trimmed = dateStr.trim();
            if (trimmed.contains(" ") || trimmed.contains("T")) {
                String clean = trimmed.replace("T", " ");
                if (clean.length() > 19) {
                    clean = clean.substring(0, 19);
                }
                return LocalDateTime.parse(clean, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            java.time.LocalDate date = java.time.LocalDate.parse(trimmed);
            return endOfDay ? date.atTime(23, 59, 59) : date.atStartOfDay();
        } catch (Exception e) {
            return endOfDay ? LocalDateTime.now() : LocalDateTime.of(1970, 1, 1, 0, 0);
        }
    }
}
