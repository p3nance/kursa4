package com.example.authapp.dto;

import com.example.authapp.network.Money;
import java.math.BigDecimal;
import java.util.List;

/** Server order snapshot. Monetary JSON numbers remain decimal throughout the client. */
public class OrderDTO {
    public int orderId;
    public String userId;
    public BigDecimal totalAmount = Money.ZERO;
    public String status;
    public String orderDate;
    public Integer promoCodeId;
    public String promoCode;
    public BigDecimal discountAmount = Money.ZERO;
    public BigDecimal discountPercent;
    public List<OrderItemDTO> items;
    public BigDecimal finalAmount = Money.ZERO;

    public OrderDTO() {}
    public OrderDTO(int orderId, String userId, BigDecimal totalAmount, String status, String orderDate) {
        this(orderId, userId, totalAmount, status, orderDate, null, null, Money.ZERO, null);
    }
    public OrderDTO(int orderId, String userId, BigDecimal totalAmount, String status, String orderDate,
                    Integer promoCodeId, String promoCode, BigDecimal discountAmount, BigDecimal discountPercent) {
        this.orderId = orderId;
        this.userId = userId;
        this.totalAmount = Money.amount(totalAmount);
        this.status = status;
        this.orderDate = orderDate;
        this.promoCodeId = promoCodeId;
        this.promoCode = promoCode;
        this.discountAmount = discountAmount == null ? Money.ZERO : Money.amount(discountAmount);
        this.discountPercent = discountPercent;
        this.finalAmount = Money.payable(this.totalAmount, this.discountAmount);
    }
}
