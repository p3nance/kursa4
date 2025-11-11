package com.example.authapp.models;

import java.util.*;

public class Cart {
    private List<Product> items = new ArrayList<>();

    public void addProduct(Product product) {
        items.add(product);
    }

    public void removeProduct(Product product) {
        items.remove(product);
    }

    public void clear() {
        items.clear();
    }

    public List<Product> getItems() {
        return items;
    }

    // Для подсчёта стоимости
    public int getTotalPrice() {
        return (int) items.stream()
                .mapToDouble(Product::getPrice)
                .sum();
    }
}
