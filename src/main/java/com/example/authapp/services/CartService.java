package com.example.authapp.services;

import com.example.authapp.models.Cart;
import com.example.authapp.models.Cart.CartItem;
import com.example.authapp.models.Product;
import com.example.authapp.dto.CartItemDTO;
import com.example.authapp.repositories.CartRepository;
import config.SessionManager;
import java.util.List;

public class CartService {
    public void loadUserCart() throws Exception {
        String userId = SessionManager.getUserId();
        if (userId == null || userId.isEmpty()) {
            return;
        }
        try {
            List<CartItemDTO> cartItems = CartRepository.loadCartFromSupabase(userId);
            Cart.getInstance().clear();
            for (CartItemDTO dto : cartItems) {
                Product product = new Product(
                    dto.productId,
                    dto.productName,
                    "",
                    dto.price,
                    1,
                    dto.productImage,
                    "",
                    ""
                );
                for (int i = 0; i < dto.quantity; i++) {
                    Cart.getInstance().addProduct(product);
                }
            }
        } catch (Exception e) {
            throw new Exception("Ошибка загрузки корзины: " + e.getMessage());
        }
    }

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
        try {
            List<CartItemDTO> existingItems = CartRepository.loadCartFromSupabase(userId);
            CartItemDTO existingItem = null;
            for (CartItemDTO item : existingItems) {
                if (item.productId == product.getId()) {
                    existingItem = item;
                    break;
                }
            }
            if (existingItem != null) {
                int newQuantity = existingItem.quantity + quantity;
                CartRepository.updateCartItemInSupabase(existingItem.cartItemId, newQuantity);
            } else {
                CartItemDTO newItem = new CartItemDTO(
                    0,
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    quantity,
                    product.getImageUrl()
                );
                CartRepository.addCartItemToSupabase(userId, newItem);
            }
            Cart cart = Cart.getInstance();
            for (int i = 0; i < quantity; i++) {
                cart.addProduct(product);
            }
        } catch (Exception e) {
            throw new Exception("Ошибка добавления в корзину: " + e.getMessage());
        }
    }

    public void addProductToCart(Product product) throws Exception {
        addProductToCart(product, 1);
    }

    public void removeFromCart(Product product) throws Exception {
        if (product == null) {
            throw new IllegalArgumentException("Товар не может быть null");
        }
        String userId = SessionManager.getUserId();
        if (userId == null || userId.isEmpty()) {
            throw new Exception("Пользователь не авторизован");
        }
        try {
            List<CartItemDTO> items = CartRepository.loadCartFromSupabase(userId);
            for (CartItemDTO item : items) {
                if (item.productId == product.getId()) {
                    CartRepository.removeCartItemFromSupabase(item.cartItemId);
                    break;
                }
            }
            Cart.getInstance().removeProduct(product);
        } catch (Exception e) {
            throw new Exception("Ошибка удаления из корзины: " + e.getMessage());
        }
    }

    public void updateCartItemQuantity(Product product, int newQuantity) throws Exception {
        if (newQuantity <= 0) {
            removeFromCart(product);
            return;
        }
        String userId = SessionManager.getUserId();
        if (userId == null || userId.isEmpty()) {
            throw new Exception("Пользователь не авторизован");
        }
        try {
            List<CartItemDTO> items = CartRepository.loadCartFromSupabase(userId);
            for (CartItemDTO item : items) {
                if (item.productId == product.getId()) {
                    CartRepository.updateCartItemInSupabase(item.cartItemId, newQuantity);
                    break;
                }
            }
            Cart cart = Cart.getInstance();
            cart.removeProduct(product);
            for (int i = 0; i < newQuantity; i++) {
                cart.addProduct(product);
            }
        } catch (Exception e) {
            throw new Exception("Ошибка обновления количества: " + e.getMessage());
        }
    }

    public double getCartTotal() {
        return Cart.getInstance().getTotal();
    }

    public int getCartSize() {
        return Cart.getInstance().getTotalQuantity();
    }

    public List<CartItem> getCartItems() {
        return Cart.getInstance().getItems();
    }

    public void clearCart() throws Exception {
        String userId = SessionManager.getUserId();
        if (userId == null || userId.isEmpty()) {
            throw new Exception("Пользователь не авторизован");
        }
        try {
            CartRepository.clearUserCart(userId);
            Cart.getInstance().clear();
        } catch (Exception e) {
            throw new Exception("Ошибка очистки корзины: " + e.getMessage());
        }
    }

    public Cart getCurrentCart() {
        return Cart.getInstance();
    }

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
        return total * (discountPercent / 100.0);
    }

    public int getProductQuantity(Product product) {
        for (CartItem item : Cart.getInstance().getItems()) {
            if (item.getProduct().getId() == product.getId()) {
                return item.getQuantity();
            }
        }
        return 0;
    }

    public boolean isProductInCart(Product product) {
        return getProductQuantity(product) > 0;
    }
}
