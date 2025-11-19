package com.example.authapp.services;

import com.example.authapp.dto.OrderDTO;
import com.example.authapp.models.Cart;
import com.example.authapp.models.Cart.CartItem;
import com.example.authapp.models.PromoCode;
import com.example.authapp.repositories.OrderRepository;
import com.example.authapp.repositories.PromoCodeRepository;
import config.SessionManager;

import java.util.List;

public class OrderService {
    private final CartService cartService;
    private final ProductService productService;

    public OrderService(CartService cartService, ProductService productService) {
        this.cartService = cartService;
        this.productService = productService;
    }

    /**
     * ✅ Получить историю заказов текущего пользователя
     */
    public List<OrderDTO> getUserOrderHistory() throws Exception {
        String userId = SessionManager.getUserId();
        if (userId == null || userId.isEmpty()) {
            throw new Exception("Пользователь не авторизован");
        }
        return OrderRepository.getUserOrders(userId);
    }

    /**
     * ✅ Создание заказа из корзины с поддержкой промокода
     */
    public int createOrderFromCart(String promoCode) throws Exception {
        String userId = SessionManager.getUserId();
        if (userId == null || userId.isEmpty()) {
            throw new Exception("❌ Пользователь не авторизован");
        }

        Cart cart = cartService.getCurrentCart();
        if (cart.getItems().isEmpty()) {
            throw new Exception("❌ Корзина пуста");
        }

        System.out.println("=== 🛒 Создание заказа ===");
        System.out.println("User ID: " + userId);
        System.out.println("Товаров: " + cart.getTotalQuantity());

        try {
            double totalAmount = cart.getTotal();
            double discountAmount = 0;
            int promoCodeId = 0;

            if (promoCode != null && !promoCode.trim().isEmpty()) {
                System.out.println("📌 Применение промокода: " + promoCode);

                PromoCodeRepository promoRepo = new PromoCodeRepository();
                PromoCode promo = promoRepo.validatePromoCode(promoCode);

                discountAmount = (totalAmount * promo.getDiscountPercent()) / 100.0;
                promoCodeId = promo.getPromoId();

                System.out.println("   - ID промокода: " + promoCodeId);
                System.out.println("   - Скидка: " + promo.getDiscountPercent() + "%");
                System.out.println("   - Сумма скидки: " + discountAmount);
            }

            double finalAmount = totalAmount - discountAmount;

            System.out.println("💰 Сумма до скидки: " + totalAmount);
            System.out.println("💰 Скидка: " + discountAmount);
            System.out.println("💰 Итого к оплате: " + finalAmount);

            int orderId = OrderRepository.createOrder(
                    userId,
                    totalAmount,
                    (promoCodeId > 0 ? promoCodeId : null),
                    discountAmount
            );

            System.out.println("✅ Заказ создан! ID: " + orderId);

            // ✅ ИСПРАВЛЕНО: передаем название и изображение товара
            for (CartItem item : cart.getItems()) {
                OrderRepository.addOrderItem(
                        orderId,
                        item.getProduct().getId(),
                        item.getProduct().getName(),      // ✅ ДОБАВЛЕНО
                        item.getProduct().getImageUrl(),  // ✅ ДОБАВЛЕНО
                        item.getQuantity(),
                        item.getProduct().getPrice()
                );
                System.out.println("   ✅ Добавлен товар: " + item.getProduct().getName() + " x" + item.getQuantity());
            }

            if (promoCodeId > 0) {
                PromoCodeRepository promoRepo = new PromoCodeRepository();
                promoRepo.usePromoCode(promoCodeId);
                System.out.println("✅ Промокод использован");
            }

            cartService.clearCart();
            System.out.println("✅ Корзина очищена");
            System.out.println("=== ✅ Заказ успешно оформлен ===");

            return orderId;

        } catch (Exception e) {
            System.err.println("❌ Ошибка создания заказа: " + e.getMessage());
            e.printStackTrace();
            throw new Exception("Ошибка при оформлении заказа: " + e.getMessage());
        }
    }
}
