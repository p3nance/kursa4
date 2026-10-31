package com.example.authapp.domain;

import com.example.authapp.models.Product;
import java.math.BigDecimal;
import java.util.Objects;

/** Immutable snapshot: recommendation never changes stock or creates orders. */
public record CatalogComponent(int productId, String name, ComponentCategory category,
                               BigDecimal price, int stock, ComponentSpecs specs) {
    public CatalogComponent {
        if (productId <= 0) throw new IllegalArgumentException("Некорректный идентификатор товара");
        name = Objects.requireNonNullElse(name, "Без названия");
        Objects.requireNonNull(category); Objects.requireNonNull(price); Objects.requireNonNull(specs);
        if (price.signum() < 0 || stock < 0) throw new IllegalArgumentException("Отрицательная цена или остаток");
    }
    public static CatalogComponent fromProduct(Product product) {
        Objects.requireNonNull(product);
        return new CatalogComponent(product.getId(), product.getName(),
                ComponentCategory.fromDisplayName(product.getCategory()).orElseThrow(
                        () -> new IllegalArgumentException("Категория не участвует в подборе")),
                product.getPrice(), product.getStock(),
                DescriptionSpecImporter.parse(product.getDescription()));
    }
}
