package com.example.authapp.services;

import com.example.authapp.dto.OrderDTO;
import com.example.authapp.dto.OrderItemDTO;
import com.example.authapp.models.Cart;
import com.example.authapp.repositories.OrderRepository;
import com.example.authapp.repositories.ProductRepository;
import config.SessionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * ✅ Сервис для управления заказами
 */
public class OrderService {
    private final CartService cartService;
    private final ProductService productService;

    public OrderService(CartService cartService, ProductService productService) {
        this.cartService = cartService;
        this.productService = productService;
    }

    /**
     * ✅ Создает заказ из текущей корзины и уменьшает stock товаров
     */
    public int createOrderFromCart(String promoCode) throws Exception {
        String userId = SessionManager.getUserId();
        if (userId == null || userId.isEmpty()) {
            throw new Exception("Пользователь не авторизован");
        }

        Cart cart = cartService.getCurrentCart();
        if (cart.getItems().isEmpty()) {
            throw new Exception("Корзина пуста");
        }

        System.out.println("\n========== ОФОРМЛЕНИЕ ЗАКАЗА ==========");
        System.out.println("Пользователь: " + userId);

        try {
            // Вычисляем суммы
            double totalAmount = cartService.getCartTotal();
            double discountAmount = 0;

            if (promoCode != null && !promoCode.isEmpty()) {
                try {
                    discountAmount = cartService.applyDiscount(promoCode);
                } catch (Exception e) {
                    System.out.println("⚠️ Ошибка применения промокода: " + e.getMessage());
                }
            }

            double finalAmount = totalAmount - discountAmount;

            System.out.println("Всего: " + totalAmount + " ₽");
            System.out.println("Скидка: " + discountAmount + " ₽");
            System.out.println("Итого к оплате: " + finalAmount + " ₽");

            // Создаем DTO заказа
            OrderDTO orderDTO = new OrderDTO(userId, totalAmount, discountAmount, finalAmount, promoCode);

            // ✅ Создаем заказ в БД
            int orderId = OrderRepository.createOrder(orderDTO);
            System.out.println("✅ Заказ #" + orderId + " создан в БД");

            // Создаем список товаров заказа
            List<OrderItemDTO> orderItems = new ArrayList<>();
            for (Cart.CartItem item : cart.getItems()) {
                double subtotal = item.getProduct().getPrice() * item.getQuantity();
                OrderItemDTO itemDTO = new OrderItemDTO(
                        item.getProduct().getId(),
                        item.getProduct().getName(),
                        item.getProduct().getImageUrl(),
                        item.getProduct().getPrice(),
                        item.getQuantity(),
                        subtotal
                );
                orderItems.add(itemDTO);

                System.out.println("  📦 " + item.getProduct().getName() + " x" + item.getQuantity() + " = " + subtotal + " ₽");
            }

            // ✅ Добавляем товары в заказ
            OrderRepository.addOrderItems(orderId, orderItems);
            System.out.println("✅ Товары добавлены в заказ #" + orderId);

            // ✅ ВАЖНО: Уменьшаем stock для каждого товара
            System.out.println("\n--- Обновление остатков (stock) ---");
            for (Cart.CartItem item : cart.getItems()) {
                int productId = item.getProduct().getId();
                int quantity = item.getQuantity();

                System.out.println("📉 Уменьшаем stock товара #" + productId + " на " + quantity);

                try {
                    ProductRepository.decreaseProductStock(productId, quantity);
                    System.out.println("   ✅ Stock товара #" + productId + " успешно уменьшен");
                } catch (Exception e) {
                    System.err.println("   ❌ Ошибка уменьшения stock: " + e.getMessage());
                    // Не прерываем процесс, продолжаем для остальных товаров
                }
            }

            // ✅ Очищаем корзину
            cartService.clearCart();
            System.out.println("\n✅ Корзина очищена");

            System.out.println("========== ЗАКАЗ #" + orderId + " УСПЕШНО ОФОРМЛЕН ==========\n");
            return orderId;

        } catch (Exception e) {
            System.err.println("❌ ОШИБКА ПРИ ОФОРМЛЕНИИ ЗАКАЗА: " + e.getMessage());
            e.printStackTrace();
            throw new Exception("Ошибка при оформлении заказа: " + e.getMessage());
        }
    }

    /**
     * ✅ Получает историю заказов пользователя
     */
    public List<OrderDTO> getUserOrderHistory() throws Exception {
        String userId = SessionManager.getUserId();
        if (userId == null || userId.isEmpty()) {
            throw new Exception("Пользователь не авторизован");
        }

        try {
            List<OrderDTO> orders = OrderRepository.getUserOrders(userId);
            System.out.println("✅ Загружено " + orders.size() + " заказов");
            return orders;
        } catch (Exception e) {
            System.err.println("❌ Ошибка загрузки истории заказов: " + e.getMessage());
            throw new Exception("Ошибка загрузки истории заказов: " + e.getMessage());
        }
    }

    /**
     * ✅ Получает детали конкретного заказа
     */
    public OrderDTO getOrderById(int orderId) throws Exception {
        List<OrderDTO> orders = getUserOrderHistory();
        for (OrderDTO order : orders) {
            if (order.orderId == orderId) {
                return order;
            }
        }
        throw new Exception("Заказ #" + orderId + " не найден");
    }
}
