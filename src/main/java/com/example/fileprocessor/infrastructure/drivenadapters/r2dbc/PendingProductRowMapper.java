package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.Product;
import io.r2dbc.spi.Readable;
import io.r2dbc.spi.Row;
import org.springframework.r2dbc.core.BeanPropertyRowMapper;

import java.time.LocalDateTime;
import java.util.List;

public class PendingProductRowMapper extends BeanPropertyRowMapper<Product> {

    public PendingProductRowMapper() {
        super(Product.class);
    }

    @Override
    public Product apply(Readable readable) {
        Row row = (Row) readable;
        String productId = row.get("nombre_producto", String.class);
        String name = row.get("nombre", String.class);
        LocalDateTime loadDate = row.get("fecha_carga", LocalDateTime.class);
        String state = row.get("estado", String.class);
        String messageError = row.get("mensaje_error", String.class);

        return new Product(
            productId,
            name,
            loadDate,
            state,
            messageError,
            List.of()
        );
    }
}
