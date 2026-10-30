package com.example.authapp.dto;

import com.google.gson.annotations.SerializedName;
import java.math.BigDecimal;
import com.example.authapp.network.Money;

public class OrderItemDTO {

    @SerializedName("id")
    public int id;

    @SerializedName("order_id")
    public int orderId;

    @SerializedName("product_id")
    public int productId;

    @SerializedName("product_name")
    public String productName;

    @SerializedName("product_image")
    public String productImage;

    @SerializedName("price")
    public BigDecimal price = Money.ZERO;

    @SerializedName("quantity")
    public int quantity;

    @SerializedName("subtotal")
    public BigDecimal subtotal = Money.ZERO;

    public OrderItemDTO() {}

    // ✅ СТАРЫЙ КОНСТРУКТОР (оставлен для обратной совместимости)
    public OrderItemDTO(int productId, String productName, String productImage,
                        BigDecimal price, int quantity, BigDecimal subtotal) {
        this.productId = productId;
        this.productName = productName;
        this.productImage = productImage;
        this.price = Money.amount(price);
        this.quantity = quantity;
        this.subtotal = Money.amount(subtotal);
    }

    // ✅ НОВЫЙ КОНСТРУКТОР (используется в OrderRepository)
    public OrderItemDTO(int id, int orderId, int productId, String productName,
                        int quantity, BigDecimal price, String productImage) {
        this.id = id;
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.price = Money.amount(price);
        this.productImage = productImage;
        this.subtotal = Money.lineTotal(this.price, quantity);
    }
}
