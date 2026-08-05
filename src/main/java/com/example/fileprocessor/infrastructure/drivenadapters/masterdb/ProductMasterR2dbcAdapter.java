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

        java.time.LocalDate start = (dateInit == null || dateInit.isBlank())
                ? null
                : ApiConstants.parseDateOrToday(dateInit);

        java.time.LocalDate end = (dateEnd == null || dateEnd.isBlank())
                ? null
                : ApiConstants.parseDateOrToday(dateEnd);

        String filterState = (state != null && !state.isBlank()) ? state : null;

        if (start == null && end == null && filterState == null) {
            return Optional.empty();
        }

        LocalDateTime startDateTime = start != null ? start.atTime(ApiConstants.START_OF_DAY_TIME) : null;
        LocalDateTime endDateTime   = end   != null ? end.atTime(ApiConstants.END_OF_DAY_TIME)     : null;

        return Optional.of(new ProductFilter(startDateTime, endDateTime, filterState));
    }

    @Override
    public Flux<ProductMaestro> getAllProducts() {
        return Flux.deferContextual(ctx -> {
            Optional<ProductFilter> productFilter = getProductFilter(ctx);
            
            // Extraer el cursor de reanudación del contexto reactivo
            String lastProductId = ctx.getOrDefault(ApiConstants.LAST_PRODUCT_ID, null);
            if (lastProductId != null && !lastProductId.isBlank()) {
                LOGGER.info(() -> "[REANUDACIÓN] Consultando productos maestros a partir de id_producto > " + lastProductId);
            } else {
                LOGGER.info(() -> "[INICIO] Consultando todos los productos maestros (sin cursor de reanudación).");
            }

            String estado = productFilter.map(ProductFilter::state).orElse(null);
            LocalDateTime start = productFilter.map(ProductFilter::start).orElse(null);
            LocalDateTime end = productFilter.map(ProductFilter::end).orElse(null);

            return repository.findAllProducts(estado, start, end, lastProductId)
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
            String lastProductId = ctx.getOrDefault(ApiConstants.LAST_PRODUCT_ID, null);

            String estado = productFilter.map(ProductFilter::state).orElse(null);
            LocalDateTime start = productFilter.map(ProductFilter::start).orElse(null);
            LocalDateTime end = productFilter.map(ProductFilter::end).orElse(null);

            return repository.countAllProducts(estado, start, end, lastProductId);
        });
    }
}
