package com.example.fileprocessor.infrastructure.drivenadapters.masterdb;

import com.example.fileprocessor.domain.entity.product.maestro.ProductMaestro;
import com.example.fileprocessor.domain.port.out.ProductMasterRepository;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.logging.Logger;

@Component
@RequiredArgsConstructor
public class ProductMasterR2dbcAdapter implements ProductMasterRepository {

    private static final Logger LOGGER = Logger.getLogger(ProductMasterR2dbcAdapter.class.getName());

    @Qualifier("masterDatabaseClient")
    private final DatabaseClient masterDatabaseClient;

    @Override
    public Flux<ProductMaestro> getAllProducts() {
        return Flux.deferContextual(ctx -> {
            String dateInit = ctx.getOrDefault(ApiConstants.HEADER_DATE_INIT, "");
            String dateEnd = ctx.getOrDefault(ApiConstants.HEADER_DATE_END, "");

            LOGGER.info(() -> "Fetching master products from EXTERNAL DATABASE. Filters - dateInit: " + dateInit + ", dateEnd: " + dateEnd);

            String baseSql = "SELECT id, id_producto, nombre, fecha_cargue, estado, carpeta_origen, pais_origen FROM productos_maestros";

            if (dateInit != null && !dateInit.isBlank() && dateEnd != null && !dateEnd.isBlank()) {
                baseSql += " WHERE fecha_cargue BETWEEN :dateInit AND :dateEnd";
                java.time.LocalDateTime start = parseDateTime(dateInit, false);
                java.time.LocalDateTime end = parseDateTime(dateEnd, true);
                
                return masterDatabaseClient.sql(baseSql)
                    .bind("dateInit", start)
                    .bind("dateEnd", end)
                    .map((row, metadata) -> ProductMaestro.builder()
                        .id(row.get("id", Long.class))
                        .productId(row.get("id_producto", String.class))
                        .name(row.get("nombre", String.class))
                        .loadDate(row.get("fecha_cargue", java.time.LocalDateTime.class))
                        .state(row.get("estado", String.class))
                        .originFolder(row.get("carpeta_origen", String.class))
                        .originCountry(row.get("pais_origen", String.class))
                        .build())
                    .all();
            }

            return masterDatabaseClient.sql(baseSql)
                .map((row, metadata) -> ProductMaestro.builder()
                    .id(row.get("id", Long.class))
                    .productId(row.get("id_producto", String.class))
                    .name(row.get("nombre", String.class))
                    .loadDate(row.get("fecha_cargue", java.time.LocalDateTime.class))
                    .state(row.get("estado", String.class))
                    .originFolder(row.get("carpeta_origen", String.class))
                    .originCountry(row.get("pais_origen", String.class))
                    .build())
                .all();
        });
    }

    private java.time.LocalDateTime parseDateTime(String dateStr, boolean endOfDay) {
        try {
            String trimmed = dateStr.trim();
            if (trimmed.contains(" ") || trimmed.contains("T")) {
                String clean = trimmed.replace("T", " ");
                if (clean.length() > 19) {
                    clean = clean.substring(0, 19);
                }
                return java.time.LocalDateTime.parse(clean, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            java.time.LocalDate date = java.time.LocalDate.parse(trimmed);
            return endOfDay ? date.atTime(23, 59, 59) : date.atStartOfDay();
        } catch (Exception e) {
            return endOfDay ? java.time.LocalDateTime.now() : java.time.LocalDateTime.of(1970, 1, 1, 0, 0);
        }
    }
}
