package com.example.authapp.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class OrderDTO {
    @SerializedName("id")
    public int orderId;

    @SerializedName("user_id")
    public String userId;

    @SerializedName("order_date")
    public String orderDate;

    @SerializedName("total_amount")
    public double totalAmount;

    @SerializedName("discount_amount")
    public double discountAmount;

    @SerializedName("final_amount")
    public double finalAmount;

    @SerializedName("promo_code")
    public String promoCode;

    @SerializedName("status")
    public String status;

    public List<OrderItemDTO> items;

    public OrderDTO() {}

    public OrderDTO(String userId, double totalAmount, double discountAmount,
                    double finalAmount, String promoCode) {
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
        this.promoCode = promoCode;
        this.status = "pending";
    }
}
