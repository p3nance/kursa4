package com.example.authapp.dto;

import java.util.List;

/**
 * Data Transfer Object для Order
 */
public class OrderDTO {
    public int orderId;
    public String userId;
    public double totalAmount;        // Сумма заказа до скидки
    public String status;
    public String orderDate;

    // ✅ ПОЛЯ ДЛЯ ПРОМОКОДОВ
    public Integer promoCodeId;       // ID примененного промокода
    public String promoCode;          // Код промокода
    public Double discountAmount;     // Сумма скидки
    public Double discountPercent;    // Процент скидки

    // ✅ ПОЛЕ ДЛЯ СПИСКА ТОВАРОВ
    public List<OrderItemDTO> items;  // Список товаров в заказе

    // ✅ ИТОГОВАЯ СУММА С УЧЕТОМ СКИДКИ
    public double finalAmount;        // Итоговая сумма к оплате (totalAmount - discountAmount)

    public OrderDTO() {}

    // Старый конструктор (для обратной совместимости)
    public OrderDTO(int orderId, String userId, double totalAmount, String status, String orderDate) {
        this.orderId = orderId;
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.status = status;
        this.orderDate = orderDate;
        this.promoCodeId = null;
        this.promoCode = null;
        this.discountAmount = 0.0;
        this.discountPercent = 0.0;
        this.finalAmount = totalAmount; // Если нет скидки, итог = полная сумма
    }

    // ✅ НОВЫЙ КОНСТРУКТОР с промокодом
    public OrderDTO(int orderId, String userId, double totalAmount, String status, String orderDate,
                    Integer promoCodeId, String promoCode, Double discountAmount, Double discountPercent) {
        this.orderId = orderId;
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.status = status;
        this.orderDate = orderDate;
        this.promoCodeId = promoCodeId;
        this.promoCode = promoCode;
        this.discountAmount = discountAmount != null ? discountAmount : 0.0;
        this.discountPercent = discountPercent != null ? discountPercent : 0.0;

        // ✅ Автоматически рассчитываем итоговую сумму
        this.finalAmount = totalAmount - this.discountAmount;
    }
}
