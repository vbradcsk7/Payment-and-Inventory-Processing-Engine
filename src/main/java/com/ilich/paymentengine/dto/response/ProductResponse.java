package com.ilich.paymentengine.dto.response;

import com.ilich.paymentengine.model.Product;
import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        int stockAvailable,
        int stockReserved,
        BigDecimal price,
        Long version
) {
    public static ProductResponse fromEntity(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getStockAvailable(),
                product.getStockReserved(),
                product.getPrice(),
                product.getVersion()
        );
    }
}
