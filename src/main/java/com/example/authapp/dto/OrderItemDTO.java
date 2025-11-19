package com.example.authapp.dto;

import com.google.gson.annotations.SerializedName;

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
    public double price;

    @SerializedName("quantity")
    public int quantity;

    @SerializedName("subtotal")
    public double subtotal;

    public OrderItemDTO() {}

    // ✅ СТАРЫЙ КОНСТРУКТОР (оставлен для обратной совместимости)
    public OrderItemDTO(int productId, String productName, String productImage,
                        double price, int quantity, double subtotal) {
        this.productId = productId;
        this.productName = productName;
        this.productImage = productImage;
        this.price = price;
        this.quantity = quantity;
        this.subtotal = subtotal;
    }

    // ✅ НОВЫЙ КОНСТРУКТОР (используется в OrderRepository)
    public OrderItemDTO(int id, int orderId, int productId, String productName,
                        int quantity, double price, String productImage) {
        this.id = id;
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.price = price;
        this.productImage = productImage;
        this.subtotal = quantity * price;
    }
}
