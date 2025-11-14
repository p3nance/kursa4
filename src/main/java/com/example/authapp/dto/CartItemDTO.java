package com.example.authapp.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Data Transfer Object для CartItem - работает с UUID строками
 */
public class CartItemDTO {

    @SerializedName("id")
    public int cartItemId;

    @SerializedName("user_id")
    public String userId;  // ✅ Меняем на String (UUID)

    @SerializedName("product_id")
    public int productId;

    @SerializedName("product_name")
    public String productName;

    @SerializedName("price")
    public double price;

    @SerializedName("quantity")
    public int quantity;

    @SerializedName("product_image")
    public String productImage;

    public CartItemDTO() {}

    public CartItemDTO(int cartItemId, int productId, String productName,
                       double price, int quantity, String productImage) {
        this.cartItemId = cartItemId;
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
        this.productImage = productImage;
    }
}
