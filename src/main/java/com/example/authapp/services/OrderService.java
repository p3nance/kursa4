package com.example.authapp.services;

import com.example.authapp.dto.OrderDTO;
import com.example.authapp.dto.OrderItemDTO;
import com.example.authapp.models.Cart;
import com.example.authapp.repositories.OrderRepository;
import com.example.authapp.repositories.ProductRepository;
import config.SessionManager;

import java.util.ArrayList;
import java.util.List;

public class OrderService {
    private final CartService cartService;
    private final ProductService productService;

    public OrderService(CartService cartService, ProductService productService) {
        this.cartService = cartService;
        this.productService = productService;
    }

    public int createOrderFromCart(String promoCode) throws Exception {
        String userId = SessionManager.getUserId();
        if (userId == null || userId.isEmpty()) {
            throw new Exception("Пользователь не авторизован");
        }

        Cart cart = cartService.getCurrentCart();
        if (cart.getItems().isEmpty()) {
            throw new Exception("Корзина пуста");
        }


        try {
            // Вычисляем суммы
            double totalAmount = cartService.getCartTotal();
            double discountAmount = 0;

            if (promoCode != null && !promoCode.isEmpty()) {
                try {
                    discountAmount = cartService.applyDiscount(promoCode);
                } catch (Exception e) {
                }
            }

            double finalAmount = totalAmount - discountAmount;

            // Создаем DTO заказа
            OrderDTO orderDTO = new OrderDTO(userId, totalAmount, discountAmount, finalAmount, promoCode);

            // Создаем заказ в БД
            int orderId = OrderRepository.createOrder(orderDTO);


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

            }

            // Добавляем товары в заказ
            OrderRepository.addOrderItems(orderId, orderItems);

            // Уменьшаем stock для каждого товара
            for (Cart.CartItem item : cart.getItems()) {
                int productId = item.getProduct().getId();
                int quantity = item.getQuantity();

                try {
                    ProductRepository.decreaseProductStock(productId, quantity);

                } catch (Exception e) {
                    System.err.println("   ❌ Ошибка уменьшения stock: " + e.getMessage());
                    // Не прерываем процесс, продолжаем для остальных товаров
                }
            }

            // Очищаем корзину
            cartService.clearCart();

            return orderId;

        } catch (Exception e) {
            System.err.println("❌ ОШИБКА ПРИ ОФОРМЛЕНИИ ЗАКАЗА: " + e.getMessage());
            e.printStackTrace();
            throw new Exception("Ошибка при оформлении заказа: " + e.getMessage());
        }
    }

    public List<OrderDTO> getUserOrderHistory() throws Exception {
        String userId = SessionManager.getUserId();
        if (userId == null || userId.isEmpty()) {
            throw new Exception("Пользователь не авторизован");
        }

        try {
            List<OrderDTO> orders = OrderRepository.getUserOrders(userId);
            return orders;
        } catch (Exception e) {
            System.err.println("❌ Ошибка загрузки истории заказов: " + e.getMessage());
            throw new Exception("Ошибка загрузки истории заказов: " + e.getMessage());
        }
    }

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
