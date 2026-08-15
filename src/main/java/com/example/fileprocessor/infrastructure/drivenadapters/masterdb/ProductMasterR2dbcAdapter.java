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

    /**
     * Obtiene los filtros de producto a partir del contexto reactivo.
     * <p>
     * Secuencia:
     * 1. Extrae las fechas de inicio, fin y el estado del producto desde el contexto.
     * 2. Parsea las fechas utilizando una utilidad, o usa null si están en blanco.
     * 3. Si no hay ningún filtro presente, retorna un Optional vacío.
     * 4. Ajusta las fechas al inicio y fin del día correspondientes.
     * 5. Retorna un objeto ProductFilter envuelto en un Optional.
     *
     * @param ctx el contexto reactivo
     * @return un Optional con los filtros aplicados o vacío si no hay filtros
     */
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

    /**
     * Obtiene todos los productos maestros aplicando los filtros del contexto.
     * <p>
     * Secuencia:
     * 1. Obtiene los filtros del contexto reactivo.
     * 2. Verifica si existe un cursor de reanudación (último ID de producto procesado).
     * 3. Registra en el log si es un inicio nuevo o una reanudación.
     * 4. Consulta el repositorio para obtener los productos aplicando estado, fechas y cursor.
     * 5. Mapea la entidad de la base de datos al objeto de dominio ProductMaestro.
     *
     * @return un Flux de ProductMaestro con los productos encontrados
     */
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

    /**
     * Cuenta la cantidad total de productos maestros aplicando los filtros.
     * <p>
     * Secuencia:
     * 1. Obtiene los filtros y el cursor de reanudación del contexto reactivo.
     * 2. Extrae el estado y las fechas de los filtros.
     * 3. Llama al repositorio para contar los productos que coinciden con los criterios.
     *
     * @return un Mono con el conteo total de productos
     */
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
