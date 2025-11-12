package com.example.authapp.services;

import com.example.authapp.models.Cart;
import com.example.authapp.models.Cart.CartItem;
import com.example.authapp.models.Product;
import java.util.List;

/**
 * Сервис для работы с корзиной
 * Работает напрямую с глобальной корзиной Cart.getInstance()
 */
public class CartService {

    /**
     * Добавляет товар в корзину
     */
    public void addProductToCart(Product product, int quantity) throws Exception {
        if (product == null) {
            throw new IllegalArgumentException("Товар не может быть null");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException("Количество должно быть больше 0");
        }

        if (product.getStock() < quantity) {
            throw new IllegalArgumentException("Недостаточно товара на складе. Доступно: " + product.getStock());
        }

        System.out.println("➕ CartService.addProductToCart: " + product.getName() + " x" + quantity);

        // Добавляем в глобальную корзину
        Cart cart = Cart.getInstance();
        cart.addProduct(product);

        System.out.println("✅ Товар добавлен в корзину");
    }

    /**
     * Добавляет товар с количеством 1
     */
    public void addProductToCart(Product product) throws Exception {
        addProductToCart(product, 1);
    }

    /**
     * Удаляет товар из корзины по товару
     */
    public void removeFromCart(Product product) throws Exception {
        if (product == null) {
            throw new IllegalArgumentException("Товар не может быть null");
        }

        System.out.println("➖ Удаление товара: " + product.getName());
        Cart.getInstance().removeProduct(product);
    }

    /**
     * Обновляет количество товара (если 0 - удаляет)
     */
    public void updateCartItemQuantity(Product product, int newQuantity) throws Exception {
        if (newQuantity <= 0) {
            removeFromCart(product);
        } else {
            System.out.println("🔄 Обновление количества: " + product.getName() + " -> " + newQuantity);
            // TODO: реализовать обновление количества в CartItem
        }
    }

    /**
     * Получает итоговую сумму корзины
     */
    public double getCartTotal() {
        double total = Cart.getInstance().getTotal();
        System.out.println("💰 Итого в корзине: " + total + " ₽");
        return total;
    }

    /**
     * Получает количество товаров в корзине
     */
    public int getCartSize() {
        int size = Cart.getInstance().getTotalQuantity();
        System.out.println("📦 Товаров в корзине: " + size);
        return size;
    }

    /**
     * Получает все товары в корзине
     */
    public List<CartItem> getCartItems() {
        return Cart.getInstance().getItems();
    }

    /**
     * Очищает корзину
     */
    public void clearCart() throws Exception {
        System.out.println("🗑️ Очистка корзины...");
        Cart.getInstance().clear();
        System.out.println("✅ Корзина очищена");
    }

    /**
     * Получает текущую корзину
     */
    public Cart getCurrentCart() {
        return Cart.getInstance();
    }

    /**
     * Применяет скидку по промокоду
     */
    public double applyDiscount(String promoCode) throws Exception {
        if (promoCode == null || promoCode.isEmpty()) {
            throw new IllegalArgumentException("Промокод не может быть пустым");
        }

        double discountPercent = 0;

        if (promoCode.equalsIgnoreCase("SALE10")) {
            discountPercent = 10;
        } else if (promoCode.equalsIgnoreCase("SALE20")) {
            discountPercent = 20;
        } else if (promoCode.equalsIgnoreCase("WELCOME")) {
            discountPercent = 5;
        } else {
            throw new Exception("Неверный промокод: " + promoCode);
        }

        double total = getCartTotal();
        double discount = total * (discountPercent / 100.0);

        System.out.println("🎫 Промокод '" + promoCode + "' применен. Скидка: " + discountPercent + "%");
        System.out.println("💵 Сумма скидки: " + discount + " ₽");

        return discount;
    }

    /**
     * Получает количество товара в корзине
     */
    public int getProductQuantity(Product product) {
        for (CartItem item : Cart.getInstance().getItems()) {
            if (item.getProduct().getId() == product.getId()) {
                return item.getQuantity();
            }
        }
        return 0;
    }

    /**
     * Проверяет есть ли товар в корзине
     */
    public boolean isProductInCart(Product product) {
        return getProductQuantity(product) > 0;
    }
}