package com.example.authapp.services;

import com.example.authapp.models.Cart;
import com.example.authapp.models.Cart.CartItem;
import com.example.authapp.models.Product;
import com.example.authapp.dto.CartItemDTO;
import com.example.authapp.repositories.CartRepository;
import config.SessionManager;

import java.util.List;

/**
 * Сервис для работы с корзиной через базу данных Supabase
 */
public class CartService {

    /**
     * ✅ ИСПРАВЛЕНО: Загружает корзину пользователя из БД при входе
     * Теперь корректно добавляет товары с их количеством
     */
    public void loadUserCart() throws Exception {
        String userId = SessionManager.getUserId();
        if (userId == null || userId.isEmpty()) {
            System.out.println("⚠️ Пользователь не авторизован");
            return;
        }

        try {
            List<CartItemDTO> cartItems = CartRepository.loadCartFromSupabase(userId);

            // Очищаем текущую корзину в памяти
            Cart.getInstance().clear();

            // Загружаем товары из БД в память ОДНИМ ВЫЗОВОМ
            for (CartItemDTO dto : cartItems) {
                Product product = new Product(
                        dto.productId,
                        dto.productName,
                        "", // description
                        dto.price,
                        1, // stock
                        dto.productImage,
                        "", // category
                        "" // manufacturer
                );

                // ✅ ИСПРАВЛЕНО: Добавляем товар с его количеством, а не циклом!
                Cart.getInstance().addProduct(product, dto.quantity);
            }

            System.out.println("✅ Корзина загружена из БД: " + cartItems.size() + " позиций");
        } catch (Exception e) {
            System.err.println("⚠️ Ошибка загрузки корзины: " + e.getMessage());
        }
    }

    /**
     * ✅ ИСПРАВЛЕНО: Добавляет товар в корзину (и в БД, и в память)
     * Теперь работает правильно с количеством > 1
     */
    public void addProductToCart(Product product, int quantity) throws Exception {
        if (product == null) {
            throw new IllegalArgumentException("Товар не может быть null");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException("Количество должно быть больше 0");
        }

        String userId = SessionManager.getUserId();
        if (userId == null || userId.isEmpty()) {
            throw new Exception("Пользователь не авторизован");
        }

        System.out.println("➕ CartService.addProductToCart: " + product.getName() + " x" + quantity);

        try {
            // Проверяем, есть ли уже такой товар в БД
            List<CartItemDTO> existingItems = CartRepository.loadCartFromSupabase(userId);
            CartItemDTO existingItem = null;

            for (CartItemDTO item : existingItems) {
                if (item.productId == product.getId()) {
                    existingItem = item;
                    break;
                }
            }

            if (existingItem != null) {
                // Обновляем количество существующего товара
                int newQuantity = existingItem.quantity + quantity;
                CartRepository.updateCartItemInSupabase(existingItem.cartItemId, newQuantity);
                System.out.println("🔄 Товар обновлен в БД: " + existingItem.productName + " -> " + newQuantity);
            } else {
                // Создаем новую запись в БД
                CartItemDTO newItem = new CartItemDTO(
                        0, // id сгенерируется автоматически
                        product.getId(),
                        product.getName(),
                        product.getPrice(),
                        quantity,
                        product.getImageUrl()
                );
                CartRepository.addCartItemToSupabase(userId, newItem);
                System.out.println("✅ Новый товар добавлен в БД: " + product.getName());
            }

            // ✅ ИСПРАВЛЕНО: Добавляем в локальную корзину одним вызовом!
            Cart.getInstance().addProduct(product, quantity);

            System.out.println("✅ Товар добавлен в корзину (БД + память)");

        } catch (Exception e) {
            throw new Exception("Ошибка добавления в корзину: " + e.getMessage());
        }
    }

    /**
     * Добавляет товар с количеством 1
     */
    public void addProductToCart(Product product) throws Exception {
        addProductToCart(product, 1);
    }

    /**
     * ✅ ИСПРАВЛЕНО: Удаляет товар из корзины (из БД и из памяти)
     */
    public void removeFromCart(Product product) throws Exception {
        if (product == null) {
            throw new IllegalArgumentException("Товар не может быть null");
        }

        String userId = SessionManager.getUserId();
        if (userId == null || userId.isEmpty()) {
            throw new Exception("Пользователь не авторизован");
        }

        System.out.println("➖ Удаление товара: " + product.getName());

        try {
            // Находим товар в БД и удаляем его полностью
            List<CartItemDTO> items = CartRepository.loadCartFromSupabase(userId);
            for (CartItemDTO item : items) {
                if (item.productId == product.getId()) {
                    CartRepository.removeCartItemFromSupabase(item.cartItemId);
                    break;
                }
            }

            // Удаляем из локальной корзины
            Cart.getInstance().removeProduct(product);
            System.out.println("✅ Товар удален из корзины (БД + память)");

        } catch (Exception e) {
            throw new Exception("Ошибка удаления из корзины: " + e.getMessage());
        }
    }

    /**
     * ✅ ИСПРАВЛЕНО: Обновляет количество товара в корзине
     */
    public void updateCartItemQuantity(Product product, int newQuantity) throws Exception {
        if (newQuantity <= 0) {
            removeFromCart(product);
            return;
        }

        String userId = SessionManager.getUserId();
        if (userId == null || userId.isEmpty()) {
            throw new Exception("Пользователь не авторизован");
        }

        System.out.println("🔄 Обновление количества: " + product.getName() + " -> " + newQuantity);

        try {
            // Находим товар в БД и обновляем его количество
            List<CartItemDTO> items = CartRepository.loadCartFromSupabase(userId);
            for (CartItemDTO item : items) {
                if (item.productId == product.getId()) {
                    CartRepository.updateCartItemInSupabase(item.cartItemId, newQuantity);
                    break;
                }
            }

            // Обновляем локальную корзину
            Cart cart = Cart.getInstance();
            cart.removeProduct(product);
            cart.addProduct(product, newQuantity);

            System.out.println("✅ Количество обновлено (БД + память)");

        } catch (Exception e) {
            throw new Exception("Ошибка обновления количества: " + e.getMessage());
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
     * Получает количество товаров в корзине (с учетом количества)
     */
    public int getCartSize() {
        int size = Cart.getInstance().getTotalQuantity();
        System.out.println("📦 Товаров в корзине: " + size);
        return size;
    }

    /**
     * Получает количество уникальных товаров в корзине
     */
    public int getCartItemsCount() {
        int count = Cart.getInstance().getUniqueItemsCount();
        System.out.println("🎁 Уникальных товаров: " + count);
        return count;
    }

    /**
     * Получает все товары в корзине
     */
    public List<CartItem> getCartItems() {
        return Cart.getInstance().getItems();
    }

    /**
     * Очищает корзину (БД + память)
     */
    public void clearCart() throws Exception {
        String userId = SessionManager.getUserId();
        if (userId == null || userId.isEmpty()) {
            throw new Exception("Пользователь не авторизован");
        }

        System.out.println("🗑️ Очистка корзины...");

        try {
            CartRepository.clearUserCart(userId);
            Cart.getInstance().clear();
            System.out.println("✅ Корзина очищена (БД + память)");

        } catch (Exception e) {
            throw new Exception("Ошибка очистки корзины: " + e.getMessage());
        }
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

        System.out.println("🎫 Промокод '" + promoCode + "' применен. Скидка: " + discountPercent + "% = " + discount + " ₽");
        return discount;
    }

    /**
     * Получает количество конкретного товара в корзине
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
