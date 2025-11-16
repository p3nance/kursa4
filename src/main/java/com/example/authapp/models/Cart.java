package com.example.authapp.models;

import java.util.ArrayList;
import java.util.List;

public class Cart {
    private static Cart instance;
    private List<CartItem> items = new ArrayList<>();

    private Cart() {}

    public static synchronized Cart getInstance() {
        if (instance == null) {
            instance = new Cart();
        }
        return instance;
    }

    public static class CartItem {
        private Product product;
        private int quantity;

        public CartItem(Product product, int quantity) {
            this.product = product;
            this.quantity = quantity;
        }

        public Product getProduct() { return product; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }

    /**
     * ✅ ИСПРАВЛЕНО: Добавляет товар или увеличивает количество на 1
     */
    public void addProduct(Product product) {
        addProduct(product, 1);
    }

    /**
     * ✅ ИСПРАВЛЕНО: Добавляет товар с указанным количеством
     */
    public void addProduct(Product product, int quantity) {
        if (product == null || quantity <= 0) {
            return;
        }

        for (CartItem item : items) {
            if (item.getProduct().getId() == product.getId()) {
                item.setQuantity(item.getQuantity() + quantity);
                System.out.println("📦 Товар обновлен: " + product.getName() + " -> " + item.getQuantity() + " шт.");
                return;
            }
        }

        items.add(new CartItem(product, quantity));
        System.out.println("✅ Новый товар добавлен: " + product.getName() + " (" + quantity + " шт.)");
    }

    /**
     * Удаляет товар полностью из корзины
     */
    public void removeProduct(Product product) {
        items.removeIf(item -> item.getProduct().getId() == product.getId());
    }

    /**
     * Очищает всю корзину
     */
    public void clear() {
        items.clear();
    }

    /**
     * Получает все товары в корзине
     */
    public List<CartItem> getItems() {
        return items;
    }

    /**
     * Получает сумму всех товаров в корзине
     */
    public double getTotal() {
        return items.stream()
                .mapToDouble(item -> item.getProduct().getPrice() * item.getQuantity())
                .sum();
    }

    /**
     * Получает общее количество товаров (с учетом количества каждого)
     */
    public int getTotalQuantity() {
        return items.stream()
                .mapToInt(CartItem::getQuantity)
                .sum();
    }

    /**
     * Получает количество уникальных товаров в корзине
     */
    public int getUniqueItemsCount() {
        return items.size();
    }
}
