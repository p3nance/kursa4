package com.example.authapp.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Глобальная корзина приложения
 * Используется для хранения товаров пока пользователь ходит по магазину
 */
public class Cart {
    private static Cart instance;
    private List<CartItem> items = new ArrayList<>();

    private Cart() {}

    public static Cart getInstance() {
        if (instance == null) {
            instance = new Cart();
        }
        return instance;
    }

    /**
     * Добавляет товар в корзину
     */
    public void addProduct(Product product) {
        System.out.println("✅ Cart.addProduct: " + product.getName());

        // Проверяем есть ли уже такой товар
        for (CartItem item : items) {
            if (item.getProduct().getId() == product.getId()) {
                item.increaseQuantity();
                System.out.println("⬆️ Количество увеличено на 1. Всего: " + item.getQuantity());
                return;
            }
        }

        // Если товара нет - добавляем новый
        items.add(new CartItem(product, 1));
        System.out.println("📦 Товар добавлен. Всего в корзине: " + items.size());
    }

    public void removeProduct(Product product) {
        items.removeIf(item -> item.getProduct().getId() == product.getId());
    }

    public List<CartItem> getItems() {
        return items;
    }

    public void clear() {
        items.clear();
    }

    public double getTotal() {
        return items.stream()
                .mapToDouble(item -> item.getProduct().getPrice() * item.getQuantity())
                .sum();
    }

    public int getTotalQuantity() {
        return items.stream()
                .mapToInt(CartItem::getQuantity)
                .sum();
    }

    /**
     * Внутренний класс для хранения товара и количества
     */
    public static class CartItem {
        private Product product;
        private int quantity;

        public CartItem(Product product, int quantity) {
            this.product = product;
            this.quantity = quantity;
        }

        public Product getProduct() {
            return product;
        }

        public int getQuantity() {
            return quantity;
        }

        public void increaseQuantity() {
            this.quantity++;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }
}